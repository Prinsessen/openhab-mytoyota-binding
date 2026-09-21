# MyToyota Binding for openHAB

Reads Toyota, Lexus and Subaru vehicles in Europe through the cloud behind the
MyToyota app (Toyota Connected Services Europe): battery level, range, charging
status, odometer, last parked position, doors, windows, lights and climate
state. One command channel wakes the car so it reports a fresh state of charge.

Toyota has no public API. The binding speaks the same protocol as the mobile
app, as documented by the [pytoyoda](https://github.com/pytoyoda/pytoyoda)
project. When Toyota changes something, this binding needs the same fix as
pytoyoda; check there first when a channel stops updating.

Built and tested against a 2025 bZ4X (Denmark) on openHAB 5.1.

## Things

| thing | type | what |
|---|---|---|
| `mytoyota:account` | bridge | the app login; one per MyToyota account |
| `mytoyota:vehicle` | thing | one vehicle, found by discovery under the account |

### Account (bridge) configuration

| parameter | required | default | meaning |
|---|---|---|---|
| `username` | yes | | the e-mail of the MyToyota app login |
| `password` | yes | | its password |
| `brand` | no | `T` | `T` Toyota, `L` Lexus, `S` Subaru |
| `pollInterval` | no | 5 | minutes between reads of every vehicle. Reads fetch what the cloud already holds and do not wake the car |
| `wakeWhileCharging` | no | 10 | while the car says it is charging, ask it for a fresh state of charge before a poll, at most every this many minutes. `0` disables the wake |

### Vehicle configuration

| parameter | required | meaning |
|---|---|---|
| `vin` | yes | the 17-character VIN, filled in by discovery |

The thing carries properties from the account's vehicle list: model, model
year, nickname, generation, EV flag and the list of capabilities the car
reports (door lock, climate, ...).

## Channels

| channel | type | notes |
|---|---|---|
| `battery#level` | Number:Dimensionless | state of charge, % |
| `battery#range` | Number:Length | EV range |
| `battery#rangeWithAc` | Number:Length | EV range with A/C on |
| `battery#chargingStatus` | String | as reported: `none`, `charging`, `chargeComplete`, ... |
| `battery#charging` | Switch | ON while the status contains "charging" |
| `battery#remainingChargeTime` | Number:Time | minutes, UNDEF when not charging |
| `battery#lastUpdate` | DateTime | when the car reported the battery values |
| `telemetry#odometer` | Number:Length | |
| `telemetry#distanceToEmpty` | Number:Length | |
| `telemetry#lastUpdate` | DateTime | |
| `location#position` | Location | last parked position; only updated when parked |
| `location#name` | String | the cloud's name for it, e.g. "Last Parked" |
| `location#lastUpdate` | DateTime | when the position was acquired |
| `status#overall` | String | `ok` or a warning state |
| `status#warnings` | Number | warning count |
| `status#locked` | Switch | ON when every door reports locked |
| `status#anyDoorOpen` | Contact | OPEN when any door is open (hood and trunk excluded) |
| `status#anyWindowOpen` | Contact | |
| `status#trunkOpen` | Contact | |
| `status#hoodOpen` | Contact | |
| `status#hazardLights` | Switch | |
| `status#lastUpdate` | DateTime | |
| `climate#status` | String | `stopped`, `starting`, `running` |
| `control#refresh` | Switch | send ON: wake the car for fresh battery and door state, poll again 45 s later; resets to OFF |
| `control#lock` | Switch | ON locks the doors, OFF unlocks; confirm on `status#locked` after the poll 45 s later |
| `control#hazardLights` | Switch | hazard lights on / off |
| `control#horn` | Switch | ON sounds the horn once |
| `control#findVehicle` | Switch | ON flashes the lights |
| `control#climate` | Switch | ON starts the remote climate with the two setpoints below, OFF stops it; confirm on `climate#status` |
| `control#climateTemperature` | Number:Temperature | target for the next climate start (seeded from the car's saved setting) |
| `control#climateDuration` | Number:Time | minutes the climate runs (seeded from the car) |
| `control#chargeNow` | Switch | ON starts charging now while plugged in, overriding a schedule |
| `control#lastCommandResult` | String | last command and the cloud's return code, e.g. `door-lock: 000000 (Success)` |
| `control#lastWake` | DateTime | last wake request sent |
| `control#lastPoll` | DateTime | last successful poll |

## Example

```java
Bridge mytoyota:account:home [ username="me@example.com", password="secret", pollInterval=5, wakeWhileCharging=10 ] {
    Thing vehicle bz4x "bZ4X" [ vin="JTMXXXXXXXXXXXXXX" ]
}
```

```java
Number:Dimensionless Car_SoC      "Battery [%.0f %%]"   { channel="mytoyota:vehicle:home:bz4x:battery#level" }
Number:Length        Car_Range    "Range [%.0f km]"     { channel="mytoyota:vehicle:home:bz4x:battery#range" }
String               Car_Charging "Charging [%s]"       { channel="mytoyota:vehicle:home:bz4x:battery#chargingStatus" }
Location             Car_Position "Position"            { channel="mytoyota:vehicle:home:bz4x:location#position" }
Switch               Car_Locked   "Locked [%s]"         { channel="mytoyota:vehicle:home:bz4x:status#locked" }
Switch               Car_Refresh  "Refresh from car"    { channel="mytoyota:vehicle:home:bz4x:control#refresh" }
```

## Remote commands

The control channels send what the app sends: `/v1/global/remote/command`
for lock, unlock, hazard lights, horn and find-vehicle,
`/v2/remote/climate-control` for climate start and stop, and
`/v1/global/remote/electric/command` for charge-now. The cloud only
acknowledges the request (return code `000000`); whether the car did it shows
on the state channels, which the binding polls again 45 seconds after every
command. Which commands a car supports is in the thing's `capabilities`
property. Commands wake the car's modem like the refresh does.

Verified on the test car (bZ4X 2025): lock while parked. The other commands
use the same request shape and are expected to work on cars whose
capabilities list them; report back if one does not.

## How fresh is the data

The cloud holds what the car last sent. Parked and asleep, the car sends
nothing, so the values keep their timestamp (see the `lastUpdate` channels).
While charging, the car reports by itself now and then; the
`wakeWhileCharging` setting asks it for a fresh state of charge more often, and
`control#refresh` does the same on demand. Every wake reaches the car's modem
over the mobile network and draws on its 12 V battery, which is why the binding
never wakes the car on every poll.

## Errors and rate limits

Toyota's gateway answers 429 or 5xx now and then; the binding retries with 2,
4 and 8 second pauses. A 401 triggers a new login once. Wrong credentials leave
the bridge OFFLINE with the message from the login service; it retries every
10 minutes.

## Installation

Download the JAR from the latest release and copy it into openHAB's `addons` folder. No other bundle is needed.

    https://github.com/Prinsessen/openhab-mytoyota-binding/releases/download/v1.1.0/org.openhab.binding.mytoyota-1.1.0.jar
    sha256 f82360a1cfa1d2778dd21e417d114a3472c0fd6759ac6be204cae1fc8c9aa0ab

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
