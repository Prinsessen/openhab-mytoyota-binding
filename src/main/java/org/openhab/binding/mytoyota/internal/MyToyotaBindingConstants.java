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
    // plug-in hybrids: the part of the battery usable for EV driving, and fuel + EV range together
    public static final String CHANNEL_BATTERY_USABLE_LEVEL = "battery#usableLevel";
    public static final String CHANNEL_BATTERY_TOTAL_RANGE = "battery#totalRange";

    // telemetry
    public static final String CHANNEL_TELEMETRY_ODOMETER = "telemetry#odometer";
    public static final String CHANNEL_TELEMETRY_DTE = "telemetry#distanceToEmpty";
    public static final String CHANNEL_TELEMETRY_TIMESTAMP = "telemetry#lastUpdate";

    public static final String CHANNEL_TELEMETRY_FUEL = "telemetry#fuelLevel";

    // trips (from the cloud's trip history; the car reports a trip when it parks)
    public static final String CHANNEL_TRIPS_LATEST_START = "trips#latestStart";
    public static final String CHANNEL_TRIPS_LATEST_END = "trips#latestEnd";
    public static final String CHANNEL_TRIPS_LATEST_DISTANCE = "trips#latestDistance";
    public static final String CHANNEL_TRIPS_LATEST_DURATION = "trips#latestDuration";
    public static final String CHANNEL_TRIPS_LATEST_SPEED = "trips#latestAverageSpeed";
    public static final String CHANNEL_TRIPS_LATEST_FUEL = "trips#latestFuel";
    public static final String CHANNEL_TRIPS_LATEST_EV_DISTANCE = "trips#latestEvDistance";
    public static final String CHANNEL_TRIPS_LATEST_SCORE = "trips#latestScore";
    public static final String CHANNEL_TRIPS_TODAY_DISTANCE = "trips#todayDistance";
    public static final String CHANNEL_TRIPS_MONTH_DISTANCE = "trips#monthDistance";
    public static final String CHANNEL_TRIPS_MONTH_DURATION = "trips#monthDuration";
    public static final String CHANNEL_TRIPS_MONTH_FUEL = "trips#monthFuel";
    public static final String CHANNEL_TRIPS_COUNT_30D = "trips#count30Days";
    public static final String CHANNEL_TRIPS_TIMESTAMP = "trips#lastUpdate";
    public static final String CHANNEL_TRIPS_LATEST_START_POS = "trips#latestStartPosition";
    public static final String CHANNEL_TRIPS_LATEST_END_POS = "trips#latestEndPosition";
    public static final String CHANNEL_TRIPS_LATEST_ROUTE = "trips#latestRoute";
    public static final String CHANNEL_TRIPS_LATEST_ID = "trips#latestId";
    // what pytoyoda derives from the same payload and this did not (1.8.0): consumption, the hybrid
    // drive-mode split and the four parts of the driving score
    public static final String CHANNEL_TRIPS_LATEST_FUEL_ECONOMY = "trips#latestFuelEconomy";
    public static final String CHANNEL_TRIPS_LATEST_EV_DURATION = "trips#latestEvDuration";
    public static final String CHANNEL_TRIPS_LATEST_SCORE_ACCELERATION = "trips#latestScoreAcceleration";
    public static final String CHANNEL_TRIPS_LATEST_SCORE_BRAKING = "trips#latestScoreBraking";
    public static final String CHANNEL_TRIPS_LATEST_SCORE_ADVICE = "trips#latestScoreAdvice";
    public static final String CHANNEL_TRIPS_LATEST_SCORE_CONSTANT_SPEED = "trips#latestScoreConstantSpeed";
    public static final String CHANNEL_TRIPS_TODAY_FUEL = "trips#todayFuel";
    public static final String CHANNEL_TRIPS_MONTH_FUEL_ECONOMY = "trips#monthFuelEconomy";
    public static final String CHANNEL_TRIPS_MONTH_EV_DISTANCE = "trips#monthEvDistance";
    // the recent trips as a list, and one of them picked out with its route (1.9.0)
    public static final String CHANNEL_TRIPS_RECENT = "trips#recent";
    public static final String CHANNEL_TRIPS_SELECT = "trips#select";
    public static final String CHANNEL_TRIPS_SELECTED_START = "trips#selectedStart";
    public static final String CHANNEL_TRIPS_SELECTED_END = "trips#selectedEnd";
    public static final String CHANNEL_TRIPS_SELECTED_DISTANCE = "trips#selectedDistance";
    public static final String CHANNEL_TRIPS_SELECTED_DURATION = "trips#selectedDuration";
    public static final String CHANNEL_TRIPS_SELECTED_SPEED = "trips#selectedAverageSpeed";
    public static final String CHANNEL_TRIPS_SELECTED_FUEL = "trips#selectedFuel";
    public static final String CHANNEL_TRIPS_SELECTED_FUEL_ECONOMY = "trips#selectedFuelEconomy";
    public static final String CHANNEL_TRIPS_SELECTED_EV_DISTANCE = "trips#selectedEvDistance";
    public static final String CHANNEL_TRIPS_SELECTED_SCORE = "trips#selectedScore";
    public static final String CHANNEL_TRIPS_SELECTED_START_POS = "trips#selectedStartPosition";
    public static final String CHANNEL_TRIPS_SELECTED_END_POS = "trips#selectedEndPosition";
    public static final String CHANNEL_TRIPS_SELECTED_ROUTE = "trips#selectedRoute";
    public static final String CHANNEL_TRIPS_SELECTED_ID = "trips#selectedId";

    // service history
    public static final String CHANNEL_SERVICE_COUNT = "service#count";
    public static final String CHANNEL_SERVICE_LAST_DATE = "service#lastDate";
    public static final String CHANNEL_SERVICE_LAST_CATEGORY = "service#lastCategory";
    public static final String CHANNEL_SERVICE_LAST_PROVIDER = "service#lastProvider";
    public static final String CHANNEL_SERVICE_LAST_MILEAGE = "service#lastMileage";
    public static final String CHANNEL_SERVICE_LAST_NOTES = "service#lastNotes";
    public static final String CHANNEL_SERVICE_LAST_OPERATIONS = "service#lastOperations";
    public static final String CHANNEL_SERVICE_LAST_DEALER = "service#lastDealer";

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

    // per-door, per-window, per-light detail (what the app's status page shows)
    public static final String[] DOORS = { "driver", "passenger", "rearLeft", "rearRight", "rearBack" };
    public static final String[] WINDOWS = { "driver", "passenger", "rearLeft", "rearRight" };
    public static final String[] LIGHTS = { "hazard", "tail", "head" };
    public static final String GROUP_DOORS = "doors#";
    public static final String GROUP_WINDOWS = "windows#";
    public static final String GROUP_LIGHTS = "lights#";
    public static final String CHANNEL_DOORS_HOOD = "doors#hoodOpen";
    public static final String CHANNEL_DOORS_REAR_SEAT = "doors#rearSeatReminder";

    // health: the warnings behind status#warnings, in words
    public static final String CHANNEL_HEALTH_WARNINGS = "health#warnings";
    public static final String CHANNEL_HEALTH_WARNING_CODES = "health#warningCodes";
    public static final String CHANNEL_HEALTH_SEVERITY = "health#severity";
    public static final String CHANNEL_HEALTH_TIMESTAMP = "health#lastUpdate";

    // climate
    public static final String CHANNEL_CLIMATE_STATUS = "climate#status";
    public static final String CHANNEL_LOCATION_REPORTED = "location#reportedAt";
    public static final String CHANNEL_BATTERY_SCHEDULE = "battery#chargingSchedule";
    public static final String CHANNEL_BATTERY_NEXT_EVENT = "battery#nextChargingEvent";
    public static final String CHANNEL_CLIMATE_STARTED = "climate#startedAt";
    public static final String CHANNEL_CLIMATE_REMAINING = "climate#remaining";
    public static final String CHANNEL_CLIMATE_CABIN_TEMP = "climate#cabinTemperature";
    public static final String CHANNEL_CLIMATE_TARGET_TEMP = "climate#targetTemperature";

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

    // optional command channels, created per vehicle from its extendedCapabilities (see
    // MyToyotaVehicleHandler.provisionOptionalChannels): id, channel type, item type, capability keys (any true)
    public static final String[][] OPTIONAL_CHANNELS = {
            { "control#trunkLock", "trunk-lock", "Switch", "trunkLockUnlockCapable", "trunkCapable" },
            { "control#buzzer", "trigger-command", "Switch", "buzzerCapable" },
            { "control#engine", "engine", "Switch", "remoteEngineStartStop", "estartStopCapable", "estartEnabled" },
            { "control#headlights", "headlights", "Switch", "lightsCapable", "headLightCapable" },
            { "control#windowsOpen", "trigger-command", "Switch", "windowsOpenCapable", "powerWindowCapable" },
            { "control#windowsClose", "trigger-command", "Switch", "windowsCloseCapable", "powerWindowCapable" },
            { "control#ventilation", "trigger-command", "Switch", "ventilatorCapable" },
            { "control#defrostFront", "climate-option", "Switch", "frontDefogger" },
            { "control#defrostRear", "climate-option", "Switch", "rearDefogger" },
            { "control#steeringHeater", "climate-option", "Switch", "steeringHeater", "steeringWheelHeaterCapable" },
            { "control#mirrorHeater", "climate-option", "Switch", "mirrorHeater" },
            { "control#seatHeaterDriver", "climate-option", "Switch", "frontDriverSeatHeater" },
            { "control#seatHeaterPassenger", "climate-option", "Switch", "frontPassengerSeatHeater" },
            { "control#seatHeaterRearLeft", "climate-option", "Switch", "rearDriverSeatHeater" },
            { "control#seatHeaterRearRight", "climate-option", "Switch", "rearPassengerSeatHeater" } };
    public static final String CHANNEL_CONTROL_TRUNK_LOCK = "control#trunkLock";
    public static final String CHANNEL_CONTROL_BUZZER = "control#buzzer";
    public static final String CHANNEL_CONTROL_ENGINE = "control#engine";
    public static final String CHANNEL_CONTROL_HEADLIGHTS = "control#headlights";
    public static final String CHANNEL_CONTROL_WINDOWS_OPEN = "control#windowsOpen";
    public static final String CHANNEL_CONTROL_WINDOWS_CLOSE = "control#windowsClose";
    public static final String CHANNEL_CONTROL_VENTILATION = "control#ventilation";
    public static final String CHANNEL_CONTROL_DEFROST_FRONT = "control#defrostFront";
    public static final String CHANNEL_CONTROL_DEFROST_REAR = "control#defrostRear";
    public static final String CHANNEL_CONTROL_STEERING_HEATER = "control#steeringHeater";
    public static final String CHANNEL_CONTROL_MIRROR_HEATER = "control#mirrorHeater";
    public static final String CHANNEL_CONTROL_SEAT_DRIVER = "control#seatHeaterDriver";
    public static final String CHANNEL_CONTROL_SEAT_PASSENGER = "control#seatHeaterPassenger";
    public static final String CHANNEL_CONTROL_SEAT_REAR_LEFT = "control#seatHeaterRearLeft";
    public static final String CHANNEL_CONTROL_SEAT_REAR_RIGHT = "control#seatHeaterRearRight";

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
    public static final String PROPERTY_FUEL_TYPE = "fuelType";
    public static final String PROPERTY_VEHICLE_TYPE = "vehicleType";
    public static final String PROPERTY_CAPABILITIES = "capabilities";

    private MyToyotaBindingConstants() {
    }
}
