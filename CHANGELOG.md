# Changelog

## 1.0.1

- Add the staff-only `/staffmode` toggle. Enabling it clears the staff member's PvP tag, stops staff interactions starting combat tags, and prevents addon logout deaths.
- Keep war and AirDrop teleport restrictions active. Staff mode grants no damage immunity or claim bypass.
- Reset the toggle on disconnect/restart and check staff permissions whenever the exemption is used.
- Add `%kingdomscombat:staffmode%` and make the logout-penalty placeholder/menu reflect the exemption.
- Add separate configurable LuckPerms permission nodes for staff mode, event hosting and combat administration, with OP fallback and explicit-deny handling.
- Add `/eventhost "Event name" 10m`, `/eventhost setlocation`, `/eventhost stop`, global boss bars, titles, sounds, chat and persistent event countdowns.
- Add `/event` with a description/time/host dialog and a teleport button, with combat/war/AirDrop checks before and after asynchronous destination loading.
- Add `/eventhost description <text>` and reject teleport buttons belonging to ended or replaced events.
- Add hosted-event placeholders and configurable duration, messages, display colors and loading timeout.

## 1.0.0

- Separate server-side Fabric 26.2 addon for Kingdoms 1.7.1+.
- Configurable PvP combat timers, directional movement refreshes, opponent-specific kill cleanup and native logout deaths.
- Active-war and 100-block AirDrop teleport locks with configurable countdown tails; preparation and claimed drops add no new lock.
- Configurable command roots/paths/wildcards, namespace and redirect checks, plus optional HuskHomes warmup/execution guards.
- Small-caps status/admin menus, dialog inputs, titles, private sounds, configurable action-bar modes and Text Placeholder API integration.
- Persistent timestamps and journaled logout penalties with player receipts for reconnect recovery.
