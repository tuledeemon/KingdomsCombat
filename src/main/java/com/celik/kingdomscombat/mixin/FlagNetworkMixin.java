package com.celik.kingdomscombat.mixin;

import com.celik.kingdomscombat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class FlagNetworkMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handlePlayerAction",at=@At("RETURN"))
    private void mining(ServerboundPlayerActionPacket packet,CallbackInfo ci){var s=KingdomsCombat.service;if(s==null||!player.level().getServer().isSameThread())return;switch(packet.getAction()){case START_DESTROY_BLOCK->s.flags.observe(()->s.flags.miningAction(player,true));case ABORT_DESTROY_BLOCK,STOP_DESTROY_BLOCK->s.flags.observe(()->s.flags.miningAction(player,false));default->{}}}
    @Inject(method="handleAnimate",at=@At("RETURN"))
    private void swing(ServerboundSwingPacket packet,CallbackInfo ci){
        var s=KingdomsCombat.service;
        if(s!=null&&player.level().getServer().isSameThread()&&packet.getHand()==InteractionHand.MAIN_HAND)s.flags.observe(()->s.flags.swing(player,((SwingArrival)packet).combat$arrivalNanos()));
    }
}
