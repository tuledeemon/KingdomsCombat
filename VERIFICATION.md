# Release verification — 1.0.1

The release was built for Minecraft 26.2 with Java 25, Fabric Loader 0.19.5, Fabric API 0.160.0+26.2 and Kingdoms 1.7.1.

- **63 unit tests** passed with no failures or errors.
- **86 checks** passed on an isolated dedicated server without optional HuskHomes or C2ME.
- **96 checks** passed with HuskHomes Fabric 4.11-9ed80cc and C2ME 0.4.1-beta.1.0+26.2 together.

The server checks exercise native PvP damage, shields, player projectiles, ender pearl and wind charge use, the Lunge enchantment impulse, command execution and redirect aliases, war preparation/active-war timers, AirDrop state changes, optional HuskHomes warmups and teleport callbacks, action bar/title/private sound packets, real placeholder registrations, menus, configuration reloads, disconnect deaths, player-save receipts and penalty recovery during login immunity.

Both optional-mod and baseline profiles stopped normally. Production JAR inspection verifies server-only metadata, expected mixins and the absence of bundled dependencies or integration test classes.

Staff-mode checks cover command and service permission checks, clearing existing combat, normal damage without new tags, retained war/AirDrop teleport restrictions, native placeholder output, safe logout, reset on disconnect, permission revocation, and normal combat after toggling off.

Hosted-event checks cover duration parsing, permissions, global boss bars, title/sound packets, the native description/time/teleport dialog, saved locations and descriptions, cooldown-blocked teleports, successful native teleporting, nonblocking chunk requests, combat starting during loading, persistence, late joiners, cancellation, expiry and stale dialog buttons. A permission-provider fixture exercises the same Fabric Permissions API contract used by LuckPerms, including non-OP grants and explicit denials; a full LuckPerms server setup was not tested.

No graphical Minecraft client or production server was used. Visual appearance and compatibility with other teleport systems or anticheat mods still require live testing.
