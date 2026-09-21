| Key | Value |
|----|----|
| uid | mytoyota |
| type | binding |
| author | Nanna Agesen |
| version range | \[5.0.0;6.0.0) |
| download | [org.openhab.binding.mytoyota-1.2.0.jar](https://github.com/Prinsessen/openhab-mytoyota-binding/releases/download/v1.2.0/org.openhab.binding.mytoyota-1.2.0.jar) |

# MyToyota Binding — Toyota, Lexus and Subaru (Europe)

Bring your car into openHAB through the cloud behind the **MyToyota app**: state of charge, range, charging status,
odometer, where it is parked, whether it is locked, and the remote commands the app offers — lock, climate, charge now.
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

---

## Channels

### Vehicle — Battery (read-only)

| Channel | Type | Description |
|----|----|----|
| `battery#level` | Number:Dimensionless | State of charge (%) as last reported by the car |
| `battery#range` | Number:Length | EV range |
| `battery#rangeWithAc` | Number:Length | EV range with A/C on |
| `battery#chargingStatus` | String | `none`, `charging`, `chargeComplete`, … |
| `battery#charging` | Switch | ON while the status says charging |
| `battery#remainingChargeTime` | Number:Time | Minutes to full while charging |
| `battery#lastUpdate` | DateTime | When the car reported these values |

### Vehicle — Telemetry, Location (read-only)

| Channel | Type | Description |
|----|----|----|
| `telemetry#odometer` | Number:Length | Odometer |
| `telemetry#distanceToEmpty` | Number:Length | Distance to empty |
| `telemetry#lastUpdate` | DateTime | |
| `location#position` | Location | Last parked position (updated when the car is parked) |
| `location#name` | String | The cloud's label, e.g. "Last Parked" |
| `location#lastUpdate` | DateTime | When the position was acquired |

### Vehicle — Doors, Windows, Lights, Climate (read-only)

| Channel | Type | Description |
|----|----|----|
| `status#locked` | Switch | ON when every door reports locked |
| `status#anyDoorOpen` | Contact | OPEN when any door is open |
| `status#anyWindowOpen` | Contact | |
| `status#trunkOpen` | Contact | |
| `status#hoodOpen` | Contact | |
| `status#hazardLights` | Switch | |
| `status#overall` | String | `ok` or a warning state |
| `status#warnings` | Number | Warning count |
| `status#lastUpdate` | DateTime | |
| `climate#status` | String | `stopped`, `starting`, `running` |

### Vehicle — Notifications (read-only) — what the app shows

| Channel | Type | Description |
|----|----|----|
| `notifications#latest` | String | The newest message, e.g. "Your request could not be completed because a keyfob was detected in your vehicle." |
| `notifications#latestTime` | DateTime | When it was issued |
| `notifications#latestCategory` | String | `RemoteControl`, `VehicleStatusAlert`, … |
| `notifications#unread` | Number | Messages not yet opened in the app |
| `notifications#recent` | String | The five newest, one per line with date and time |

### Vehicle — Control (read/write)

| Channel | Type | Description |
|----|----|----|
| `control#refresh` | Switch | ON: wake the car for a fresh state of charge and door state; polled again 45 s later |
| `control#lock` | Switch | ON locks, OFF unlocks — confirm on `status#locked` |
| `control#climate` | Switch | ON starts the remote climate with the two setpoints below, OFF stops it |
| `control#climateTemperature` | Number:Temperature | Target for the next climate start (seeded from the car's saved setting) |
| `control#climateDuration` | Number:Time | Minutes the climate runs |
| `control#chargeNow` | Switch | ON: start charging now while plugged in |
| `control#hazardLights` | Switch | Hazard lights on / off |
| `control#horn` | Switch | ON sounds the horn once |
| `control#findVehicle` | Switch | ON flashes the lights |
| `control#lastCommandResult` | String | Last command and the cloud's return code, e.g. `door-lock: 000000 (Success)` |
| `control#lastWake` / `control#lastPoll` | DateTime | Bookkeeping |

Every command is acknowledged by the cloud (return code `000000` = accepted) and followed by a confirming poll 45 s
later, so the state channels show what the car actually did.

---

## Quick Start

### mytoyota.things

```
Bridge mytoyota:account:home [ username="me@example.com", password="secret", brand="T", pollInterval=5, wakeWhileCharging=10 ] {
    Thing vehicle car "My bZ4X" [ vin="JTMXXXXXXXXXXXXXX" ]
}
```

| Bridge parameter | Default | Meaning |
|----|----|----|
| `brand` | `T` | `T` Toyota, `L` Lexus, `S` Subaru |
| `pollInterval` | 5 | Minutes between reads. Reads fetch what the cloud holds; they do **not** wake the car |
| `wakeWhileCharging` | 10 | While charging, ask the car for a fresh state of charge at most every N minutes (0 = never) |

Or add the bridge in the UI and pick the car from the Inbox — discovery fills in the VIN.

### mytoyota.items

```
Number:Dimensionless Car_SoC       "Battery [%.0f %%]"     { channel="mytoyota:vehicle:home:car:battery#level", unit="%" }
Number:Length        Car_Range     "Range [%.0f km]"       { channel="mytoyota:vehicle:home:car:battery#range", unit="km" }
String               Car_Charging  "Charging [%s]"         { channel="mytoyota:vehicle:home:car:battery#chargingStatus" }
Location             Car_Position  "Position"              { channel="mytoyota:vehicle:home:car:location#position" }
Switch               Car_Locked    "Locked [%s]"           { channel="mytoyota:vehicle:home:car:status#locked" }
Switch               Car_Lock      "Lock"                  { channel="mytoyota:vehicle:home:car:control#lock", autoupdate="false" }
Switch               Car_Climate   "Climate"               { channel="mytoyota:vehicle:home:car:control#climate", autoupdate="false" }
Switch               Car_Refresh   "Refresh from car"      { channel="mytoyota:vehicle:home:car:control#refresh", autoupdate="false" }
String               Car_LastCmd   "Last command [%s]"     { channel="mytoyota:vehicle:home:car:control#lastCommandResult" }
```

`unit="%"` and `unit="km"` keep the item states in the units you expect; without them openHAB stores `0.18` and
`85500 m`.

---

## How fresh is the data?

The cloud holds what the car last sent. Parked and asleep, the car sends nothing, so values keep their timestamp
(see the `lastUpdate` channels). While charging the car reports by itself now and then; `wakeWhileCharging` asks it
for a fresh state of charge more often, and `control#refresh` does the same on demand. Every wake reaches the car's
modem over the mobile network and draws on its 12 V battery — that is why the binding never wakes the car on every
poll, and why you should not automate the refresh every minute.

---

## Tested on

- **Toyota bZ4X 2025** (Denmark): all reads, the notifications, the wake, and `lock` (including the car refusing it with the key fob inside, reported on `notifications#latest`).

The other commands use the same request shape as the app and are expected to work on cars whose `capabilities`
thing property lists them (door lock, climate, hazard, horn, …). **If you own a different Toyota, Lexus or Subaru,
please post what works** — model, year and the `capabilities` property are all that is needed to extend the binding.

---

## Resources

* **Download JAR:** [org.openhab.binding.mytoyota-1.2.0.jar](https://github.com/Prinsessen/openhab-mytoyota-binding/releases/download/v1.2.0/org.openhab.binding.mytoyota-1.2.0.jar)
* **Source Code:** [github.com/Prinsessen/openhab-mytoyota-binding](https://github.com/Prinsessen/openhab-mytoyota-binding)
* **Full Documentation:** [README.md](https://github.com/Prinsessen/openhab-mytoyota-binding/blob/main/README.md)
* **Release:** [v1.2.0 — MyToyota Binding 1.2.0](https://github.com/Prinsessen/openhab-mytoyota-binding/releases/tag/v1.2.0)
* **License:** EPL-2.0

---

*Tested on openHAB 5.1. Not affiliated with Toyota Motor Corporation; "Toyota", "Lexus", "Subaru" and "MyToyota" are trademarks of their owners.*
