# MyToyota Binding for openHAB

Toyota, Lexus and Subaru cars in Europe through the cloud behind the **MyToyota
app** (Toyota Connected Services Europe). The binding logs in exactly like the
app, with your MyToyota e-mail and password: no developer registration, no API
key of your own.

**What you get:** state of charge, range, charging status, odometer, fuel level
(hybrids), last parked position, trips with route, doors, windows, lights,
warnings in words, climate state, the messages the app shows, and the remote
commands the app offers: lock, climate with all its options, charge now, horn,
lights, trunk, and, per car, whatever else it reports it can do.

> Toyota publishes no API. The binding speaks the protocol of the mobile app,
> as documented by the [pytoyoda](https://github.com/pytoyoda/pytoyoda)
> project. Europe only. When Toyota changes something, this binding needs the
> same fix as pytoyoda; check there first when a channel stops updating, and
> open an issue here.

Requires openHAB 5.x. Built and tested on openHAB 5.1 with a 2025 bZ4X in
Denmark; see [What is verified](#what-is-verified) before relying on a command.

---

## Contents

1. [Installation](#installation)
2. [Things](#things)
3. [Channels](#channels)
4. [How fresh is the data, and what wakes the car](#how-fresh-is-the-data-and-what-wakes-the-car)
5. [Remote commands and capability channels](#remote-commands-and-capability-channels)
6. [Examples](#examples) (things, items, sitemap, rules, trip map)
7. [What is verified](#what-is-verified)
8. [Troubleshooting](#troubleshooting)
9. [Privacy](#privacy)
10. [Changelog, license, contributing](#changelog-license-contributing)

---

## Installation

From the openHAB add-on store (Settings → Add-ons → Bindings → search
"MyToyota"), or copy the JAR from the
[latest release](https://github.com/Prinsessen/openhab-mytoyota-binding/releases/latest)
into openHAB's `addons` folder. No other bundle is needed. When replacing an
older JAR, remove it first.

| release | file | sha256 |
|---|---|---|
| 1.7.1 | `org.openhab.binding.mytoyota-1.7.1.jar` | `f6c8dd35776e5aafa98bd7c3ce515d427a5be96a408ead2af4ba75c450d7cd35` |
| 1.7.0 | `org.openhab.binding.mytoyota-1.7.0.jar` | `76bc51ef7f6a485c9e175e1dc2f129af7012c2b10f9c875ceabaf13ef4738736` |

---

## Things

| thing | type | what |
|---|---|---|
| `mytoyota:account` | bridge | the app login; one per MyToyota account |
| `mytoyota:vehicle` | thing | one car under the account, found by **discovery** (Inbox) once the bridge is ONLINE |

### Account (bridge) parameters

| parameter | required | default | meaning |
|---|---|---|---|
| `username` | yes | | the e-mail of the MyToyota app login |
| `password` | yes | | its password |
| `brand` | no | `T` | `T` Toyota, `L` Lexus, `S` Subaru |
| `pollInterval` | no | 5 | minutes between reads of every vehicle. Reads fetch what the cloud holds; they do **not** wake the car |
| `wakeWhileCharging` | no | 10 | while the car reports that it is charging, ask it for a fresh state of charge before a poll, at most every this many minutes. `0` never |

### Vehicle parameters

| parameter | required | meaning |
|---|---|---|
| `vin` | yes | the 17-character VIN, filled in by discovery |

The vehicle thing carries properties from the account's vehicle list: model,
model year, nickname, generation, EV flag and `capabilities`, the flags the car
reports (`doorLockUnlockCapable, climateCapable, …`). The capability channels
below are created from those flags.

---

## Channels

All channels are read-only unless marked *command*. Quantity channels carry
units; give the linked items `unit="…"` metadata (see the items example) so
their state is stored in the unit you expect.

### battery

| channel | type | |
|---|---|---|
| `battery#level` | Number:Dimensionless | state of charge, % |
| `battery#range` | Number:Length | EV range |
| `battery#rangeWithAc` | Number:Length | EV range with A/C on |
| `battery#chargingStatus` | String | `none`, `charging`, `chargeComplete`, … as the car says it |
| `battery#charging` | Switch | ON while the status contains "charging" |
| `battery#remainingChargeTime` | Number:Time | minutes to full while charging, else UNDEF |
| `battery#lastUpdate` | DateTime | when the car reported these values |

### telemetry

| channel | type | |
|---|---|---|
| `telemetry#odometer` | Number:Length | |
| `telemetry#distanceToEmpty` | Number:Length | |
| `telemetry#fuelLevel` | Number:Dimensionless | hybrids and combustion cars; UNDEF on a battery EV |
| `telemetry#lastUpdate` | DateTime | |

### trips

Read when the car parks (its position timestamp moves) and once an hour. The
cloud keeps about 12 months.

| channel | type | |
|---|---|---|
| `trips#latestStart`, `latestEnd` | DateTime | the newest trip |
| `trips#latestDistance` | Number:Length | |
| `trips#latestDuration` | Number:Time | |
| `trips#latestAverageSpeed` | Number:Speed | |
| `trips#latestFuel` | Number:Volume | litres; UNDEF on an EV |
| `trips#latestEvDistance` | Number:Length | driven electrically |
| `trips#latestScore` | Number | Toyota's driving score, 0–100 |
| `trips#latestStartPosition`, `latestEndPosition` | Location | |
| `trips#latestRoute` | String | the route as `lat,lon,flags;…` (five decimals; flags `e` electric, `h` highway, `o` over the limit). For a map; do not persist |
| `trips#latestId` | String | the cloud's trip id |
| `trips#todayDistance` | Number:Length | today's total |
| `trips#monthDistance`, `monthDuration`, `monthFuel` | | this month's totals |
| `trips#count30Days` | Number | trips in the last 30 days |
| `trips#lastUpdate` | DateTime | when the history was read |

### service

| channel | type | |
|---|---|---|
| `service#count` | Number | service records the cloud holds |
| `service#lastDate` | DateTime | newest record |
| `service#lastCategory`, `lastProvider` | String | |
| `service#lastMileage` | Number:Length | odometer at that service |

### location

| channel | type | |
|---|---|---|
| `location#position` | Location | last parked position; updated when the car parks after a drive |
| `location#name` | String | the cloud's label, e.g. "Last Parked" |
| `location#lastUpdate` | DateTime | when the position was acquired |

### status (the app's status page, summarised)

| channel | type | |
|---|---|---|
| `status#locked` | Switch | ON when every door reports locked |
| `status#anyDoorOpen`, `anyWindowOpen`, `trunkOpen`, `hoodOpen` | Contact | |
| `status#hazardLights` | Switch | |
| `status#overall` | String | `ok` or `warning` |
| `status#warnings` | Number | how many items on the app's status page are red (unlocked or open doors, open windows). Not the health warnings |
| `status#lastUpdate` | DateTime | |

### doors, windows, lights (per item)

| channel | type | |
|---|---|---|
| `doors#driverLocked`, `driverOpen` … for `driver`, `passenger`, `rearLeft`, `rearRight`, `rearBack` (trunk) | Switch / Contact | |
| `doors#hoodOpen` | Contact | |
| `doors#rearSeatReminder` | String | `ok`, `notDetected`, or `warning: …` |
| `windows#driver` … `windows#rearRight` | String | `close`, `open`, `unknown` (cars with fixed rear windows report unknown) |
| `lights#hazard`, `tail`, `head` | Switch | |

### health

| channel | type | |
|---|---|---|
| `health#warnings` | String | active warnings in words with severity and start time, e.g. "Tire Pressure Warning System (severity 4) since 21/09 16:45"; "none" when clear |
| `health#warningCodes` | String | Toyota's codes, e.g. `TIRW` |
| `health#severity` | Number | worst active severity, 0 when none |
| `health#lastUpdate` | DateTime | |

### climate

| channel | type | |
|---|---|---|
| `climate#status` | String | `stopped`, `starting`, `running` |

### notifications (what the app shows)

| channel | type | |
|---|---|---|
| `notifications#latest` | String | the newest message, e.g. "Your request could not be completed because a keyfob was detected in your vehicle." |
| `notifications#latestTime` | DateTime | |
| `notifications#latestCategory` | String | `RemoteControl`, `VehicleStatusAlert`, … |
| `notifications#unread` | Number | messages not yet opened in the app (the binding cannot mark them read) |
| `notifications#recent` | String | the five newest, one per line with date and time |

### control (*command*)

Always present:

| channel | type | |
|---|---|---|
| `control#refresh` | Switch | ON wakes the car for a fresh state of charge and door state; polled again 45 s later; returns to OFF |
| `control#lock` | Switch | ON locks, OFF unlocks. Mirrors the car's lock state |
| `control#hazardLights` | Switch | on / off. Mirrors the car |
| `control#horn`, `control#findVehicle` | Switch | ON runs it once |
| `control#climate` | Switch | ON starts the remote climate with the setpoints below, OFF stops it. Mirrors `climate#status` |
| `control#climateTemperature` | Number:Temperature | target for the next start; seeded from the car's saved setting |
| `control#climateDuration` | Number:Time | minutes the climate runs; seeded from the car |
| `control#chargeNow` | Switch | ON starts charging now while plugged in, overriding the car's own schedule |
| `control#lastCommandResult` | String | last command and the cloud's return code, e.g. `door-lock: 000000 (Success)` |
| `control#lastWake`, `lastPoll` | DateTime | bookkeeping |

Created per car from its capabilities (absent otherwise):

| channel | created when the car reports | |
|---|---|---|
| `control#trunkLock` | `trunkLockUnlockCapable` | ON locks the trunk, OFF unlocks; mirrors the trunk's lock state |
| `control#buzzer` | `buzzerCapable` | ON sounds the warning buzzer once |
| `control#engine` | `remoteEngineStartStop` | ON remote engine start, OFF stop (hybrids) |
| `control#headlights` | `lightsCapable` | on / off |
| `control#windowsOpen`, `windowsClose` | `windowsOpenCapable` / `windowsCloseCapable` | one-shot |
| `control#ventilation` | `ventilatorCapable` | one-shot |
| `control#defrostFront`, `defrostRear`, `steeringHeater`, `mirrorHeater` | `frontDefogger`, `rearDefogger`, `steeringHeater`, `mirrorHeater` | climate options, see below |
| `control#seatHeaterDriver`, `seatHeaterPassenger`, `seatHeaterRearLeft`, `seatHeaterRearRight` | `frontDriverSeatHeater` … | climate options |

**Climate options** are the app's "climate schedule": each is a Switch that is
seeded from the car's saved settings, sent with every climate start, and saved
in the car (`saveSettings`), so the app and openHAB show the same.

---

## How fresh is the data, and what wakes the car

The cloud holds what the car last sent. A parked, sleeping car sends nothing,
so values keep their timestamp (the `lastUpdate` channels say how old they
are). Position and trips update when the car parks after a drive. While
charging the car reports by itself now and then.

Two things wake the car's modem: `control#refresh` (on demand) and the
`wakeWhileCharging` setting (automatic while charging, so the state of charge
is current for a charging automation). Every wake draws on the 12 V battery;
the binding never wakes the car on every poll, and you should not automate the
refresh every minute. Remote commands wake the car as well.

---

## Remote commands and capability channels

Every command pytoyoda knows is in the binding, but a car only gets the
channels it reports it can use: on the first poll the binding reads the
account's vehicle list (`extendedCapabilities`) and creates the matching
command channels on the thing. A bZ4X gets trunk lock, buzzer, defrost, seat
and steering wheel heaters; a hybrid also gets engine start and stop; nobody
gets channels for things their car cannot do. The `capabilities` property on
the thing lists the flags.

A command is acknowledged by the cloud (return code `000000` = accepted, shown
on `control#lastCommandResult`) and followed by a confirming poll 45 seconds
later, so the state channels show what the car actually did. A car may refuse:
for example a lock with the key fob inside is accepted by the cloud and
refused by the car, and the reason appears on `notifications#latest`.

Not covered: the car's own charging schedule (`RESERVE_CHARGE`,
`SET_CHARGING_TIME`). The request model is known but has not been tried on a
car.

---

## Examples

All files are in [`examples/`](examples/) with generic item names (`Car_*`).

### Things: `examples/mytoyota.things`

```java
Bridge mytoyota:account:home [ username="me@example.com", password="secret", brand="T", pollInterval=5, wakeWhileCharging=10 ] {
    Thing vehicle car "My car" [ vin="JTMXXXXXXXXXXXXXX" ]
}
```

Or add the bridge in the UI and pick the car from the Inbox; discovery fills
in the VIN and the properties.

### Items: `examples/mytoyota.items`

The essentials (the file has every channel):

```java
Number:Dimensionless Car_SoC        "Battery [%.0f %%]"      { channel="mytoyota:vehicle:home:car:battery#level", unit="%" }
Number:Length        Car_Range      "Range [%.0f km]"        { channel="mytoyota:vehicle:home:car:battery#range", unit="km" }
String               Car_Charging   "Charging [%s]"          { channel="mytoyota:vehicle:home:car:battery#chargingStatus" }
DateTime             Car_Battery_At "Battery reported [%1$td/%1$tm %1$tR]" { channel="mytoyota:vehicle:home:car:battery#lastUpdate" }
Location             Car_Position   "Parked at"              { channel="mytoyota:vehicle:home:car:location#position" }
Switch               Car_Locked     "Locked [%s]"            { channel="mytoyota:vehicle:home:car:status#locked" }
String               Car_Warnings   "Warnings [%s]"          { channel="mytoyota:vehicle:home:car:health#warnings" }
String               Car_Says       "Car says [%s]"          { channel="mytoyota:vehicle:home:car:notifications#latest" }
Switch               Car_Lock       "Doors"                  { channel="mytoyota:vehicle:home:car:control#lock", autoupdate="false" }
Switch               Car_Climate    "Climate"                { channel="mytoyota:vehicle:home:car:control#climate", autoupdate="false" }
Switch               Car_Refresh    "Refresh from car"       { channel="mytoyota:vehicle:home:car:control#refresh", autoupdate="false" }
String               Car_LastCmd    "Last command [%s]"      { channel="mytoyota:vehicle:home:car:control#lastCommandResult" }
```

`unit="%"` and `unit="km"` keep the item states in the units you expect;
without them openHAB stores `0.18` and `85500 m`. `autoupdate="false"` on the
command switches makes them show what the car reports, not what was clicked.

### Sitemap: `examples/mytoyota.sitemap`

A page with the daily numbers, one-row commands that also show the state
("Doors [OFF] · Lock / Unlock"), the climate options as On/Off buttons, and the
trip map as a webview.

### Rules: `examples/rules/`

| file | what it shows |
|---|---|
| `preheat-before-departure.js` | start the climate 20 minutes before a weekday departure time, only when the car is parked at home |
| `plug-in-reminder.js` | evening mail when the car is parked at home below 40 % and not charging |
| `charge-to-target.js` | the pattern for a charger automation: stop when the state of charge reaches a target, with a freshness check on `battery#lastUpdate` |
| `night-lock.js` | lock at 23:00 if unlocked; report if the car refused (key fob inside) using `notifications#latest` |

### Trip map: `examples/trip-map.html`

A Leaflet page that reads `trips#latestRoute` through openHAB's REST API and
draws the newest trip with electric, highway and over-the-limit segments in
different colours. Copy it to openHAB's `html/` folder and embed it with
`Webview url="/static/trip-map.html" height=18`.

---

## What is verified

| | Toyota bZ4X 2025 (Denmark) |
|---|---|
| login, discovery, all reads (battery, telemetry, location, status, doors/windows/lights, health, climate, notifications) | verified |
| capability channels created from the car's flags | verified (trunk, buzzer, defrost, seat and steering heaters appeared; engine, headlights, windows did not) |
| wake (`control#refresh`, `wakeWhileCharging`) | verified: fresh state of charge within 45 s, door state within 15 s |
| `control#lock` | verified, including the car refusing it with the key fob inside, reported on `notifications#latest` |
| trips, service history | code from pytoyoda's model; the test car had no trips yet when released |
| `control#trunkLock` (unlock) | verified |
| unlock, hazard, horn, find, climate start/stop and options, charge now, buzzer | same request shape as the app; not yet exercised on a car |
| Lexus, Subaru | same service, different realm; untested |

Please report what works on your model, with the `capabilities` property.

---

## Troubleshooting

| symptom | cause and fix |
|---|---|
| bridge OFFLINE "Login failed: user not found" / "authenticate returned 401" | wrong e-mail or password. The bridge retries every 10 minutes |
| bridge OFFLINE "429" or "5xx" | Toyota's gateway rate-limits; the binding retries with 2, 4, 8 s pauses and reports the last answer. Wait |
| vehicle OFFLINE "VIN must have 17 characters" | check the VIN, or use discovery |
| values never change | the car is asleep; look at `*#lastUpdate`. Use `control#refresh` once |
| `location#position` never changes, `location#lastUpdate` stays old, and the app says "Vehicle location is currently unavailable because you have privacy preferences turned on" | the car's privacy setting stops position upload; the cloud keeps the last point it got. Turn location sharing on in the car (multimedia screen → Settings → Privacy), then `control#refresh`. Dealers often leave it on. Trips depend on the same data |
| `status#warnings` is 5 but `health#warnings` says "none" | different things: 5 is the app's red marks (unlocked doors), health is tyre pressure etc. |
| a command returns `000000` but nothing happens | the car refused; `notifications#latest` says why (key fob inside, doors open, battery too low for climate) |
| `trips#*` stay UNDEF | no trip in the cloud yet, the car has not parked since the last read, or privacy is on in the car (see above) |
| after a Toyota app update something stops | compare with pytoyoda and open an issue with the log at DEBUG for `org.openhab.binding.mytoyota` |

Logs: `log:set DEBUG org.openhab.binding.mytoyota` in the console. Remote
commands and notifications are logged at INFO.

---

## Privacy

The VIN is shown only in the thing configuration; logs and notification texts
show it shortened or replaced by "the car". Credentials stay in the bridge
configuration. Trip routes are kept in a String channel and not persisted
unless you choose to.

---

## Changelog, license, contributing

See [CHANGELOG.md](CHANGELOG.md). EPL-2.0. Issues and pull requests on
[GitHub](https://github.com/Prinsessen/openhab-mytoyota-binding); community
thread on the
[openHAB forum](https://community.openhab.org/t/mytoyota-binding-toyota-lexus-and-subaru-europe/170453).
Not affiliated with Toyota Motor Corporation.
