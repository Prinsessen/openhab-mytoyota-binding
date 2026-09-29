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
package org.openhab.binding.mytoyota.internal;

import static org.openhab.binding.mytoyota.internal.MyToyotaBindingConstants.*;

import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * What the account's vehicle list (/v2/vehicle/guid) says about one car, read the same way by discovery
 * and by the vehicle handler, so a thing from a .things file ends up with the same properties as a
 * discovered one.
 *
 * The rules for "is this an electric car" and "what kind of car is it" follow pytoyoda 5.2.9
 * (Vehicle._electric_capable, VehicleType.from_vehicle_info): Toyota's registry is wrong about EV
 * status for some platforms, so the fuel type and the top-level evVehicle flag are consulted as well
 * as the capability flag. A car that is not electric-capable is never asked for its battery.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public final class MyToyotaVehicleInfo {

    private MyToyotaVehicleInfo() {
    }

    public static String text(@Nullable JsonObject o, String key) {
        if (o == null) {
            return "";
        }
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? "" : e.getAsString();
    }

    public static boolean flag(@Nullable JsonObject o, String key) {
        if (o == null) {
            return false;
        }
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    public static @Nullable JsonObject object(@Nullable JsonObject o, String key) {
        if (o == null) {
            return null;
        }
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    /**
     * The true flags of extendedCapabilities and remoteServiceCapabilities, sorted, comma separated -
     * what a user pastes into a forum post when asking why a channel is missing.
     */
    public static String capabilities(JsonObject v) {
        TreeSet<String> flags = new TreeSet<>();
        for (String container : new String[] { "extendedCapabilities", "remoteServiceCapabilities" }) {
            JsonObject o = object(v, container);
            if (o == null) {
                continue;
            }
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isBoolean()
                        && e.getValue().getAsBoolean()) {
                    flags.add(e.getKey());
                }
            }
        }
        return String.join(", ", flags);
    }

    /** True when either capability container carries this flag set to true. */
    public static boolean capable(JsonObject v, String key) {
        return flag(object(v, "extendedCapabilities"), key) || flag(object(v, "remoteServiceCapabilities"), key);
    }

    /**
     * Whether the car has a tank the cloud reports on. Read from the capabilities rather than the
     * fuel type, because Toyota's registry is wrong about some cars (see vehicleType).
     */
    public static boolean burnsFuel(JsonObject v) {
        return capable(v, "fuelLevelAvailable") || capable(v, "fuelRangeAvailable") || capable(v, "hybridPulse");
    }

    /**
     * Toyota's fuelType code: B full hybrid, E electric, I plug-in hybrid; anything else burns fuel
     * only. The registry gets this wrong for some plug-in hybrids - a 2025 RAV4 PHEV came back as
     * fuelType E on the forum, 2026-09-29 - so a car that the same payload says has a fuel level is
     * reported as a plug-in hybrid whatever the code says. pytoyoda 5.2.9 trusts the code alone and
     * calls that car electric.
     */
    public static String vehicleType(JsonObject v) {
        String fuel = text(v, "fuelType");
        if (flag(v, "evVehicle") || "E".equals(fuel)) {
            return burnsFuel(v) ? "plug-in hybrid" : "electric";
        }
        switch (fuel) {
            case "I":
                return "plug-in hybrid";
            case "B":
                return "full hybrid";
            default:
                return "fuel-only";
        }
    }

    /**
     * Whether the electric status endpoint is worth asking. A full hybrid and a combustion car have no
     * traction battery the cloud reports on; asking anyway used to fail the whole poll and take the
     * car offline.
     */
    public static boolean electricCapable(JsonObject v) {
        String fuel = text(v, "fuelType");
        return flag(object(v, "extendedCapabilities"), "econnectVehicleStatusCapable") || flag(v, "evVehicle")
                || "E".equals(fuel) || "I".equals(fuel);
    }

    /** The thing properties for this car; the same map from discovery and from the handler. */
    public static Map<String, String> properties(JsonObject v) {
        Map<String, String> p = new TreeMap<>();
        p.put(PROPERTY_VIN, text(v, "vin"));
        p.put(PROPERTY_MODEL, text(v, "modelName"));
        p.put(PROPERTY_MODEL_YEAR, text(v, "modelYear"));
        p.put(PROPERTY_NICKNAME, text(v, "nickName"));
        p.put(PROPERTY_GENERATION, text(v, "generation"));
        p.put(PROPERTY_EV, text(v, "evVehicle"));
        p.put(PROPERTY_FUEL_TYPE, text(v, "fuelType"));
        p.put(PROPERTY_VEHICLE_TYPE, vehicleType(v));
        p.put(PROPERTY_CAPABILITIES, capabilities(v));
        return p;
    }
}
