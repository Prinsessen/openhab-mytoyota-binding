# Changelog

## 1.3.0 (unreleased)

Per-door, per-window and per-light channels (groups `doors`, `windows`, `lights`),
plus the rear seat reminder: the same detail the app's status page shows.

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
