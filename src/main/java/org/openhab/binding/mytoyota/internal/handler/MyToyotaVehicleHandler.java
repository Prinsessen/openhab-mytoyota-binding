/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.mytoyota.internal.handler;

import static org.openhab.binding.mytoyota.internal.MyToyotaBindingConstants.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mytoyota.internal.api.MyToyotaApiClient;
import org.openhab.binding.mytoyota.internal.api.MyToyotaApiException;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PointType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.MetricPrefix;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * One vehicle: polls the cloud on the bridge's interval and maps the answers to channels.
 *
 * Reads used (all GET, VIN in a header): electric status (battery, range, charging), telemetry (odometer,
 * distance to empty), location (last parked), vehicle status (doors, windows, lights, warnings) and climate
 * status. Two POSTs wake the car: realtime-status makes it report a fresh state of charge, remote/status
 * refreshes the door/window cache. The car's modem is woken by them, so they are rate-limited: only while
 * charging (bridge setting) or on the refresh channel.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class MyToyotaVehicleHandler extends BaseThingHandler {

    private static final int REPOLL_AFTER_WAKE_SECONDS = 45;

    private final Logger logger = LoggerFactory.getLogger(MyToyotaVehicleHandler.class);

    private String vin = "";
    private @Nullable ScheduledFuture<?> pollJob;
    private @Nullable ScheduledFuture<?> repollJob;
    private Instant lastWake = Instant.EPOCH;
    private boolean charging;
    /** Setpoints for a climate start; seeded once from the car's saved settings. */
    private double climateTemperature = 21;
    private int climateDuration = 20;
    private boolean climateSeeded;

    public MyToyotaVehicleHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        vin = getConfigAs(MyToyotaVehicleConfiguration.class).vin.trim().toUpperCase();
        if (vin.length() != 17) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "VIN must have 17 characters");
            return;
        }
        updateStatus(ThingStatus.UNKNOWN);
        startPolling();
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            startPolling();
        } else {
            stopPolling();
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    private synchronized void startPolling() {
        stopPolling();
        MyToyotaAccountHandler account = getAccount();
        if (account == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            return;
        }
        int minutes = Math.max(1, account.getAccountConfig().pollInterval);
        pollJob = scheduler.scheduleWithFixedDelay(this::poll, 5, minutes * 60L, TimeUnit.SECONDS);
    }

    private synchronized void stopPolling() {
        ScheduledFuture<?> job = pollJob;
        if (job != null) {
            job.cancel(true);
            pollJob = null;
        }
        ScheduledFuture<?> re = repollJob;
        if (re != null) {
            re.cancel(true);
            repollJob = null;
        }
    }

    @Override
    public void dispose() {
        stopPolling();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            scheduler.execute(this::poll);
            return;
        }
        String id = channelUID.getId();
        switch (id) {
            case CHANNEL_CONTROL_REFRESH -> {
                if (command == OnOffType.ON) {
                    scheduler.execute(() -> {
                        wake(true);
                        updateState(CHANNEL_CONTROL_REFRESH, OnOffType.OFF);
                        scheduleRepoll();
                    });
                }
            }
            case CHANNEL_CONTROL_LOCK -> remoteCommand(command == OnOffType.ON ? "door-lock" : "door-unlock");
            case CHANNEL_CONTROL_HAZARD -> remoteCommand(command == OnOffType.ON ? "hazard-on" : "hazard-off");
            case CHANNEL_CONTROL_HORN -> {
                if (command == OnOffType.ON) {
                    remoteCommand("sound-horn");
                    updateState(CHANNEL_CONTROL_HORN, OnOffType.OFF);
                }
            }
            case CHANNEL_CONTROL_FIND -> {
                if (command == OnOffType.ON) {
                    remoteCommand("find-vehicle");
                    updateState(CHANNEL_CONTROL_FIND, OnOffType.OFF);
                }
            }
            case CHANNEL_CONTROL_CLIMATE -> climateCommand(command == OnOffType.ON);
            case CHANNEL_CONTROL_CLIMATE_TEMPERATURE -> {
                Double t = commandNumber(command);
                if (t != null) {
                    climateTemperature = t;
                    updateState(CHANNEL_CONTROL_CLIMATE_TEMPERATURE, new QuantityType<>(t, SIUnits.CELSIUS));
                }
            }
            case CHANNEL_CONTROL_CLIMATE_DURATION -> {
                Double d = commandNumber(command);
                if (d != null) {
                    climateDuration = Math.max(1, d.intValue());
                    updateState(CHANNEL_CONTROL_CLIMATE_DURATION, new QuantityType<>(climateDuration, Units.MINUTE));
                }
            }
            case CHANNEL_CONTROL_CHARGE_NOW -> {
                if (command == OnOffType.ON) {
                    JsonObject body = new JsonObject();
                    body.addProperty("command", "CHARGE_NOW");
                    sendRemote(MyToyotaApiClient.ENDPOINT_ELECTRIC_COMMAND, body, "charge-now");
                    updateState(CHANNEL_CONTROL_CHARGE_NOW, OnOffType.OFF);
                }
            }
            default -> {
                // read-only channel
            }
        }
    }

    // ------------------------------------------------------------ remote commands

    /** {"command":"door-lock"} and friends on /v1/global/remote/command */
    private void remoteCommand(String name) {
        JsonObject body = new JsonObject();
        body.addProperty("command", name);
        sendRemote(MyToyotaApiClient.ENDPOINT_COMMAND, body, name);
    }

    /** Climate start with the held temperature and duration, or stop. */
    private void climateCommand(boolean start) {
        JsonObject body = new JsonObject();
        body.addProperty("command", start ? "start" : "stop");
        if (start) {
            JsonObject temp = new JsonObject();
            temp.addProperty("value", climateTemperature);
            temp.addProperty("unit", "C");
            body.add("temperature", temp);
            body.addProperty("duration", climateDuration);
        }
        sendRemote(MyToyotaApiClient.ENDPOINT_CLIMATE_CONTROL, body, start ? "climate-start" : "climate-stop");
    }

    /**
     * Sends a command, shows the cloud's return code on lastCommandResult, and polls the car 45 s later so
     * the state channels confirm what happened. Return code 000000 means the gateway accepted the request;
     * whether the car did it is only visible in the state channels afterwards.
     */
    private void sendRemote(String endpoint, JsonObject body, String label) {
        scheduler.execute(() -> {
            MyToyotaAccountHandler account = getAccount();
            MyToyotaApiClient client = account == null ? null : account.getClient();
            if (client == null) {
                updateState(CHANNEL_CONTROL_LAST_RESULT, new StringType(label + ": account offline"));
                return;
            }
            try {
                JsonObject r = client.post(endpoint, vin, body);
                String code = string(payload(r), "returnCode");
                String msg = firstMessage(r);
                String result = label + ": " + (code == null ? "no return code" : code)
                        + (msg == null ? "" : " (" + msg + ")");
                logger.info("Remote command on {}: {}", shortVin(), result);
                updateState(CHANNEL_CONTROL_LAST_RESULT, new StringType(result));
                lastWake = Instant.now();
                scheduleRepoll();
            } catch (MyToyotaApiException e) {
                logger.warn("Remote command {} on {} failed: {}", label, shortVin(), e.getMessage());
                updateState(CHANNEL_CONTROL_LAST_RESULT, new StringType(label + ": failed, " + e.getMessage()));
            }
        });
    }

    private static @Nullable String firstMessage(JsonObject resp) {
        JsonObject status = object(resp, "status");
        if (status == null) {
            return null;
        }
        JsonElement msgs = status.get("messages");
        if (msgs == null || !msgs.isJsonArray() || msgs.getAsJsonArray().isEmpty()) {
            return null;
        }
        JsonElement first = msgs.getAsJsonArray().get(0);
        return first.isJsonObject() ? string(first.getAsJsonObject(), "description") : null;
    }

    private static @Nullable Double commandNumber(Command command) {
        if (command instanceof QuantityType<?> q) {
            return q.doubleValue();
        }
        if (command instanceof DecimalType d) {
            return d.doubleValue();
        }
        return null;
    }

    // ------------------------------------------------------------------ polling

    private @Nullable MyToyotaAccountHandler getAccount() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            return null;
        }
        return bridge.getHandler() instanceof MyToyotaAccountHandler h ? h : null;
    }

    private void poll() {
        MyToyotaAccountHandler account = getAccount();
        MyToyotaApiClient client = account == null ? null : account.getClient();
        if (account == null || client == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return;
        }
        try {
            int wakeMinutes = account.getAccountConfig().wakeWhileCharging;
            if (charging && wakeMinutes > 0
                    && Instant.now().isAfter(lastWake.plusSeconds(wakeMinutes * 60L))) {
                wake(false);
            }
            updateElectric(client.get(MyToyotaApiClient.ENDPOINT_ELECTRIC_STATUS, vin));
            updateTelemetry(client.get(MyToyotaApiClient.ENDPOINT_TELEMETRY, vin));
            updateLocation(client.get(MyToyotaApiClient.ENDPOINT_LOCATION, vin));
            updateVehicleStatus(client.get(MyToyotaApiClient.ENDPOINT_VEHICLE_STATUS, vin));
            updateClimate(client.get(MyToyotaApiClient.ENDPOINT_CLIMATE_STATUS, vin));
            if (!climateSeeded) {
                seedClimateSettings(client.get(MyToyotaApiClient.ENDPOINT_CLIMATE_SETTINGS, vin));
            }
            updateState(CHANNEL_CONTROL_LAST_POLL, new DateTimeType(ZonedDateTime.now()));
            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }
            account.reportCommunication(true, null);
        } catch (MyToyotaApiException e) {
            logger.debug("Poll of {} failed: {}", shortVin(), e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            account.reportCommunication(false, e.getMessage());
        } catch (RuntimeException e) {
            logger.warn("Unexpected answer while polling {}: {}", shortVin(), e.toString());
        }
    }

    /** Asks the car for a fresh state of charge (and, if full, for fresh door/window state). */
    private void wake(boolean full) {
        MyToyotaAccountHandler account = getAccount();
        MyToyotaApiClient client = account == null ? null : account.getClient();
        if (client == null) {
            return;
        }
        try {
            JsonObject r = client.post(MyToyotaApiClient.ENDPOINT_ELECTRIC_REALTIME, vin);
            if (full) {
                client.post(MyToyotaApiClient.ENDPOINT_REFRESH_STATUS, vin);
            }
            lastWake = Instant.now();
            updateState(CHANNEL_CONTROL_LAST_WAKE, new DateTimeType(ZonedDateTime.now()));
            String code = string(payload(r), "returnCode");
            if (code != null && !"000000".equals(code)) {
                logger.debug("Wake of {} answered returnCode {}", shortVin(), code);
            }
        } catch (MyToyotaApiException e) {
            logger.debug("Wake of {} failed: {}", shortVin(), e.getMessage());
        }
    }

    private synchronized void scheduleRepoll() {
        ScheduledFuture<?> re = repollJob;
        if (re != null) {
            re.cancel(false);
        }
        repollJob = scheduler.schedule(this::poll, REPOLL_AFTER_WAKE_SECONDS, TimeUnit.SECONDS);
    }

    // ----------------------------------------------------------------- mapping

    private void updateElectric(JsonObject resp) {
        JsonObject p = payload(resp);
        updateState(CHANNEL_BATTERY_LEVEL, quantity(p.get("batteryLevel"), Units.PERCENT));
        updateState(CHANNEL_BATTERY_RANGE, distance(p.get("evRange")));
        updateState(CHANNEL_BATTERY_RANGE_AC, distance(p.get("evRangeWithAc")));
        String status = string(p, "chargingStatus");
        updateState(CHANNEL_BATTERY_CHARGING, status == null ? UnDefType.UNDEF : new StringType(status));
        charging = status != null && status.toLowerCase().contains("charging");
        updateState(CHANNEL_BATTERY_CHARGING_ACTIVE, OnOffType.from(charging));
        updateState(CHANNEL_BATTERY_REMAINING_TIME, quantity(p.get("remainingChargeTime"), Units.MINUTE));
        updateState(CHANNEL_BATTERY_TIMESTAMP, dateTime(string(p, "lastUpdateTimestamp")));
    }

    private void updateTelemetry(JsonObject resp) {
        JsonObject p = payload(resp);
        updateState(CHANNEL_TELEMETRY_ODOMETER, distance(p.get("odometer")));
        updateState(CHANNEL_TELEMETRY_DTE, distance(p.get("distanceToEmpty")));
        updateState(CHANNEL_TELEMETRY_TIMESTAMP, dateTime(string(p, "timestamp")));
    }

    private void updateLocation(JsonObject resp) {
        JsonObject p = payload(resp);
        JsonObject loc = object(p, "vehicleLocation");
        if (loc == null) {
            updateState(CHANNEL_LOCATION_POSITION, UnDefType.UNDEF);
            return;
        }
        Double lat = number(loc.get("latitude"));
        Double lon = number(loc.get("longitude"));
        updateState(CHANNEL_LOCATION_POSITION, lat == null || lon == null ? UnDefType.UNDEF
                : new PointType(new DecimalType(lat), new DecimalType(lon)));
        String name = string(loc, "displayName");
        updateState(CHANNEL_LOCATION_NAME, name == null ? UnDefType.UNDEF : new StringType(name));
        updateState(CHANNEL_LOCATION_TIMESTAMP, dateTime(string(loc, "locationAcquisitionDatetime")));
    }

    private void updateVehicleStatus(JsonObject resp) {
        JsonObject p = payload(resp);
        String overall = string(p, "overallStatus");
        updateState(CHANNEL_STATUS_OVERALL, overall == null ? UnDefType.UNDEF : new StringType(overall));
        Double warnings = number(p.get("overallWarningCounts"));
        updateState(CHANNEL_STATUS_WARNINGS,
                warnings == null ? UnDefType.UNDEF : new DecimalType(warnings.intValue()));
        updateState(CHANNEL_STATUS_TIMESTAMP, dateTime(string(p, "lastUpdateTimestamp")));

        JsonObject doors = object(p, "doors");
        if (doors != null) {
            boolean allLocked = true, anyLockKnown = false, anyOpen = false;
            for (Map.Entry<String, JsonElement> e : doors.entrySet()) {
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject door = e.getValue().getAsJsonObject();
                String lock = nested(door, "lockStatus", "status");
                if (lock != null) {
                    anyLockKnown = true;
                    allLocked &= "locked".equalsIgnoreCase(lock);
                }
                String open = nested(door, "openStatus", "status");
                if (open != null && !"hood".equals(e.getKey()) && !"rearBack".equals(e.getKey())) {
                    anyOpen |= "open".equalsIgnoreCase(open);
                }
            }
            updateState(CHANNEL_STATUS_LOCKED, anyLockKnown ? OnOffType.from(allLocked) : UnDefType.UNDEF);
            updateState(CHANNEL_STATUS_DOOR_OPEN, anyOpen ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
            updateState(CHANNEL_STATUS_TRUNK_OPEN, openClosed(nested(object(doors, "rearBack"), "openStatus", "status")));
            updateState(CHANNEL_STATUS_HOOD_OPEN, openClosed(nested(object(doors, "hood"), "openStatus", "status")));
        }

        JsonObject windows = object(p, "windows");
        if (windows != null) {
            boolean anyOpen = false;
            for (Map.Entry<String, JsonElement> e : windows.entrySet()) {
                if (e.getValue().isJsonObject()) {
                    anyOpen |= "open".equalsIgnoreCase(string(e.getValue().getAsJsonObject(), "status"));
                }
            }
            updateState(CHANNEL_STATUS_WINDOW_OPEN, anyOpen ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
        }

        JsonObject lights = object(p, "lights");
        if (lights != null) {
            String hazard = nested(lights, "hazard", "status");
            updateState(CHANNEL_STATUS_HAZARD, hazard == null ? UnDefType.UNDEF : OnOffType.from("on".equalsIgnoreCase(hazard)));
        }
    }

    private void updateClimate(JsonObject resp) {
        String status = string(payload(resp), "status");
        updateState(CHANNEL_CLIMATE_STATUS, status == null ? UnDefType.UNDEF : new StringType(status));
    }

    /** The car's saved climate settings become the initial setpoints of the climate channels. */
    private void seedClimateSettings(JsonObject resp) {
        JsonObject p = payload(resp);
        JsonObject temperature = object(p, "temperature");
        Double t = temperature == null ? null : number(temperature.get("value"));
        Double d = number(p.get("duration"));
        if (t != null) {
            climateTemperature = t;
        }
        if (d != null && d >= 1) {
            climateDuration = d.intValue();
        }
        updateState(CHANNEL_CONTROL_CLIMATE_TEMPERATURE, new QuantityType<>(climateTemperature, SIUnits.CELSIUS));
        updateState(CHANNEL_CONTROL_CLIMATE_DURATION, new QuantityType<>(climateDuration, Units.MINUTE));
        climateSeeded = true;
    }

    // ----------------------------------------------------------------- helpers

    private static JsonObject payload(JsonObject resp) {
        JsonObject p = object(resp, "payload");
        return p == null ? new JsonObject() : p;
    }

    private static @Nullable JsonObject object(@Nullable JsonObject o, String key) {
        if (o == null) {
            return null;
        }
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static @Nullable String string(@Nullable JsonObject o, String key) {
        if (o == null) {
            return null;
        }
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() || e.isJsonObject() || e.isJsonArray() ? null : e.getAsString();
    }

    private static @Nullable String nested(@Nullable JsonObject o, String key, String subKey) {
        return string(object(o, key), subKey);
    }

    private static @Nullable Double number(@Nullable JsonElement e) {
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
            return null;
        }
        try {
            return e.getAsDouble();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static State quantity(@Nullable JsonElement e, javax.measure.Unit<?> unit) {
        Double v = number(e);
        return v == null ? UnDefType.UNDEF : new QuantityType<>(v, unit);
    }

    /** {"unit":"km","value":85.5} or {"value":6,"unit":"km"} */
    private static State distance(@Nullable JsonElement e) {
        if (e == null || !e.isJsonObject()) {
            return UnDefType.UNDEF;
        }
        JsonObject o = e.getAsJsonObject();
        Double v = number(o.get("value"));
        if (v == null) {
            return UnDefType.UNDEF;
        }
        String unit = string(o, "unit");
        return new QuantityType<>(v, "mi".equalsIgnoreCase(unit) ? ImperialUnits.MILE : MetricPrefix.KILO(SIUnits.METRE));
    }

    private static State dateTime(@Nullable String iso) {
        if (iso == null || iso.isEmpty()) {
            return UnDefType.UNDEF;
        }
        try {
            return new DateTimeType(Instant.parse(iso).atZone(ZoneId.systemDefault()));
        } catch (DateTimeParseException e) {
            return UnDefType.UNDEF;
        }
    }

    private static State openClosed(@Nullable String status) {
        return status == null ? UnDefType.UNDEF
                : "open".equalsIgnoreCase(status) ? OpenClosedType.OPEN : OpenClosedType.CLOSED;
    }

    private String shortVin() {
        return vin.length() == 17 ? vin.substring(0, 3) + "…" + vin.substring(13) : vin;
    }
}
