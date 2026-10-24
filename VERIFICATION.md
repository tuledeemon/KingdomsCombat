# Release verification — 1.1.1

Built for Minecraft 26.2, Java 25, Fabric Loader 0.19.5 and Fabric API 0.160.0+26.2.

- **66 unit tests passed**, including 240 save/read cycles across eight concurrent threads, canonical path aliases, preservation of a legacy staging file, failed publication cleanup and retry.
- **156 dedicated-server checks passed** with Kingdoms 1.10.1, LuckPerms Fabric 5.5.85, HuskHomes 4.11-9ed80cc and C2ME 0.4.1-beta.1.0+26.2.

The persistence checks replace the combat-state destination with a deliberately blocked path, trigger native player death and addon disconnect callbacks, and verify that errors do not escape into the server tick. They check last committed data preservation, blocked teleports during the outage, scheduled retry, persistence recovery, deferred unjournaled logout death, stable penalties for logouts during an existing outage, and successful journal/receipt recovery after storage is restored.

The staff checks use real LuckPerms users and permission caches, plus native server command, packet, menu, teleport, ban and chat entry points. Synthetic server players are connected to recording network handlers and initialized with LuckPerms' user binding; no graphical client is connected.

Coverage includes default-group grants/denials and world contexts, suppression of personal/wildcard grants and permission maps, preserving ordinary donor permissions, raw staffmode authorization, native OP and elevated-source restrictions, default-group named native permissions, restoration while on, per-tool denial, unchanged assigned nodes, stale dialogs and menus, reconnect reset and permission revocation. Removed vanish/teleport/bring/inspection commands are verified absent.

Moderation checks cover protected menu icons and native reason dialogs; warning history; freeze movement, native teleport and item-use rejection; damage protection; persistent reviews and safe recorded logout; chat and namespaced/redirected command mute rejection; mute persistence, expiry and reversal; native temporary bans and unban; target immunity; private staff-chat delivery; creative-flight cleanup; and the append-only audit file.

Existing checks cover real PvP damage, shields, projectiles, pearls, wind charges, Lunge, command aliases, war/AirDrop timers, optional HuskHomes callbacks, placeholder/HUD/title/sound packets, configuration reload, logout deaths and saved receipts, hosted-event dialogs, asynchronous event teleports, cancellation and expiry.

The server profile stopped normally. Production JAR inspection confirms server-only metadata, expected mixins and no bundled LuckPerms/Kingdoms/HuskHomes dependencies or integration-test classes.

No production server or graphical Minecraft client was used. Visual appearance, external custom rank systems and other mods' chat/permission implementations need live validation. The permission-cache hook was tested against LuckPerms Fabric 5.5.85; it is a required separate dependency.

## Update and rollback

Stop the server, back up `config/kingdoms-combat/`, `<world>/kingdoms-combat/` and the native ban file, install the matching LuckPerms dependency and replace the old combat JAR with 1.1.1. Keep a single combat JAR installed. This patch does not change the combat data format or assigned LuckPerms ranks.

If startup fails or permission gating behaves incorrectly, stop and restore the previous JAR and saved configuration. Version 1.0.2 does not enforce off-mode permission restriction or saved freezes/mutes; native bans remain in force until removed. Do not silently downgrade an active staff review. This release was published as an artifact; no production deployment was performed.
