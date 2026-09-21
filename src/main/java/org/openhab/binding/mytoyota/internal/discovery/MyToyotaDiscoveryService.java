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
package org.openhab.binding.mytoyota.internal.discovery;

import static org.openhab.binding.mytoyota.internal.MyToyotaBindingConstants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.mytoyota.internal.api.MyToyotaApiException;
import org.openhab.binding.mytoyota.internal.handler.MyToyotaAccountHandler;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Lists the vehicles of the account and offers each as a vehicle thing.
 *
 * @author Nanna Agesen - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = MyToyotaDiscoveryService.class)
@NonNullByDefault
public class MyToyotaDiscoveryService extends AbstractThingHandlerDiscoveryService<MyToyotaAccountHandler> {

    private final Logger logger = LoggerFactory.getLogger(MyToyotaDiscoveryService.class);

    public MyToyotaDiscoveryService() {
        super(MyToyotaAccountHandler.class, Set.of(THING_TYPE_VEHICLE), 20);
    }

    @Override
    protected void startScan() {
        MyToyotaAccountHandler account = thingHandler;
        if (account == null) {
            return;
        }
        try {
            ThingUID bridgeUID = account.getThing().getUID();
            int n = 0;
            for (JsonElement el : account.listVehicles()) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject v = el.getAsJsonObject();
                String vin = text(v, "vin");
                if (vin.length() != 17) {
                    continue;
                }
                String model = text(v, "modelName");
                String year = text(v, "modelYear");
                String nick = text(v, "nickName");
                String label = (nick.isEmpty() ? model : nick + " (" + model + ")") + (year.isEmpty() ? "" : " " + year);

                Map<String, Object> properties = new HashMap<>();
                properties.put(PROPERTY_VIN, vin);
                properties.put(PROPERTY_MODEL, model);
                properties.put(PROPERTY_MODEL_YEAR, year);
                properties.put(PROPERTY_NICKNAME, nick);
                properties.put(PROPERTY_GENERATION, text(v, "generation"));
                properties.put(PROPERTY_EV, text(v, "evVehicle"));
                properties.put(PROPERTY_CAPABILITIES, capabilities(v));

                DiscoveryResult result = DiscoveryResultBuilder.create(new ThingUID(THING_TYPE_VEHICLE, bridgeUID, vin))
                        .withBridge(bridgeUID).withProperties(properties).withRepresentationProperty(PROPERTY_VIN)
                        .withLabel(label.isBlank() ? "Toyota " + vin.substring(13) : label).build();
                thingDiscovered(result);
                n++;
            }
            logger.debug("Discovered {} vehicle(s)", n);
        } catch (MyToyotaApiException e) {
            logger.debug("Vehicle discovery failed: {}", e.getMessage());
        }
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? "" : e.getAsString();
    }

    /** The true flags of extendedCapabilities, comma separated, so the thing shows what the car can do. */
    private static String capabilities(JsonObject v) {
        JsonElement ext = v.get("extendedCapabilities");
        if (ext == null || !ext.isJsonObject()) {
            return "";
        }
        return ext.getAsJsonObject().entrySet().stream()
                .filter(e -> e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isBoolean()
                        && e.getValue().getAsBoolean())
                .map(Map.Entry::getKey).sorted().collect(Collectors.joining(", "));
    }
}
