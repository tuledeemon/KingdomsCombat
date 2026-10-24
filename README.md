# Kingdoms Combat

A separate **server-only Fabric 26.2 addon** for [Kingdoms](https://github.com/tuledeemon/Kingdoms). It adds combat logout deaths and configurable teleport restrictions during combat, active wars and nearby AirDrops.

**No client mod or resource pack is needed.** Custom menus, messages, titles and the countdown use Kingdoms' small-caps style.

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
- **Kingdoms 1.7.1+** and its existing dependencies.
- Text Placeholder API **3.1.0-beta.1+26.2** (already required by Kingdoms).
- **LuckPerms Fabric 5.5.85+** for 26.2, installed separately; required for staff permission isolation.

HuskHomes is optional. Its Fabric 26.2 build **4.11-9ed80cc** is the compatibility target. Kingdoms, LuckPerms, HuskHomes and Placeholder API are installed separately; they are not bundled into this addon.

Use **`/combat`** for player status and **`/combatadmin`** for the admin menu. Text settings open Minecraft dialogs. Configuration is generated at `config/kingdoms-combat/config.json`; persistent tags and logout penalties live at `<world>/kingdoms-combat/state.json`.

## Guides

- [Player and administrator guide](GUIDE.md)
- [Staff mode and events](STAFF_EVENTS_GUIDE.md)
- [Player guide on the Kingdoms wiki](https://github.com/tuledeemon/Kingdoms/wiki/Combat-and-Teleports)
- [Building and verification](BUILDING.md)
- [Changelog](CHANGELOG.md)

The HuskHomes integration follows its official [Fabric API events](https://william278.net/docs/huskhomes/api-events) and [command/alias reference](https://william278.net/docs/huskhomes/commands).
