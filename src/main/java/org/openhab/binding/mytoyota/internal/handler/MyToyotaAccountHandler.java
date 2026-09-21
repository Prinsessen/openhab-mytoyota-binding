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

    public MyToyotaAccountHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void initialize() {
        config = getConfigAs(MyToyotaAccountConfiguration.class);
        if (config.username.isBlank() || config.password.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Username and password are required");
            return;
        }
        String brand = config.brand == null || config.brand.isBlank() ? "T" : config.brand.trim().toUpperCase();
        client = new MyToyotaApiClient(config.username.trim(), config.password, brand);
        updateStatus(ThingStatus.UNKNOWN);
        loginJob = scheduler.schedule(this::tryLogin, 1, TimeUnit.SECONDS);
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
            loginJob = scheduler.schedule(this::tryLogin, 10, TimeUnit.MINUTES);
        }
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> job = loginJob;
        if (job != null) {
            job.cancel(true);
            loginJob = null;
        }
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

    /** Called by a vehicle handler when a call failed, so the bridge status reflects it. */
    void reportCommunication(boolean ok, @Nullable String message) {
        if (ok) {
            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }
        } else if (getThing().getStatus() == ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
        }
    }
}
