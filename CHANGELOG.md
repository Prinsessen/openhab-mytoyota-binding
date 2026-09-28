# Changelog

## 1.8.2 — 2026-09-28

A car that nothing answers for stays OFFLINE. 1.8.0 and 1.8.1 put every failed
read on an hourly retry after three failures, and a poll in which every read
was waiting out that retry attempted nothing - and then fell through to ONLINE.
A car the cloud had dropped therefore showed ONLINE for 55 minutes of every
hour. Found by walking through what 1.8.1 would do if 2026-09-26 happened
again, before it did. No other change.

## 1.8.1 — 2026-09-28

A car that is not in the account's vehicle list is looked for again every
hour instead of once. On 2026-09-26 the MyToyota cloud dropped the test car
("your car was successfully removed from the app") and the owner had to add
the VIN again; with 1.8.0 a car that came back that way would have been polled
without its properties and capability channels until openHAB restarted. No
other change.

## 1.8.0 — 2026-09-28

For the cars that burn fuel, after the first forum report (a RAV4 plug-in
hybrid with "no capabilities for the petrol side" and a thing showing only
`vendor: Toyota`), and a pass through pytoyoda 5.2.9 for what it reads that
this did not.

- **A fuel-only or full-hybrid car no longer goes OFFLINE.** The electric
  status was read for every car and its failure ended the whole poll. Now every
  read stands on its own, the battery is asked only of cars the vehicle list
  calls electric (pytoyoda's rule: `evVehicle`, `fuelType` E or I, or
  `econnectVehicleStatusCapable`), a read that fails three times in a row is
  retried hourly and logged once, and the thing goes OFFLINE only when every
  read of a poll failed.
- **Properties for every thing**, not just discovered ones: VIN, model, year,
  nickname, generation, `evVehicle`, and new `fuelType` and `vehicleType`
  (electric / plug-in hybrid / full hybrid / fuel-only), and `capabilities` now
  covering both `extendedCapabilities` and `remoteServiceCapabilities`.
- **Capability channels see both spellings** - `estartStopCapable`,
  `headLightCapable`, `powerWindowCapable`, `trunkCapable`,
  `steeringWheelHeaterCapable` count alongside the `extendedCapabilities`
  names, so a hybrid's engine start gets its channel.
- **Trips:** `latestFuelEconomy` and `monthFuelEconomy` (l/100 km),
  `todayFuel`, `latestEvDuration`, `monthEvDistance`, and the four parts of
  the driving score (`latestScoreAcceleration`, `latestScoreBraking`,
  `latestScoreConstantSpeed`, `latestScoreAdvice`).
- **Service:** `lastNotes`, `lastOperations`, `lastDealer`.
- **Plug-in hybrids:** `battery#usableLevel` (`phevUsableBatteryLevel`,
  pytoyoda 5.2.9) and `battery#totalRange` (fuel range + EV range with A/C).
- **Electric status route:** Toyota fenced `/v1/global/remote/electric/status`
  behind SigV4 in September 2026; a 403 switches the handler to
  `/v1/vehicle/electric/status` (pytoyoda 5.2.8) and logs it. The old route
  still answers here, so it stays first.
- From 2026-09-25, unreleased until now: the whole climate-status payload
  (`climate#startedAt`, `remaining`, `cabinTemperature`, `targetTemperature`),
  the car's own charging schedule and next charging event
  (`battery#chargingSchedule`, `nextChargingEvent`), the location record's
  refresh time (`location#reportedAt`), and a filter for the 65535 sentinel in
  `remainingChargeTime` while not charging.
- Housekeeping: a raw NUL byte sat in `MyToyotaVehicleHandler.java` (a `"\0"`
  sentinel); it compiled, but `file` called the source data and grep skipped
  the file. Written as `"\u0000"` now.

## 1.7.1 — 2026-09-22

Command channels that mirror the car (lock, trunk, hazard, climate, engine,
headlights) show the commanded state as soon as the cloud accepts the request,
instead of flicking back to the old state for the 45 s until the confirming
poll. If the car refuses, that poll puts the real state back. Trunk unlock
verified on the bZ4X.

## 1.7.0 — 2026-09-22

Everything pytoyoda reads, and the whole app status page, in one release
(1.3 to 1.7 were built and tested on one car in a week and are published
together):

- **Trips** (`trips` group): the newest trip with start, end, distance,
  duration, average speed, fuel, electric distance, score, start and end
  positions (Location), the full route as a compact point list with electric /
  highway / overspeed flags for a map, and the trip id; today's and this
  month's totals; trips in 30 days. Read hourly and after every parking.
- **Service history** (`service` group): count, date, category, provider and
  odometer of the last service. Read every six hours.
- **Fuel level** (`telemetry#fuelLevel`) for hybrids and combustion cars.
- **Doors, windows, lights** (groups `doors`, `windows`, `lights`): lock and
  open state per door and the trunk, hood, per window, hazard / tail / head
  lights, and the rear seat reminder: the same detail as the app's status page.
- **Health** (`health` group): the warnings behind the app's warning count, in
  words, with codes, worst severity and timestamp.
- **Capability channels**: trunk lock, buzzer, engine start/stop, headlights,
  windows open/close, ventilation and the climate options (front and rear
  defrost, steering wheel, mirror and seat heaters) are created per car from
  the capabilities it reports; a car without a feature never shows the channel.
  The climate options are sent with the climate start and saved in the car.
- Climate status is re-read after every climate command; trunk lock mirrors
  the trunk's lock state; one-shot commands rest at OFF.
- Documentation: full README with every channel, `examples/` with things,
  items (every channel), sitemap, four rules and the trip map page.

## 1.2.0 — 2026-09-21

Notifications: the messages the app shows, as channels (latest, time, category,
unread count, five newest). Includes 1.1.1's command-state mirroring.

## 1.1.1 — 2026-09-21 (folded into 1.2.0)

Command channels mirror the car: `control#lock` follows the door lock state,
`control#hazardLights` the hazard lights, `control#climate` the climate status; the
one-shot channels (refresh, horn, find, charge now) rest at OFF instead of NULL.
`control#lastWake` is also set by remote commands.

## 1.1.0 — 2026-09-21

Remote commands: lock/unlock, hazard lights, horn, find vehicle, climate
start/stop with temperature and duration setpoints, charge now, and a
last-command-result channel. Every command is followed by a confirming poll.

## 1.0.0 — 2026-09-21 (never published on its own; included in 1.1.0)

First version. Account bridge with the MyToyota app login, vehicle discovery,
read channels for battery, range, charging, telemetry, last parked position,
doors, windows, lights and climate state, and a refresh command that wakes the
car for a fresh state of charge. Tested on a 2025 bZ4X in Denmark.
