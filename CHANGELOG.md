# Changelog

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
