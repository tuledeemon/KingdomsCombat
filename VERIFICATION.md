# Release verification — 1.0.0

The release was built for Minecraft 26.2 with Java 25, Fabric Loader 0.19.5, Fabric API 0.160.0+26.2 and Kingdoms 1.7.1.

- **47 unit tests** passed with no failures or errors.
- **54 checks** passed on an isolated dedicated server without optional HuskHomes or C2ME.
- **58 checks** passed with HuskHomes Fabric 4.11-9ed80cc and C2ME 0.4.1-beta.1.0+26.2 together.

The server checks exercise native PvP damage, shields, player projectiles, ender pearl and wind charge use, the Lunge enchantment impulse, command execution and redirect aliases, war preparation/active-war timers, AirDrop state changes, optional HuskHomes warmups and teleport callbacks, action bar/title/private sound packets, real placeholder registrations, menus, configuration reloads, disconnect deaths, player-save receipts and penalty recovery during login immunity.

Both optional-mod and baseline profiles stopped normally. Production JAR inspection verifies server-only metadata, expected mixins and the absence of bundled dependencies or integration test classes.

No graphical Minecraft client or production server was used. Visual appearance and compatibility with other teleport systems or anticheat mods still require live testing.
