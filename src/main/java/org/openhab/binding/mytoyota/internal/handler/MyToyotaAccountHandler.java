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

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mytoyota.internal.api.MyToyotaApiClient;
import org.openhab.binding.mytoyota.internal.api.MyToyotaApiException;
import org.openhab.binding.mytoyota.internal.discovery.MyToyotaDiscoveryService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The account bridge: owns the API session and the poll settings that every vehicle under it uses.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class MyToyotaAccountHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(MyToyotaAccountHandler.class);

    private MyToyotaAccountConfiguration config = new MyToyotaAccountConfiguration();
    private @Nullable MyToyotaApiClient client;
    private @Nullable ScheduledFuture<?> loginJob;
    private @Nullable ScheduledFuture<?> recoveryJob;
    private boolean recoveryPending;
    private volatile boolean disposed;

    public MyToyotaAccountHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void initialize() {
        disposed = false;
        config = getConfigAs(MyToyotaAccountConfiguration.class);
        if (config.username.isBlank() || config.password.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Username and password are required");
            return;
        }
        String brand = config.brand == null || config.brand.isBlank() ? "T" : config.brand.trim().toUpperCase();
        client = new MyToyotaApiClient(config.username.trim(), config.password, brand);
        updateStatus(ThingStatus.UNKNOWN);
        loginJob = schedule(this::tryLogin, 1, TimeUnit.SECONDS);
    }

    /** Every delayed job of this handler goes through here (a test runs them by hand). */
    @Nullable
    ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return scheduler.schedule(task, delay, unit);
    }

    private void tryLogin() {
        MyToyotaApiClient c = client;
        if (c == null) {
            return;
        }
        try {
            c.login();
            updateStatus(ThingStatus.ONLINE);
        } catch (MyToyotaApiException e) {
            logger.debug("Login failed: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            // try again later; a wrong password stays offline with the message above
            loginJob = schedule(this::tryLogin, 10, TimeUnit.MINUTES);
        }
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> job = loginJob;
        if (job != null) {
            job.cancel(true);
            loginJob = null;
        }
        disposed = true;
        cancelRecovery();
        client = null;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // the bridge has no channels
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Set.of(MyToyotaDiscoveryService.class);
    }

    public @Nullable MyToyotaApiClient getClient() {
        return client;
    }

    public MyToyotaAccountConfiguration getAccountConfig() {
        return config;
    }

    /** The vehicles of this account as returned by the cloud (payload array). */
    public JsonArray listVehicles() throws MyToyotaApiException {
        MyToyotaApiClient c = client;
        if (c == null) {
            throw new MyToyotaApiException("Account not initialised");
        }
        JsonObject resp = c.get(MyToyotaApiClient.ENDPOINT_VEHICLES, null);
        JsonArray payload = resp.getAsJsonArray("payload");
        return payload == null ? new JsonArray() : payload;
    }

    /** The vehicle list entry for a VIN (capabilities, model, nickname), or null. */
    public @Nullable JsonObject findVehicle(String vin) throws MyToyotaApiException {
        for (com.google.gson.JsonElement el : listVehicles()) {
            if (el.isJsonObject()) {
                JsonObject v = el.getAsJsonObject();
                com.google.gson.JsonElement vinEl = v.get("vin");
                if (vinEl != null && vinEl.isJsonPrimitive() && vin.equalsIgnoreCase(vinEl.getAsString())) {
                    return v;
                }
            }
        }
        return null;
    }

    /**
     * Called by a vehicle handler after each poll, so the bridge status reflects it.
     *
     * A vehicle stops polling while its bridge is OFFLINE, so once a failed poll has taken the bridge OFFLINE
     * no vehicle will ever report success again: the bridge must find its own way back (until 1.9.11 it never
     * did - a FortiGate reboot on 2026-11-03 left the account and the car OFFLINE until restarted by hand).
     */
    void reportCommunication(boolean ok, @Nullable String message) {
        if (ok) {
            if (getThing().getStatus() != ThingStatus.ONLINE) {
                cancelRecovery();
                updateStatus(ThingStatus.ONLINE);
            }
        } else if (getThing().getStatus() == ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
            scheduleRecovery();
        }
    }

    /** One retry pending at a time, one poll interval away - the cadence the vehicles had. */
    private synchronized void scheduleRecovery() {
        if (recoveryPending || disposed) {
            return;
        }
        recoveryPending = true;
        recoveryJob = schedule(this::recover, Math.max(1, config.pollInterval), TimeUnit.MINUTES);
    }

    private synchronized void cancelRecovery() {
        ScheduledFuture<?> job = recoveryJob;
        if (job != null) {
            job.cancel(false);
        }
        recoveryJob = null;
        recoveryPending = false;
    }

    /** Ask the cloud for the vehicle list: ONLINE when it answers (the vehicles then poll again), else retry. */
    private void recover() {
        synchronized (this) {
            recoveryJob = null;
            recoveryPending = false;
        }
        if (getThing().getStatus() == ThingStatus.ONLINE) {
            return;
        }
        try {
            listVehicles();
            logger.debug("MyToyota cloud answers again, account back ONLINE");
            updateStatus(ThingStatus.ONLINE);
        } catch (MyToyotaApiException | RuntimeException e) {
            logger.debug("MyToyota cloud still unreachable: {}", e.getMessage());
            scheduleRecovery();
        }
    }
}
