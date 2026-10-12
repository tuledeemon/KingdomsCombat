package com.celik.kingdomscombat;
import net.william278.huskhomes.event.*;
import net.william278.huskhomes.teleport.Teleportable;
import net.william278.huskhomes.user.FabricUser;
import net.minecraft.world.InteractionResult;
public final class HuskHomesHook {
    private HuskHomesHook(){}
    public static boolean blocked(Teleportable target){
        CombatService s=KingdomsCombat.service;if(s==null||s.stopping||!s.config.huskHomesTeleportGuard||!(target instanceof FabricUser user))return false;
        if(s.server.isSameThread())return !s.allowTeleport(user.getPlayer(),true);
        return s.server.submit(()->!s.allowTeleport(user.getPlayer(),true)).join();
    }
    public static void register(){
        TeleportCallback.EVENT.register(event->{if(blocked(event.getTeleport().getTeleporter())){event.setCancelled(true);return InteractionResult.FAIL;}return InteractionResult.PASS;});
        TeleportWarmupCallback.EVENT.register(event->{if(blocked(event.getTimedTeleport().getTeleporter())){event.setCancelled(true);return InteractionResult.FAIL;}return InteractionResult.PASS;});
        TeleportBackCallback.EVENT.register(event->{if(blocked(event.getTeleport().getTeleporter())){event.setCancelled(true);return InteractionResult.FAIL;}return InteractionResult.PASS;});
        RandomTeleportCallback.EVENT.register(event->{if(blocked(event.getTeleport().getTeleporter())){event.setCancelled(true);return InteractionResult.FAIL;}return InteractionResult.PASS;});
    }
}
