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
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
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
    private boolean channelsProvisioned;
    private Instant lastTripsFetch = Instant.EPOCH;
    private Instant lastServiceFetch = Instant.EPOCH;
    private String lastLocationStamp = "";
    private static final long TRIPS_INTERVAL_S = 3600;
    private static final long SERVICE_INTERVAL_S = 6 * 3600;
    /** Climate options sent with a start: channel id -> "on"/"off" */
    private final Map<String, String> climateOptions = new HashMap<>();
    /** Last states we posted, for the few places where a later step needs them */
    private final Map<String, String> lastSeen = new HashMap<>();

    private @Nullable String items(String channel) {
        return lastSeen.get(channel);
    }

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
            case CHANNEL_CONTROL_LOCK -> remoteCommand(command == OnOffType.ON ? "door-lock" : "door-unlock", id, command);
            case CHANNEL_CONTROL_HAZARD -> remoteCommand(command == OnOffType.ON ? "hazard-on" : "hazard-off", id, command);
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
            case CHANNEL_CONTROL_TRUNK_LOCK -> remoteCommand(command == OnOffType.ON ? "trunk-lock" : "trunk-unlock", id, command);
            case CHANNEL_CONTROL_ENGINE -> remoteCommand(command == OnOffType.ON ? "engine-start" : "engine-stop", id, command);
            case CHANNEL_CONTROL_HEADLIGHTS -> remoteCommand(command == OnOffType.ON ? "headlight-on" : "headlight-off", id, command);
            case CHANNEL_CONTROL_BUZZER -> oneShot(id, command, "buzzer-warning");
            case CHANNEL_CONTROL_WINDOWS_OPEN -> oneShot(id, command, "power-window-on");
            case CHANNEL_CONTROL_WINDOWS_CLOSE -> oneShot(id, command, "power-window-close");
            case CHANNEL_CONTROL_VENTILATION -> oneShot(id, command, "ventilation-on");
            case CHANNEL_CONTROL_DEFROST_FRONT, CHANNEL_CONTROL_DEFROST_REAR, CHANNEL_CONTROL_STEERING_HEATER,
                    CHANNEL_CONTROL_MIRROR_HEATER, CHANNEL_CONTROL_SEAT_DRIVER, CHANNEL_CONTROL_SEAT_PASSENGER,
                    CHANNEL_CONTROL_SEAT_REAR_LEFT, CHANNEL_CONTROL_SEAT_REAR_RIGHT -> {
                if (command instanceof OnOffType) {
                    climateOptions.put(id, command == OnOffType.ON ? "on" : "off");
                    updateState(id, (OnOffType) command);
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
        remoteCommand(name, null, null);
    }

    /**
     * Same, for a command channel that mirrors a state of the car: once the cloud accepts the request the channel
     * shows the commanded state at once instead of the old mirrored one, so the UI does not flick back for the
     * 45 s until the confirming poll. If the car refuses, that poll puts the real state back.
     */
    private void remoteCommand(String name, @Nullable String channel, @Nullable Command expected) {
        JsonObject body = new JsonObject();
        body.addProperty("command", name);
        sendRemote(MyToyotaApiClient.ENDPOINT_COMMAND, body, name, channel, expected);
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
            // the options the app keeps under "climate schedule", only those the car has channels for
            JsonObject heating = new JsonObject();
            putOption(heating, "frontDefroster", CHANNEL_CONTROL_DEFROST_FRONT);
            putOption(heating, "rearDefogger", CHANNEL_CONTROL_DEFROST_REAR);
            putOption(heating, "steeringHeater", CHANNEL_CONTROL_STEERING_HEATER);
            putOption(heating, "mirrorHeater", CHANNEL_CONTROL_MIRROR_HEATER);
            if (heating.size() > 0) {
                body.add("heatingOptions", heating);
            }
            JsonObject seats = new JsonObject();
            putOption(seats, "driverSeat", CHANNEL_CONTROL_SEAT_DRIVER);
            putOption(seats, "passengerSeat", CHANNEL_CONTROL_SEAT_PASSENGER);
            putOption(seats, "rearDriverSeat", CHANNEL_CONTROL_SEAT_REAR_LEFT);
            putOption(seats, "rearPassengerSeat", CHANNEL_CONTROL_SEAT_REAR_RIGHT);
            if (seats.size() > 0) {
                body.add("seatOptions", seats);
            }
            body.addProperty("saveSettings", true);   // so the app's "climate schedule" shows the same
        }
        sendRemote(MyToyotaApiClient.ENDPOINT_CLIMATE_CONTROL, body, start ? "climate-start" : "climate-stop",
                CHANNEL_CONTROL_CLIMATE, OnOffType.from(start));
    }

    /**
     * Sends a command, shows the cloud's return code on lastCommandResult, and polls the car 45 s later so
     * the state channels confirm what happened. Return code 000000 means the gateway accepted the request;
     * whether the car did it is only visible in the state channels afterwards.
     */
    private void sendRemote(String endpoint, JsonObject body, String label) {
        sendRemote(endpoint, body, label, null, null);
    }

    private void sendRemote(String endpoint, JsonObject body, String label, @Nullable String channel,
            @Nullable Command expected) {
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
                if (channel != null && expected instanceof State expectedState && "000000".equals(code)) {
                    updateState(channel, expectedState);   // accepted: show it now, the repoll confirms or corrects
                }
                lastWake = Instant.now();
                updateState(CHANNEL_CONTROL_LAST_WAKE, new DateTimeType(ZonedDateTime.now()));
                if (label.startsWith("climate")) {
                    try {
                        client.post(MyToyotaApiClient.ENDPOINT_CLIMATE_REFRESH, vin);   // faster confirmation
                    } catch (MyToyotaApiException e) {
                        logger.debug("Climate refresh after {} failed: {}", label, e.getMessage());
                    }
                }
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
            updateNotifications(client.get(MyToyotaApiClient.ENDPOINT_NOTIFICATIONS, vin));
            updateHealth(client.get(MyToyotaApiClient.ENDPOINT_HEALTH, vin));
            // trips: hourly, and right after the car parks (its position timestamp moves)
            String locStamp = String.valueOf(items(CHANNEL_LOCATION_TIMESTAMP));
            boolean parkedSince = !locStamp.equals(lastLocationStamp);
            if (parkedSince || Instant.now().isAfter(lastTripsFetch.plusSeconds(TRIPS_INTERVAL_S))) {
                updateTrips(client);
                lastTripsFetch = Instant.now();
                lastLocationStamp = locStamp;
            }
            if (Instant.now().isAfter(lastServiceFetch.plusSeconds(SERVICE_INTERVAL_S))) {
                updateService(client.get(MyToyotaApiClient.ENDPOINT_SERVICE_HISTORY, vin));
                lastServiceFetch = Instant.now();
            }
            if (!channelsProvisioned) {
                provisionOptionalChannels(account);
            }
            if (!climateSeeded) {
                seedClimateSettings(client.get(MyToyotaApiClient.ENDPOINT_CLIMATE_SETTINGS, vin));
            }
            updateState(CHANNEL_CONTROL_LAST_POLL, new DateTimeType(ZonedDateTime.now()));
            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
                restOneShotChannels();
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
        updateState(CHANNEL_TELEMETRY_FUEL, quantity(p.get("fuelLevel"), Units.PERCENT));   // null on a battery EV
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
        State acquired = dateTime(string(loc, "locationAcquisitionDatetime"));
        updateState(CHANNEL_LOCATION_TIMESTAMP, acquired);
        lastSeen.put(CHANNEL_LOCATION_TIMESTAMP, acquired.toString());
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
            // the lock command channel mirrors the car, so a Switch item shows the real state
            updateState(CHANNEL_CONTROL_LOCK, anyLockKnown ? OnOffType.from(allLocked) : UnDefType.UNDEF);
            updateState(CHANNEL_STATUS_DOOR_OPEN, anyOpen ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
            updateState(CHANNEL_STATUS_TRUNK_OPEN, openClosed(nested(object(doors, "rearBack"), "openStatus", "status")));
            String trunkLock = nested(object(doors, "rearBack"), "lockStatus", "status");
            if (getThing().getChannel(CHANNEL_CONTROL_TRUNK_LOCK) != null) {
                updateState(CHANNEL_CONTROL_TRUNK_LOCK, trunkLock == null ? UnDefType.UNDEF : OnOffType.from("locked".equalsIgnoreCase(trunkLock)));
            }
            updateState(CHANNEL_STATUS_HOOD_OPEN, openClosed(nested(object(doors, "hood"), "openStatus", "status")));
        }

        // per-door detail, as the app's status page shows it
        for (String d : DOORS) {
            JsonObject door = object(doors, d);
            String lock = nested(door, "lockStatus", "status");
            String open = nested(door, "openStatus", "status");
            updateState(GROUP_DOORS + d + "Locked", lock == null ? UnDefType.UNDEF : OnOffType.from("locked".equalsIgnoreCase(lock)));
            updateState(GROUP_DOORS + d + "Open", openClosed(open));
        }
        updateState(CHANNEL_DOORS_HOOD, openClosed(nested(object(doors, "hood"), "openStatus", "status")));
        JsonObject seat = object(p, "rearSeatReminder");
        if (seat != null) {
            JsonElement w = seat.get("warning");
            String reason = string(seat, "reason");
            updateState(CHANNEL_DOORS_REAR_SEAT, w != null && w.isJsonPrimitive() && w.getAsBoolean()
                    ? new StringType("warning" + (reason == null ? "" : ": " + reason))
                    : new StringType(reason == null ? "ok" : reason));
        }

        JsonObject windows = object(p, "windows");
        if (windows != null) {
            for (String wn : WINDOWS) {
                String st = string(object(windows, wn), "status");
                updateState(GROUP_WINDOWS + wn, st == null ? UnDefType.UNDEF : new StringType(st));
            }
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
            for (String ln : LIGHTS) {
                String st = nested(lights, ln, "status");
                updateState(GROUP_LIGHTS + ln, st == null ? UnDefType.UNDEF : OnOffType.from("on".equalsIgnoreCase(st)));
            }
            String hazard = nested(lights, "hazard", "status");
            State hazardState = hazard == null ? UnDefType.UNDEF : OnOffType.from("on".equalsIgnoreCase(hazard));
            updateState(CHANNEL_STATUS_HAZARD, hazardState);
            updateState(CHANNEL_CONTROL_HAZARD, hazardState);
        }
    }

    private void updateClimate(JsonObject resp) {
        String status = string(payload(resp), "status");
        updateState(CHANNEL_CLIMATE_STATUS, status == null ? UnDefType.UNDEF : new StringType(status));
        // the climate command channel mirrors the car: ON while starting or running
        updateState(CHANNEL_CONTROL_CLIMATE, status == null ? UnDefType.UNDEF
                : OnOffType.from(!"stopped".equalsIgnoreCase(status) && !"off".equalsIgnoreCase(status)));
    }

    /** One-shot command channels rest at OFF so their Switch items never show NULL. */
    private void restOneShotChannels() {
        for (String ch : new String[] { CHANNEL_CONTROL_REFRESH, CHANNEL_CONTROL_HORN, CHANNEL_CONTROL_FIND,
                CHANNEL_CONTROL_CHARGE_NOW, CHANNEL_CONTROL_BUZZER, CHANNEL_CONTROL_WINDOWS_OPEN,
                CHANNEL_CONTROL_WINDOWS_CLOSE, CHANNEL_CONTROL_VENTILATION }) {
            if (getThing().getChannel(ch) != null) {
                updateState(ch, OnOffType.OFF);
            }
        }
    }

    /**
     * The app's own messages ("Your car is unlocked", "a keyfob was detected", "Climate Start requires at
     * least 31% battery"): newest first, with an unread flag. The car's name or VIN prefix is stripped so the
     * text reads the same in openHAB as in the app and the VIN stays out of item states and logs.
     */
    private String lastNotificationId = "";

    private void updateNotifications(JsonObject resp) {
        JsonElement pl = resp.get("payload");
        JsonObject first = null;
        if (pl != null && pl.isJsonArray() && !pl.getAsJsonArray().isEmpty() && pl.getAsJsonArray().get(0).isJsonObject()) {
            first = pl.getAsJsonArray().get(0).getAsJsonObject();
        } else if (pl != null && pl.isJsonObject()) {
            first = pl.getAsJsonObject();
        }
        JsonElement listEl = first == null ? null : first.get("notifications");
        if (listEl == null || !listEl.isJsonArray()) {
            return;
        }
        JsonArray list = listEl.getAsJsonArray();
        int unread = 0;
        StringBuilder recent = new StringBuilder();
        int shown = 0;
        for (JsonElement el : list) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject n = el.getAsJsonObject();
            JsonElement read = n.get("isRead");
            if (read != null && read.isJsonPrimitive() && !read.getAsBoolean()) {
                unread++;
            }
            if (shown < 5) {
                String when = string(n, "notificationDate");
                String stamp = "";
                if (when != null) {
                    try {
                        stamp = Instant.parse(when).atZone(ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")) + " ";
                    } catch (DateTimeParseException e) {
                        stamp = "";
                    }
                }
                recent.append(shown > 0 ? "\n" : "").append(stamp).append(cleanMessage(string(n, "message")));
                shown++;
            }
        }
        updateState(CHANNEL_NOTIFY_UNREAD, new DecimalType(unread));
        updateState(CHANNEL_NOTIFY_RECENT, recent.length() == 0 ? UnDefType.UNDEF : new StringType(recent.toString()));
        if (list.isEmpty() || !list.get(0).isJsonObject()) {
            return;
        }
        JsonObject latest = list.get(0).getAsJsonObject();
        String id = string(latest, "messageId");
        if (id != null && id.equals(lastNotificationId)) {
            return;
        }
        lastNotificationId = id == null ? "" : id;
        String message = cleanMessage(string(latest, "message"));
        updateState(CHANNEL_NOTIFY_LATEST, message.isEmpty() ? UnDefType.UNDEF : new StringType(message));
        updateState(CHANNEL_NOTIFY_LATEST_TIME, dateTime(string(latest, "notificationDate")));
        String category = string(latest, "category");
        updateState(CHANNEL_NOTIFY_LATEST_CATEGORY, category == null ? UnDefType.UNDEF : new StringType(category));
        logger.info("Notification for {}: {}", shortVin(), message);
    }

    /** "Dream Catcher II : text", "Dream Catcher II: text" or "<VIN>: text" become "text". */
    private String cleanMessage(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        int colon = text.indexOf(':');
        if (colon > 0 && colon < 40) {
            String prefix = text.substring(0, colon).trim();
            if (prefix.equalsIgnoreCase(vin) || prefix.equalsIgnoreCase(getThing().getProperties().getOrDefault(PROPERTY_NICKNAME, " "))
                    || !prefix.contains(" ") && prefix.length() == 17) {
                text = text.substring(colon + 1).trim();
            } else if (prefix.matches("[A-Za-z0-9 '\\-]{2,30}") && text.length() > colon + 2) {
                // an unknown name-like prefix: strip it too, the app does not repeat it
                text = text.substring(colon + 1).trim();
            }
        }
        return text.replace(vin, "the car");
    }

    /**
     * The warnings behind the count on the status page, in words: /v1/vehiclehealth/status lists each active
     * warning with a code (TIRW), a description ("Tire Pressure Warning System"), a severity and when it began.
     * Nothing active gives "none".
     */
    private void updateHealth(JsonObject resp) {
        JsonObject p = payload(resp);
        JsonElement list = p.get("warning");
        StringBuilder text = new StringBuilder();
        StringBuilder codes = new StringBuilder();
        int worst = 0;
        if (list != null && list.isJsonArray()) {
            for (JsonElement el : list.getAsJsonArray()) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject w = el.getAsJsonObject();
                String desc = string(w, "wngdesc");
                String code = string(w, "wngcode");
                Double sev = number(w.get("severity"));
                String since = string(w, "wngdcmtime");
                String sinceText = "";
                if (since != null) {
                    try {
                        sinceText = " since " + Instant.parse(since).atZone(ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
                    } catch (DateTimeParseException e) {
                        sinceText = "";
                    }
                }
                if (text.length() > 0) {
                    text.append("; ");
                    codes.append(", ");
                }
                text.append(desc == null ? (code == null ? "unknown" : code) : desc)
                        .append(sev == null ? "" : " (severity " + sev.intValue() + ")").append(sinceText);
                codes.append(code == null ? "?" : code);
                if (sev != null && sev.intValue() > worst) {
                    worst = sev.intValue();
                }
            }
        }
        updateState(CHANNEL_HEALTH_WARNINGS, new StringType(text.length() == 0 ? "none" : text.toString()));
        updateState(CHANNEL_HEALTH_WARNING_CODES, new StringType(codes.length() == 0 ? "" : codes.toString()));
        updateState(CHANNEL_HEALTH_SEVERITY, new DecimalType(worst));
        updateState(CHANNEL_HEALTH_TIMESTAMP, dateTime(string(p, "wnglastUpdTime")));
    }

    /**
     * Trips from the cloud: the newest trip in detail, today's and this month's totals from the summary the
     * same call returns. Units as Toyota sends them: metres, seconds, km/h, millilitres of fuel (null on an
     * EV), plus the EV distance from the "hdc" block and the driving score. Built from pytoyoda's model;
     * confirmed field by field on the first real trip.
     */
    private void updateTrips(MyToyotaApiClient client) {
        ZonedDateTime now = ZonedDateTime.now();
        String from = now.minusDays(30).toLocalDate().toString();
        String to = now.toLocalDate().toString();
        JsonObject resp;
        try {
            resp = client.get(String.format(MyToyotaApiClient.ENDPOINT_TRIPS, from, to, 1), vin);
        } catch (MyToyotaApiException e) {
            logger.debug("Trips for {} failed: {}", shortVin(), e.getMessage());
            return;
        }
        JsonObject p = payload(resp);
        JsonElement tripsEl = p.get("trips");
        JsonObject meta = object(object(p, "_metadata"), "pagination");
        Double total = meta == null ? null : number(meta.get("totalCount"));
        updateState(CHANNEL_TRIPS_COUNT_30D, total == null ? UnDefType.UNDEF : new DecimalType(total.intValue()));
        if (tripsEl != null && tripsEl.isJsonArray() && !tripsEl.getAsJsonArray().isEmpty()
                && tripsEl.getAsJsonArray().get(0).isJsonObject()) {
            JsonObject trip = tripsEl.getAsJsonArray().get(0).getAsJsonObject();
            JsonObject sum = object(trip, "summary");
            updateState(CHANNEL_TRIPS_LATEST_START, dateTime(string(sum, "startTs")));
            updateState(CHANNEL_TRIPS_LATEST_END, dateTime(string(sum, "endTs")));
            updateState(CHANNEL_TRIPS_LATEST_DISTANCE, metres(sum == null ? null : sum.get("length")));
            updateState(CHANNEL_TRIPS_LATEST_DURATION, seconds(sum == null ? null : sum.get("duration")));
            updateState(CHANNEL_TRIPS_LATEST_SPEED, quantity(sum == null ? null : sum.get("averageSpeed"), SIUnits.KILOMETRE_PER_HOUR));
            updateState(CHANNEL_TRIPS_LATEST_FUEL, millilitres(sum == null ? null : sum.get("fuelConsumption")));
            JsonObject hdc = object(trip, "hdc");
            updateState(CHANNEL_TRIPS_LATEST_EV_DISTANCE, metres(hdc == null ? null : hdc.get("evDistance")));
            JsonObject scores = object(trip, "scores");
            Double score = scores == null ? null : number(scores.get("global"));
            updateState(CHANNEL_TRIPS_LATEST_SCORE, score == null ? UnDefType.UNDEF : new DecimalType(score.intValue()));
            updateState(CHANNEL_TRIPS_LATEST_START_POS, point(sum, "startLat", "startLon"));
            updateState(CHANNEL_TRIPS_LATEST_END_POS, point(sum, "endLat", "endLon"));
            String tripId = string(trip, "id");
            updateState(CHANNEL_TRIPS_LATEST_ID, tripId == null ? UnDefType.UNDEF : new StringType(tripId));
            updateState(CHANNEL_TRIPS_LATEST_ROUTE, route(trip.get("route")));
        }
        // month and day summaries
        JsonElement sumsEl = p.get("summary");
        State monthDist = UnDefType.UNDEF, monthDur = UnDefType.UNDEF, monthFuel = UnDefType.UNDEF, todayDist = UnDefType.UNDEF;
        if (sumsEl != null && sumsEl.isJsonArray()) {
            for (JsonElement el : sumsEl.getAsJsonArray()) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject m = el.getAsJsonObject();
                Double y = number(m.get("year"));
                Double mo = number(m.get("month"));
                if (y == null || mo == null || y.intValue() != now.getYear() || mo.intValue() != now.getMonthValue()) {
                    continue;
                }
                JsonObject ms = object(m, "summary");
                monthDist = metres(ms == null ? null : ms.get("length"));
                monthDur = seconds(ms == null ? null : ms.get("duration"));
                monthFuel = millilitres(ms == null ? null : ms.get("fuelConsumption"));
                JsonElement hist = m.get("histograms");
                if (hist != null && hist.isJsonArray()) {
                    for (JsonElement h : hist.getAsJsonArray()) {
                        if (!h.isJsonObject()) {
                            continue;
                        }
                        Double d = number(h.getAsJsonObject().get("day"));
                        if (d != null && d.intValue() == now.getDayOfMonth()) {
                            JsonObject ds = object(h.getAsJsonObject(), "summary");
                            todayDist = metres(ds == null ? null : ds.get("length"));
                        }
                    }
                }
            }
        }
        updateState(CHANNEL_TRIPS_MONTH_DISTANCE, monthDist);
        updateState(CHANNEL_TRIPS_MONTH_DURATION, monthDur);
        updateState(CHANNEL_TRIPS_MONTH_FUEL, monthFuel);
        updateState(CHANNEL_TRIPS_TODAY_DISTANCE, todayDist);
        updateState(CHANNEL_TRIPS_TIMESTAMP, new DateTimeType(now));
    }

    /** Service history: how many records and the newest one. */
    private void updateService(JsonObject resp) {
        JsonElement list = payload(resp).get("serviceHistories");
        if (list == null || !list.isJsonArray()) {
            return;
        }
        JsonArray arr = list.getAsJsonArray();
        updateState(CHANNEL_SERVICE_COUNT, new DecimalType(arr.size()));
        JsonObject newest = null;
        String newestDate = "";
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            String d = string(el.getAsJsonObject(), "serviceDate");
            if (d != null && d.compareTo(newestDate) > 0) {
                newestDate = d;
                newest = el.getAsJsonObject();
            }
        }
        if (newest == null) {
            updateState(CHANNEL_SERVICE_LAST_DATE, UnDefType.UNDEF);
            updateState(CHANNEL_SERVICE_LAST_CATEGORY, new StringType("none"));
            updateState(CHANNEL_SERVICE_LAST_PROVIDER, UnDefType.UNDEF);
            updateState(CHANNEL_SERVICE_LAST_MILEAGE, UnDefType.UNDEF);
            return;
        }
        try {
            updateState(CHANNEL_SERVICE_LAST_DATE, new DateTimeType(java.time.LocalDate.parse(newestDate).atStartOfDay(ZoneId.systemDefault())));
        } catch (DateTimeParseException e) {
            updateState(CHANNEL_SERVICE_LAST_DATE, UnDefType.UNDEF);
        }
        String cat = string(newest, "serviceCategory");
        updateState(CHANNEL_SERVICE_LAST_CATEGORY, cat == null ? UnDefType.UNDEF : new StringType(cat));
        String prov = string(newest, "serviceProvider");
        updateState(CHANNEL_SERVICE_LAST_PROVIDER, prov == null ? UnDefType.UNDEF : new StringType(prov));
        Double km = number(newest.get("mileage"));
        String unit = string(newest, "unit");
        updateState(CHANNEL_SERVICE_LAST_MILEAGE, km == null ? UnDefType.UNDEF
                : new QuantityType<>(km, "mi".equalsIgnoreCase(unit) ? ImperialUnits.MILE : MetricPrefix.KILO(SIUnits.METRE)));
    }

    private static State point(@Nullable JsonObject o, String latKey, String lonKey) {
        if (o == null) {
            return UnDefType.UNDEF;
        }
        Double lat = number(o.get(latKey));
        Double lon = number(o.get(lonKey));
        return lat == null || lon == null ? UnDefType.UNDEF : new PointType(new DecimalType(lat), new DecimalType(lon));
    }

    /**
     * The trip's route as one compact string: "lat,lon,flags;lat,lon,flags;…" with five decimals (about a
     * metre) and flags e = driven electrically, h = highway, o = over the speed limit. A 30 minute trip is
     * a few hundred points, well within a String item. Consumers (a map) split on ";" and ",".
     */
    private static State route(@Nullable JsonElement e) {
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().isEmpty()) {
            return UnDefType.UNDEF;
        }
        StringBuilder sb = new StringBuilder();
        for (JsonElement pEl : e.getAsJsonArray()) {
            if (!pEl.isJsonObject()) {
                continue;
            }
            JsonObject pt = pEl.getAsJsonObject();
            Double lat = number(pt.get("lat"));
            Double lon = number(pt.get("lon"));
            if (lat == null || lon == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(String.format(java.util.Locale.ROOT, "%.5f,%.5f,", lat, lon));
            if (flag(pt, "isEv")) {
                sb.append('e');
            }
            if (flag(pt, "highway")) {
                sb.append('h');
            }
            if (flag(pt, "overspeed")) {
                sb.append('o');
            }
        }
        return sb.length() == 0 ? UnDefType.UNDEF : new StringType(sb.toString());
    }

    private static State metres(@Nullable JsonElement e) {
        Double v = number(e);
        return v == null ? UnDefType.UNDEF : new QuantityType<>(v / 1000.0, MetricPrefix.KILO(SIUnits.METRE));
    }

    private static State seconds(@Nullable JsonElement e) {
        Double v = number(e);
        return v == null ? UnDefType.UNDEF : new QuantityType<>(v / 60.0, Units.MINUTE);
    }

    private static State millilitres(@Nullable JsonElement e) {
        Double v = number(e);
        return v == null ? UnDefType.UNDEF : new QuantityType<>(v / 1000.0, Units.LITRE);
    }

    private void putOption(JsonObject target, String apiKey, String channelId) {
        String v = climateOptions.get(channelId);
        if (v != null) {
            target.addProperty(apiKey, v);
        }
    }

    /** ON on a one-shot channel runs the command once; the channel returns to OFF. */
    private void oneShot(String channelId, Command command, String remote) {
        if (command == OnOffType.ON) {
            remoteCommand(remote);
            updateState(channelId, OnOffType.OFF);
        }
    }

    /**
     * Creates the optional command channels this car can use, from the extendedCapabilities in the
     * account's vehicle list (and a few remoteServiceCapabilities flags), and drops those it cannot.
     * A bZ4X gets trunk lock, buzzer and the climate options; a hybrid also gets engine start.
     */
    private void provisionOptionalChannels(MyToyotaAccountHandler account) {
        JsonObject vehicle;
        try {
            vehicle = account.findVehicle(vin);
        } catch (MyToyotaApiException e) {
            logger.debug("Could not read capabilities for {}: {}", shortVin(), e.getMessage());
            return;
        }
        if (vehicle == null) {
            channelsProvisioned = true;
            return;
        }
        JsonObject ext = object(vehicle, "extendedCapabilities");
        JsonObject rsc = object(vehicle, "remoteServiceCapabilities");
        ThingBuilder builder = editThing();
        List<Channel> channels = new ArrayList<>(getThing().getChannels());
        boolean changed = false;
        List<String> added = new ArrayList<>();
        for (String[] def : OPTIONAL_CHANNELS) {
            String id = def[0];
            boolean capable = false;
            for (int i = 3; i < def.length; i++) {
                capable |= flag(ext, def[i]) || flag(rsc, def[i]);
            }
            ChannelUID uid = new ChannelUID(getThing().getUID(), id.replace('#', ':').split(":")[0], id.substring(id.indexOf('#') + 1));
            boolean present = getThing().getChannel(uid) != null;
            if (capable && !present) {
                channels.add(ChannelBuilder.create(uid, def[2]).withType(new ChannelTypeUID(BINDING_ID, def[1])).build());
                added.add(id);
                changed = true;
            } else if (!capable && present) {
                channels.removeIf(c -> c.getUID().equals(uid));
                changed = true;
            }
        }
        if (changed) {
            updateThing(builder.withChannels(channels).build());
            logger.info("Optional channels for {}: {}", shortVin(), added.isEmpty() ? "none added" : String.join(", ", added));
        }
        channelsProvisioned = true;
        restOneShotChannels();
    }

    private static boolean flag(@Nullable JsonObject o, String key) {
        if (o == null) {
            return false;
        }
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
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
        JsonObject heating = object(p, "heatingOptions");
        JsonObject seats = object(p, "seatOptions");
        seedOption(heating, "frontDefroster", CHANNEL_CONTROL_DEFROST_FRONT);
        seedOption(heating, "rearDefogger", CHANNEL_CONTROL_DEFROST_REAR);
        seedOption(heating, "steeringHeater", CHANNEL_CONTROL_STEERING_HEATER);
        seedOption(heating, "mirrorHeater", CHANNEL_CONTROL_MIRROR_HEATER);
        seedOption(seats, "driverSeat", CHANNEL_CONTROL_SEAT_DRIVER);
        seedOption(seats, "passengerSeat", CHANNEL_CONTROL_SEAT_PASSENGER);
        seedOption(seats, "rearDriverSeat", CHANNEL_CONTROL_SEAT_REAR_LEFT);
        seedOption(seats, "rearPassengerSeat", CHANNEL_CONTROL_SEAT_REAR_RIGHT);
        climateSeeded = true;
    }

    // ----------------------------------------------------------------- helpers

    /** A saved on/off option becomes the channel's state and the value sent with the next start. */
    private void seedOption(@Nullable JsonObject o, String apiKey, String channelId) {
        String v = string(o, apiKey);
        if (v == null || getThing().getChannel(channelId) == null) {
            return;
        }
        boolean on = "on".equalsIgnoreCase(v) || "high".equalsIgnoreCase(v) || "low".equalsIgnoreCase(v);
        climateOptions.put(channelId, on ? "on" : "off");
        updateState(channelId, OnOffType.from(on));
    }

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
