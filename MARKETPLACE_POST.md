| Key | Value |
|----|----|
| uid | mytoyota |
| type | binding |
| author | Nanna Agesen |
| version range | \[5.0.0;6.0.0) |
| download | [org.openhab.binding.mytoyota-1.7.0.jar](https://github.com/Prinsessen/openhab-mytoyota-binding/releases/download/v1.7.0/org.openhab.binding.mytoyota-1.7.0.jar) |

# MyToyota Binding — Toyota, Lexus and Subaru (Europe)

Bring your car into openHAB through the cloud behind the **MyToyota app**: state of charge, range, charging status,
odometer, fuel level, where it is parked, trips with the route, every door, window and light, warnings in words,
the messages the app shows, and the remote commands the app offers — lock, climate with all its options, charge now,
horn, lights, trunk, and per car whatever else it reports it can do.
**No developer registration, no API key of your own: the binding logs in exactly like the app**, with your MyToyota
e-mail and password.

> **Note:** Toyota publishes no API. The binding speaks the protocol of the mobile app, as documented by the
> [pytoyoda](https://github.com/pytoyoda/pytoyoda) project. Europe only (Toyota Connected Services Europe); Lexus
> and Subaru (Alliance) accounts use the same service. When Toyota changes something, the binding needs the same
> fix as pytoyoda — please open an issue.

---

## Supported Things

| Thing Type | Description |
|----|-----|
| `account` (Bridge) | Your MyToyota login; one per account |
| `vehicle` | One car under the account — found by **discovery** (Inbox) after the bridge is ONLINE |

| Bridge parameter | Default | Meaning |
|----|----|----|
| `brand` | `T` | `T` Toyota, `L` Lexus, `S` Subaru |
| `pollInterval` | 5 | Minutes between reads. Reads fetch what the cloud holds; they do **not** wake the car |
| `wakeWhileCharging` | 10 | While charging, ask the car for a fresh state of charge at most every N minutes (0 = never) |

---

## Channels

### Battery, telemetry, location (read-only)

| Channel | Type | Description |
|----|----|----|
| `battery#level` | Number:Dimensionless | State of charge (%) as last reported by the car |
| `battery#range` / `battery#rangeWithAc` | Number:Length | EV range, without and with A/C |
| `battery#chargingStatus` | String | `none`, `charging`, `chargeComplete`, … |
| `battery#charging` | Switch | ON while the status says charging |
| `battery#remainingChargeTime` | Number:Time | Minutes to full while charging |
| `battery#lastUpdate` | DateTime | When the car reported these values |
| `telemetry#odometer` / `telemetry#distanceToEmpty` | Number:Length | Odometer, distance to empty |
| `telemetry#fuelLevel` | Number:Dimensionless | Fuel (%) — hybrids and combustion cars |
| `location#position` | Location | Last parked position (updated when the car is parked) |
| `location#name` / `location#lastUpdate` | String / DateTime | The cloud's label ("Last Parked") and when it was acquired |

### Status, doors, windows, lights (read-only) — the app's status page

| Channel | Type | Description |
|----|----|----|
| `status#locked` | Switch | ON when every door reports locked |
| `status#anyDoorOpen` / `anyWindowOpen` / `trunkOpen` / `hoodOpen` | Contact | Summaries |
| `status#overall` / `status#warnings` | String / Number | `ok` or a warning state; the number of red marks in the app |
| `doors#driverLocked`, `driverOpen`, `passenger…`, `rearLeft…`, `rearRight…`, `rearBack…` | Switch / Contact | Lock and open state of every door and the trunk |
| `doors#hoodOpen`, `doors#rearSeatReminder` | Contact / String | Hood, rear seat reminder |
| `windows#driver`, `passenger`, `rearLeft`, `rearRight` | String | `closed`, `open`, … per window |
| `lights#hazard`, `tail`, `head` | Switch | Lights left on |
| `health#warnings` | String | The warnings behind the count, in words ("Tire Pressure Warning System"), or `none` |
| `health#warningCodes` / `health#severity` | String / Number | Codes and the worst severity |
| `climate#status` | String | `stopped`, `starting`, `running` |

### Trips and service history (read-only)

| Channel | Type | Description |
|----|----|----|
| `trips#latestStart` / `latestEnd` | DateTime | The newest trip |
| `trips#latestDistance`, `latestDuration`, `latestAverageSpeed`, `latestFuel`, `latestEvDistance`, `latestScore` | Number | Its numbers |
| `trips#latestStartPosition` / `latestEndPosition` | Location | Where it began and ended |
| `trips#latestRoute` | String | The route as `lat,lon,flags;…` (e = electric, h = highway, o = over the limit) — see the map page in `examples/` |
| `trips#todayDistance`, `monthDistance`, `monthDuration`, `monthFuel`, `count30Days` | Number | Totals |
| `service#count`, `lastDate`, `lastCategory`, `lastProvider`, `lastMileage` | | The service history |

### Notifications (read-only) — what the app shows

| Channel | Type | Description |
|----|----|----|
| `notifications#latest` | String | The newest message, e.g. "Your request could not be completed because a keyfob was detected in your vehicle." |
| `notifications#latestTime` / `latestCategory` | DateTime / String | When, and `RemoteControl`, `VehicleStatusAlert`, … |
| `notifications#unread` | Number | Messages not yet opened in the app |
| `notifications#recent` | String | The five newest, one per line |

### Control (read/write)

| Channel | Type | Description |
|----|----|----|
| `control#refresh` | Switch | ON: wake the car for a fresh state of charge and door state; polled again 45 s later |
| `control#lock` | Switch | ON locks, OFF unlocks — mirrors `status#locked` |
| `control#climate` | Switch | ON starts the remote climate with the setpoints and options below, OFF stops it |
| `control#climateTemperature` / `climateDuration` | Number | Target and minutes for the next climate start (seeded from the car's saved setting) |
| `control#chargeNow` | Switch | ON: start charging now while plugged in |
| `control#hazardLights` / `horn` / `findVehicle` | Switch | Hazard lights, horn once, flash the lights |
| `control#lastCommandResult` | String | Last command and the cloud's return code, e.g. `door-lock: 000000 (Success)` |

**Created only when the car reports the capability** (thing property `capabilities`): `control#trunkLock`, `buzzer`,
`engine`, `headlights`, `windowsOpen`, `windowsClose`, `ventilation`, and the climate options `defrostFront`,
`defrostRear`, `steeringHeater`, `mirrorHeater`, `seatHeaterDriver`, `seatHeaterPassenger`, `seatHeaterRearLeft`,
`seatHeaterRearRight`. The options are switches you set once; they go with every climate start and are saved in the car,
like the app's climate schedule settings.

Every command is acknowledged by the cloud (return code `000000` = accepted) and followed by a confirming poll 45 s
later, so the state channels show what the car actually did. A car may refuse: a lock with the key fob inside is
accepted by the cloud and refused by the car, and the reason appears on `notifications#latest`.

---

## Quick Start

```
Bridge mytoyota:account:home [ username="me@example.com", password="secret", brand="T", pollInterval=5, wakeWhileCharging=10 ] {
    Thing vehicle car "My car" [ vin="JTMXXXXXXXXXXXXXX" ]
}
```

```
Number:Dimensionless Car_SoC       "Battery [%.0f %%]"     { channel="mytoyota:vehicle:home:car:battery#level", unit="%" }
Number:Length        Car_Range     "Range [%.0f km]"       { channel="mytoyota:vehicle:home:car:battery#range", unit="km" }
String               Car_Charging  "Charging [%s]"         { channel="mytoyota:vehicle:home:car:battery#chargingStatus" }
Location             Car_Position  "Position"              { channel="mytoyota:vehicle:home:car:location#position" }
Switch               Car_Locked    "Locked [%s]"           { channel="mytoyota:vehicle:home:car:status#locked" }
String               Car_Warnings  "Warnings [%s]"         { channel="mytoyota:vehicle:home:car:health#warnings" }
String               Car_Says      "Car says [%s]"         { channel="mytoyota:vehicle:home:car:notifications#latest" }
Switch               Car_Lock      "Doors"                 { channel="mytoyota:vehicle:home:car:control#lock", autoupdate="false" }
Switch               Car_Climate   "Climate"               { channel="mytoyota:vehicle:home:car:control#climate", autoupdate="false" }
Switch               Car_Refresh   "Refresh from car"      { channel="mytoyota:vehicle:home:car:control#refresh", autoupdate="false" }
String               Car_LastCmd   "Last command [%s]"     { channel="mytoyota:vehicle:home:car:control#lastCommandResult" }
```

`unit="%"` and `unit="km"` keep the item states in the units you expect; without them openHAB stores `0.18` and
`85500 m`. `autoupdate="false"` on the command switches makes them show what the car reports, not what was clicked.

**Complete examples** in the repository's [`examples/`](https://github.com/Prinsessen/openhab-mytoyota-binding/tree/main/examples)
folder: a things file, an items file with **every** channel, a sitemap page, four rules (preheat before departure,
plug-in reminder, charge to a target with a freshness check, night lock with the car's refusal reason), and a Leaflet
page that draws the newest trip from `trips#latestRoute` as a webview.

---

## How fresh is the data?

The cloud holds what the car last sent. Parked and asleep, the car sends nothing, so values keep their timestamp
(see the `lastUpdate` channels). Position and trips arrive when the car is parked after a drive. While charging the
car reports by itself now and then; `wakeWhileCharging` asks it for a fresh state of charge more often, and
`control#refresh` does the same on demand. Every wake reaches the car's modem over the mobile network and draws on
its 12 V battery — that is why the binding never wakes the car on every poll, and why you should not automate the
refresh every minute.

---

## Tested on

- **Toyota bZ4X 2025** (Denmark): login, discovery, all reads (battery, telemetry, location, status, per-door /
  window / light, health, climate, notifications), the capability channels the car reports (trunk, buzzer, defrost,
  seat and steering heaters appeared; engine, headlights and windows did not), the wake, and `lock` — including
  the car refusing it with the key fob inside, reported on `notifications#latest`.
- Trips and service history follow pytoyoda's model; the test car had no trips in the cloud when this was released.
- Unlock, hazard, horn, find, climate start/stop and options, charge now, trunk and buzzer use the same request
  shape as the app and are not yet exercised on a car.

**If you own a different Toyota, Lexus or Subaru, please post what works** — model, year and the `capabilities`
thing property are all that is needed to extend the binding.

---

## Resources

* **Download JAR:** [org.openhab.binding.mytoyota-1.7.0.jar](https://github.com/Prinsessen/openhab-mytoyota-binding/releases/download/v1.7.0/org.openhab.binding.mytoyota-1.7.0.jar)
* **Source Code:** [github.com/Prinsessen/openhab-mytoyota-binding](https://github.com/Prinsessen/openhab-mytoyota-binding)
* **Full Documentation:** [README.md](https://github.com/Prinsessen/openhab-mytoyota-binding/blob/main/README.md)
* **Examples:** [examples/](https://github.com/Prinsessen/openhab-mytoyota-binding/tree/main/examples)
* **Changelog:** [CHANGELOG.md](https://github.com/Prinsessen/openhab-mytoyota-binding/blob/main/CHANGELOG.md)
* **Release:** [v1.7.0 — MyToyota Binding 1.7.0](https://github.com/Prinsessen/openhab-mytoyota-binding/releases/tag/v1.7.0)
* **License:** EPL-2.0

---

*Tested on openHAB 5.1. Not affiliated with Toyota Motor Corporation; "Toyota", "Lexus", "Subaru" and "MyToyota" are trademarks of their owners.*
