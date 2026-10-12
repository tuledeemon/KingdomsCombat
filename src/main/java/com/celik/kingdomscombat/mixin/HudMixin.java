package com.celik.kingdomscombat.mixin;
import com.celik.kingdoms.PlayerFeedback;
import com.celik.kingdomscombat.KingdomsCombat;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=PlayerFeedback.class,remap=false)
public abstract class HudMixin {
    @ModifyArg(method="actionBar",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/game/ClientboundSetActionBarTextPacket;<init>(Lnet/minecraft/network/chat/Component;)V"),index=0)
    private Component addTag(Component original,@Local(argsOnly=true) ServerPlayer player){return KingdomsCombat.service==null?original:KingdomsCombat.service.append(original,player);}
}
