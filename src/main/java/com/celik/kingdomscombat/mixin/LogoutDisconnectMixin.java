package com.celik.kingdomscombat.mixin;

import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Run on the main thread, before vanilla persists/unregisters the departing player. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class LogoutDisconnectMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="onDisconnect",at=@At("HEAD"))
    private void combatLogout(DisconnectionDetails details,CallbackInfo ci){
        if(KingdomsCombat.service==null)return;
        try{KingdomsCombat.service.disconnect(player);}
        catch(RuntimeException error){KingdomsCombat.LOG.error("Combat logout processing failed for {}; retaining penalty recovery state",player.getUUID(),error);}
    }
}
