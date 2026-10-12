package com.celik.kingdomscombat;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.william278.huskhomes.FabricHuskHomes;
import net.william278.huskhomes.user.FabricUser;
import net.william278.huskhomes.teleport.*;
import net.william278.huskhomes.event.*;
import java.util.function.BiConsumer;

final class HuskCompatibility {
    static void run(MinecraftServer server,ServerPlayer a,ServerPlayer b,CombatService service,BiConsumer<Boolean,String> check)throws Exception{
        var plugin=(FabricHuskHomes)FabricLoader.getInstance().getEntrypoints("server",DedicatedServerModInitializer.class).stream().filter(p->p instanceof FabricHuskHomes).findFirst().orElseThrow();
        var user=FabricUser.adapt(a,plugin);var target=FabricUser.adapt(b,plugin);
        var teleport=Teleport.builder(plugin).teleporter(user).executor(user).target(target.getPosition()).toTeleport();
        var event=TeleportCallback.SUPPLIER.apply(teleport);TeleportCallback.EVENT.invoker().invoke(event);check.accept(event.isCancelled(),"actual HuskHomes teleport callback cancels tagged player teleports");
        var warmup=Teleport.builder(plugin).teleporter(user).executor(user).target(target.getPosition()).toTimedTeleport();var waiting=TeleportWarmupCallback.SUPPLIER.apply(warmup,3);TeleportWarmupCallback.EVENT.invoker().invoke(waiting);check.accept(waiting.isCancelled(),"actual HuskHomes warmup callback prevents starting while tagged");
        service.config.huskHomesTeleportGuard=false;var permitted=TeleportCallback.SUPPLIER.apply(teleport);TeleportCallback.EVENT.invoker().invoke(permitted);check.accept(!permitted.isCancelled(),"HuskHomes execution guard can be disabled separately from command rules");service.config.huskHomesTeleportGuard=true;
        service.ledger.zone(a.getUUID(),TagLedger.Reason.WAR,service.now(),20);service.ledger.zone(a.getUUID(),TagLedger.Reason.AIRDROP,service.now(),20);service.toggleStaffMode(a);var staff=TeleportCallback.SUPPLIER.apply(teleport);TeleportCallback.EVENT.invoker().invoke(staff);check.accept(!staff.isCancelled(),"staff mode bypasses the actual HuskHomes guard during war and AirDrop restrictions");service.toggleStaffMode(a);
        service.ledger.clear(a.getUUID());var clear=TeleportCallback.SUPPLIER.apply(teleport);TeleportCallback.EVENT.invoker().invoke(clear);check.accept(!clear.isCancelled(),"HuskHomes works normally once combat restrictions are cleared");service.ledger.manual(a.getUUID(),service.now(),20);
    }
}
