# Building Kingdoms Combat

Use Java **25** and the included Gradle **9.4.1** wrapper. Minecraft and Fabric dependencies are fetched by Gradle.

## Local dependency JARs

Create `libs/` with these separately distributed dependencies:

- Kingdoms **1.12.1 or newer** for 26.2 (the local historical filename may still be `kingdoms-1.7.1-mc26.2.jar`; use current JAR contents).
- `LuckPerms-Fabric-5.5.85.jar` for 26.2; required at runtime and never bundled.
- `placeholder-api-3.1.0-beta.1+26.2.jar`
- `voicechat-fabric-2.6.22+26.2.jar` supplies the optional integration API during compilation and development. Voice Chat is installed separately on production servers and is not bundled.
- EconomyCraft 1.10.0 for 26.2 and Architectury 21.1.9 for the development server.
- `huskhomes-fabric-26.2.jar`, using the official Fabric 26.2 build `4.11-9ed80cc`.

HuskHomes embeds its common API and support libraries. For compilation, extract its `META-INF/jars/*.jar` files into `libs/huskhomes-api/`. These files are compile-only and are not redistributed or bundled into this addon.

```text
./gradlew test build
./gradlew runIntegration
```

Windows uses `gradlew.bat`. Set `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Temp` and create `C:\Temp` before running Gradle. Keep Unicode project paths intact and quote them. If Java cannot read test worker classpaths containing Unicode, copy the project into an ASCII temporary build directory and copy verified artifacts back. Keep the real workspace and account paths unchanged.

## Integration profiles

`runIntegration` creates a disposable localhost server on **127.0.0.1:25692**, generates its test properties/EULA file, and shuts down after its checks. Read [Minecraft's EULA](https://www.minecraft.net/eula) before running. The integration test mod is excluded from the production JAR.

- Without optional mods: leave `run-test/mods/` empty. The normal development dependencies in `libs/` still load Kingdoms and its economy/placeholder dependencies.
- With HuskHomes: copy the matching official HuskHomes JAR to `run-test/mods/`.
- With HuskHomes and C2ME: also add C2ME `0.4.1-beta.1.0+26.2` there.

The tests use real damage, item-use, Lunge, command, HUD, placeholder, Kingdoms war/AirDrop data, disconnect callbacks and player-save paths. They do not connect a graphical Minecraft client.
