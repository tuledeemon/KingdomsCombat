# Changelog

## 1.6.1

- Add public `/worth <itemname>` and held-item `/worth` commands, including namespaced IDs, spaces/underscores, configured price-key suggestions and console lookup.
- Quote live unit, marginal full-stack and held-amount totals through the same validation and pricing path as `/sell`, without mutating market state, inventory or wallets. Show the starting unit price and before-tribute estimate notice. Add the independent `kingdomscombat.worth` permission.

## 1.6.0

- Add a public `/sell` input menu that sells on close, quotes the batch total and supports cancellation/withdrawal. Replace the old root/subcommands and redirect EconomyCraft sell-menu/shop-sell entry points into the same market.
- Add independent server-wide item pressure, marginal batch pricing, real-time/offline recovery, policy overrides and admin setting/price/reset/reload commands. Import the supplied 1,695-price list and reduce all eleven leaf types from 3 to 1.
- Reject custom names/item names, protected items, filled containers and EconomyCraft-rejected stacks. Retain items made unsellable before checkout.
- Journal deposit/withdrawal transfers against durable player receipts and queue receipt-backed EconomyCraft payments. Cancel/refund on damage/shutdown, block combat/arena/frozen access and prevent arena admission with unsold deposits.

## 1.5.3

- Handle arena defeat at the server-player death entry point, after vanilla totem protection, instead of the earlier Fabric death-permission event.
- Preserve normal totem consumption, recovery effects, usage statistics and current-match participation in both hands and gear modes. Restore consumed personal totems with the original inventory afterward. Totem-bypassing damage, no-totem defeat and disconnect forfeits retain safe recovery.

## 1.5.2

- Allow current active fighters to place match-owned minecarts, including TNT, furnace and storage variants. Add `arenaMinecarts`; keep countdown, waiting entrants and outside-boundary placement protected.
- Allow fighter interaction with temporary non-storage carts. Block storage menus, hopper transfers/collection and cart loot; clean up tagged carts after the match or on later chunk ticks.
- Allow a button’s scheduled powered-to-unpowered transition through arena protection so stone and wooden buttons release normally. Preserve other environmental block protections and arena terrain reset.

## 1.5.1

- Allow tournament entry with special equipment, using a narrowly scoped server-only inventory exchange during escrow. Normal equipment restrictions remain active for player interactions.
- Save and restore wearer state together with original gear, resynchronize derived health/effects for each loadout and recover it after defeat or restart. Preserve legacy snapshots.

## 1.5.0

- Add optional linked-account Discord role grants from recorded vanilla playtime, configured atomically by an admin command. Reuse Discord Integration, preserve other roles and perform bounded/retried network work off the server thread.
- Prevent opening/using ender chests during PvP tags and close an already-open inventory on tagging. Preserve contents and ordinary chest access; add an independent configuration toggle.
- Replace `/baltop` with a paginated wallet menu backed by offline-inclusive EconomyCraft snapshots, shared caching and background sorting.
- Completely remove suspicion checks, packet observers, alerts, evidence storage access, commands, menus and configuration. Retain player heads in staff screens.

## 1.4.1

- Add independent configurable low-rate attack timing analysis, enabled for existing configurations without changing the faster timing gate. Require several long, consistent windows and preserve mining/use/lag exemptions.
- Verify exact one-hit-per-second repetition is observable and ordinary low-rate timing variation does not flag.

## 1.4.0

- Add observational fast-click, click-timing and repeated movement/farming-cycle checks with bounded sample windows, lag guards, independent alert cooldowns and configurable thresholds. No automatic punishments or action blocking.
- Add staff-mode-only live alerts, persistent evidence, session alert toggle, per-player history and admin history clearing. Detector/storage failures preserve gameplay.
- Add a review menu ranked by decaying recent flags, with player heads linking to evidence. Use profile heads in the staff player list and detail headers, and preserve profiles for offline review.

## 1.3.4

- Add persisted own-gear and admin-kit modes for tournaments and duels, selectable by host command/menu before a bracket starts. Own gear does not require a saved kit.
- Back up complete inventories before personal gear enters the arena; restore original components, durability and consumable counts afterward. Artifacts are usable; their original records, charges and abilities restore even after breaking. Fixers and core tokens remain safely backed up outside the fight.
- Reset effects, fire, freezing, health, food, saturation and exhaustion during preparation and again immediately before gates open.
- Block private event-chest access while arena inventory recovery is pending, preventing stored arena gear from escaping restoration.

## 1.3.3

- Decorate existing kit shields in their original inventory slots instead of creating/replacing an offhand shield. Preserve enchantments, damage, names and all other non-banner components. Clear previous patterns when using a plain banner.
- Verify shield-free kits, enchanted/damaged shields and main-hand/offhand slot preservation through match preparation. Update menus and guide.

## 1.3.2

- Allow water/lava flow inside the arena, journal affected blocks and prevent flows across its boundary. Add `arenaFluidFlow`.
- Include the floor below selected corners and headroom above them; migrate existing selections once. Verify ordinary destruction, mining actions and arena-floor mining for current fighters.
- Add `/eventhost arena setlocation` and an arena-menu destination option. `/event` offers a public arena teleport without fighter permissions; recheck combat/war/AirDrop/freeze locks and load destination chunks with temporary tickets.
- Let restricted arena spectators hear staff/event hosts while their microphones remain blocked. Add `arenaSpectatorsHearStaff`; moderation mutes still override speech.

## 1.3.1

- Allow solo applications and 2v2 captain invitations/acceptances without banners. Preserve optional held-banner shield customization; leave the saved admin kit unchanged without a banner. Update menus, notices and guide.
- Verify no-banner solo acceptance, normal kit shield retention and no-banner doubles registration with native server checks.

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
