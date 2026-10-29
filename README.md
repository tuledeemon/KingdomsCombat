# Kingdoms Combat

A separate **server-only Fabric 26.2 addon** for [Kingdoms](https://github.com/tuledeemon/Kingdoms). It adds combat logout deaths and configurable teleport restrictions during combat, active wars and nearby AirDrops.

**No client mod or resource pack is needed.** Custom menus, messages, titles and the countdown use Kingdoms' small-caps style.

**1.5.3:** Fix arena elimination running before totems. Totems now activate normally in either hand; own-gear inventory recovery still restores consumed items.

**1.5.2:** Active fighters can place temporary minecarts during matches. Fix stone/wooden buttons staying pressed. Carts clean up after the match, with storage transfers and cart loot blocked.

**1.5.1:** Fix tournament entry and recovery with special equipment in both gear modes. Preserve its saved wearer state and normal equipment restrictions.

**1.5.0:** Automatic Discord roles for recorded playtime, combat-tag ender-chest restrictions and a paginated player-wallet `/baltop`. Remove the entire suspicion-flag system while retaining player heads in staff menus. [Role setup and leaderboard](SERVER_UTILITIES_GUIDE.md).

**1.3.4:** Optional own-gear tournaments and duels back up personal equipment and restore original items and supplies afterward. Every round clears effects and fully resets health, food, saturation and exhaustion before gates open. [Commands and limitations](TOURNAMENTS_GUIDE.md#choose-the-gear-mode).

**1.3.3:** Banner customization only decorates existing kit shields in place, preserving enchantments, damage and other properties. Kits without shields never receive one.

**1.3.2:** Arena water/lava flow and reset, public viewing-area teleport through `/event`, spectators hearing staff voice while muted, and automatic floor/headroom protection for current-fighter mining.

**1.3.1:** Banners are optional for solo and team applications. A held banner customizes the shield; otherwise the configured kit is unchanged.

**1.3.0:** Hosted duels and 1v1/2v2 single-elimination tournaments: arena claims, two spawns, gates, saved kits, banner shields, manual/automatic opening brackets and shared Elo. Only current fighters get arena permissions. Original inventories restore after defeat, logout or restart. Optional Simple Voice Chat honors staff mutes and arena restrictions. [Setup and commands](TOURNAMENTS_GUIDE.md).

**1.2.1:** Combat-log death and item drops now happen immediately at the logout location, before Minecraft saves and removes the player. Reconnecting does not repeat the death.

**1.2.0:** Hosted events can use a private input chest with required items, item/money rewards, configurable repeat claims and optional ending after the first claim. `/eventhost chest` opens its setup menu. Teleport destinations inside incoming or unclaimed AirDrop areas are blocked from outside, using the configured AirDrop radius; staff mode retains its bypass.

**1.1.1:** Fixes the combat-save crash with serialized writes, unique temporary files and automatic runtime retry. Failed combat saves retain pending changes in memory and defer unjournaled logout deaths.

**1.1.0:** `/staffmode` now controls staff permissions as well as the combat exemption. While off, recognized staff receive only the LuckPerms default group's permissions, including OP restriction. While on, their original permissions return and `/staff` opens freeze, warning, mute, kick, ban and notes tools. Staff chat uses `/sc`. Timed hosted events and `/event` remain available.

## Default rules

- PvP hits tag both players for **20 seconds**. Logging out while combat tagged causes a normal Minecraft death.
- Successful ender pearl throws, wind charge uses and spear **Lunge enchantment impulses** refresh only the user's existing timers.
- Killing your opponent ends that fight. If another opponent remains, their timer continues.
- Active war refreshes a **20-second teleport lock** for participating kingdom members. Preparation adds no war lock.
- Standing within **100 blocks horizontally** of a descending or capturable AirDrop refreshes a **20-second teleport lock**. Any winning side, cancellation or expiry stops refreshing it.
- War/AirDrop timers count down after the restriction ends or you leave the area. These timers do not cause logout death by default.
- Configured teleport commands are blocked. The optional HuskHomes API guard also checks the actual teleporter at warmup and execution, covering queued teleports and menu clicks.

The countdown appends to the Kingdoms action bar by default. Disable `actionBar` to show it somewhere else with **`%kingdomscombat:tag%`**.

## Install

Put the [release JAR](https://github.com/tuledeemon/KingdomsCombat/releases) in the **server's** `mods` folder alongside:

- Minecraft **26.2**, Java **25**, Fabric Loader **0.19.5+** and Fabric API for 26.2.
- **Kingdoms 1.12.1+** and its existing dependencies.
- Text Placeholder API **3.1.0-beta.1+26.2** (already required by Kingdoms).
- **LuckPerms Fabric 5.5.85+** for 26.2, installed separately; required for staff permission isolation.

HuskHomes is optional. Its Fabric 26.2 build **4.11-9ed80cc** is the compatibility target. Kingdoms, LuckPerms, HuskHomes and Placeholder API are installed separately; they are not bundled into this addon.

Use **`/combat`** for player status and **`/combatadmin`** for the admin menu. Text settings open Minecraft dialogs. Configuration is generated at `config/kingdoms-combat/config.json`; persistent tags and logout penalties live at `<world>/kingdoms-combat/state.json`.

## Guides

- [Player and administrator guide](GUIDE.md)
- [Staff mode and events](STAFF_EVENTS_GUIDE.md)
- [Tournaments and duels](TOURNAMENTS_GUIDE.md)
- [Player guide on the Kingdoms wiki](https://github.com/tuledeemon/Kingdoms/wiki/Combat-and-Teleports)
- [Building and verification](BUILDING.md)
- [Changelog](CHANGELOG.md)

The HuskHomes integration follows its official [Fabric API events](https://william278.net/docs/huskhomes/api-events) and [command/alias reference](https://william278.net/docs/huskhomes/commands).
