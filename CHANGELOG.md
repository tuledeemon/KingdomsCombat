# Changelog

## 1.3.0

- Add one protected event-arena cuboid with two opposing spawns, admin-saved inventory/armor kit and individually selected gate blocks. Keep gates closed during preparation, open them at fight start and restore terrain/gates afterward.
- Add direct 1v1s and single-elimination 1v1/2v2 tournaments. Doubles use captain invitations with teammate acceptance. Copy each applicant's banner color/patterns onto their kit shield.
- Keep the earliest power-of-two number of complete applications; exclude latest excess entries without byes. Preserve ordered applications and manual opening pairs; auto-pair the remaining teams and advance winners.
- Grant build/PvP permissions only to the current non-eliminated fighters; protect waiting entrants, spectators and the arena against interference. Block storage access, kit drops and item pickups; retire temporary projectiles after their match, including after chunk reload/restart.
- Save original inventory/components, equipment, XP, effects, food, health and location before kits. Intercept lethal arena damage without drops, restore eliminated players immediately, retain recovery backups until native player data confirms the receipt, and recover offline/unloaded-chunk returns with bounded tickets.
- Treat a current fighter's disconnect as a side forfeit. Preserve ordinary combat-log behavior outside the arena. Void interrupted matches on restart, restore originals and pause the preserved bracket.
- Persist one shared individual Elo ladder for singles/doubles; average team ratings for doubles. Expose Elo, current-fighter and tournament-name placeholders; add menus, input dialogs, titles, boss bars and configurable limits/timers.
- Add optional Simple Voice Chat microphone and outgoing-audio hooks. Staff mutes block speech globally; arena spectators cannot send/receive voice. Publish immutable thread-safe policy snapshots without querying worlds from the voice thread.

## 1.2.1

- Run combat-log death at the start of vanilla server-side disconnect, before the player save/removal. Remove the network-event callback that could race logout.
- Close the current container before death so cursor and crafting inputs follow normal inventory drops. Keep native inventory/experience rules and existing staff/shutdown exemptions.
- Keep the saved death receipt so rejoining does not repeat the penalty. Protocol reconfiguration does not trigger logout death.

## 1.2.0

- Add host-configured event input chests with exact item/component/count requirements, up to nine required stacks, up to eight reward stacks, and an optional EconomyCraft wallet reward. Native chest and input-dialog menus need no client mod.
- Make once-per-player and end-after-first-claim independent host options. Consume only required quantities, reject insufficient inputs/full reward inventories, and prevent reward-preview extraction or using unrelated items as private storage.
- Save each deposit/withdrawal/claim with a verified vanilla player-save receipt and a durable recovery journal. Keep private inputs recoverable after closure, logout, cancellation or expiry; use Kingdoms' idempotent economy delivery for pending cash.
- Block teleport arrivals from outside an incoming/capturable AirDrop area, including native server, direct connection and cross-dimension entry points. Resolve relative coordinates, use the existing radius/dimension rules, release the destination restriction after a win, and retain staff-mode bypass.
- Add `airDropBlockIncomingTeleports` and update the host/player guides.

## 1.1.1

- Fix the `state.json.tmp → state.json` missing-file crash: serialize save/read transactions and stage each write in its own unique file in the destination directory.
- Flush staged data before replacing the destination; clean only the current write's temporary file and preserve legacy `.tmp` recovery files.
- Runtime combat-save failures retain pending changes in memory, retry at the configured save interval and log errors with a 30-second throttle. Ordinary restricted teleports remain blocked until a save succeeds.
- Defer logout death if its penalty journal cannot be saved; reject pending-penalty recovery during a storage outage and resume safely once the journal is durable. Startup still refuses an unwritable combat-state file.
- Add concurrent-writer and filesystem-failure regression coverage, including real death/disconnect callbacks and recovery.

## 1.1.0

- Restrict recognized staff to the configured LuckPerms default group's permissions while staffmode is off; preserve their assigned groups and nodes, including contextual inheritance. Suppress native OP fallback while off and retain separate authorization for `/staffmode`.
- Add the staffmode-only `/staff` menu with freeze/unfreeze, warning, timed/permanent mute and ban, kick, notes, history and private staff chat (`/sc`). Input uses native Minecraft dialogs.
- Persist reviews and mutes across reconnects/restarts; use native ban storage. Record moderation actions and frozen logouts in an append-only audit log.
- Freeze movement, combat, item use, inventory actions and teleporting during review. Close moderation menus and reset creative/spectator flight when leaving staff mode.
- Add per-tool permissions, moderation immunity, configuration limits and `%kingdomscombat:frozen%` / `%kingdomscombat:muted%` placeholders.
- Require separately installed LuckPerms Fabric 5.5.85+ for 26.2. No vanish, teleport, bring or inventory-inspection tools are added.

## 1.0.2

- Staff mode now bypasses all combat, war and AirDrop restrictions and clears existing timers, including their cooldown tails.
- Apply the exemption to HuskHomes callbacks and the event teleport button. Staff permissions and reset-on-disconnect behavior remain in effect.

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
