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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * Constants of the MyToyota binding: thing types, channel ids, thing properties.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class MyToyotaBindingConstants {

    public static final String BINDING_ID = "mytoyota";

    public static final ThingTypeUID THING_TYPE_ACCOUNT = new ThingTypeUID(BINDING_ID, "account");
    public static final ThingTypeUID THING_TYPE_VEHICLE = new ThingTypeUID(BINDING_ID, "vehicle");

    // battery
    public static final String CHANNEL_BATTERY_LEVEL = "battery#level";
    public static final String CHANNEL_BATTERY_RANGE = "battery#range";
    public static final String CHANNEL_BATTERY_RANGE_AC = "battery#rangeWithAc";
    public static final String CHANNEL_BATTERY_CHARGING = "battery#chargingStatus";
    public static final String CHANNEL_BATTERY_CHARGING_ACTIVE = "battery#charging";
    public static final String CHANNEL_BATTERY_REMAINING_TIME = "battery#remainingChargeTime";
    public static final String CHANNEL_BATTERY_TIMESTAMP = "battery#lastUpdate";

    // telemetry
    public static final String CHANNEL_TELEMETRY_ODOMETER = "telemetry#odometer";
    public static final String CHANNEL_TELEMETRY_DTE = "telemetry#distanceToEmpty";
    public static final String CHANNEL_TELEMETRY_TIMESTAMP = "telemetry#lastUpdate";

    // location
    public static final String CHANNEL_LOCATION_POSITION = "location#position";
    public static final String CHANNEL_LOCATION_NAME = "location#name";
    public static final String CHANNEL_LOCATION_TIMESTAMP = "location#lastUpdate";

    // status
    public static final String CHANNEL_STATUS_OVERALL = "status#overall";
    public static final String CHANNEL_STATUS_WARNINGS = "status#warnings";
    public static final String CHANNEL_STATUS_LOCKED = "status#locked";
    public static final String CHANNEL_STATUS_DOOR_OPEN = "status#anyDoorOpen";
    public static final String CHANNEL_STATUS_WINDOW_OPEN = "status#anyWindowOpen";
    public static final String CHANNEL_STATUS_TRUNK_OPEN = "status#trunkOpen";
    public static final String CHANNEL_STATUS_HOOD_OPEN = "status#hoodOpen";
    public static final String CHANNEL_STATUS_HAZARD = "status#hazardLights";
    public static final String CHANNEL_STATUS_TIMESTAMP = "status#lastUpdate";

    // climate
    public static final String CHANNEL_CLIMATE_STATUS = "climate#status";

    // control
    public static final String CHANNEL_CONTROL_REFRESH = "control#refresh";
    public static final String CHANNEL_CONTROL_LAST_WAKE = "control#lastWake";
    public static final String CHANNEL_CONTROL_LAST_POLL = "control#lastPoll";
    public static final String CHANNEL_CONTROL_LOCK = "control#lock";
    public static final String CHANNEL_CONTROL_HAZARD = "control#hazardLights";
    public static final String CHANNEL_CONTROL_HORN = "control#horn";
    public static final String CHANNEL_CONTROL_FIND = "control#findVehicle";
    public static final String CHANNEL_CONTROL_CLIMATE = "control#climate";
    public static final String CHANNEL_CONTROL_CLIMATE_TEMPERATURE = "control#climateTemperature";
    public static final String CHANNEL_CONTROL_CLIMATE_DURATION = "control#climateDuration";
    public static final String CHANNEL_CONTROL_CHARGE_NOW = "control#chargeNow";
    public static final String CHANNEL_CONTROL_LAST_RESULT = "control#lastCommandResult";

    // notifications (what the app shows)
    public static final String CHANNEL_NOTIFY_LATEST = "notifications#latest";
    public static final String CHANNEL_NOTIFY_LATEST_TIME = "notifications#latestTime";
    public static final String CHANNEL_NOTIFY_LATEST_CATEGORY = "notifications#latestCategory";
    public static final String CHANNEL_NOTIFY_UNREAD = "notifications#unread";
    public static final String CHANNEL_NOTIFY_RECENT = "notifications#recent";

    // thing properties (vehicle)
    public static final String PROPERTY_VIN = "vin";
    public static final String PROPERTY_MODEL = "modelName";
    public static final String PROPERTY_MODEL_YEAR = "modelYear";
    public static final String PROPERTY_NICKNAME = "nickName";
    public static final String PROPERTY_GENERATION = "generation";
    public static final String PROPERTY_EV = "evVehicle";
    public static final String PROPERTY_CAPABILITIES = "capabilities";

    private MyToyotaBindingConstants() {
    }
}
