package com.celik.kingdomscombat.mixin;

import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class ArenaPlayerMixin {
    @Inject(method="drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;",at=@At("HEAD"),cancellable=true)
    private void drop(ItemStack stack,boolean random,CallbackInfoReturnable<ItemEntity> cir){if((Object)this instanceof ServerPlayer p&&KingdomsCombat.service!=null&&KingdomsCombat.service.arenas.reserved(p.getUUID()))cir.setReturnValue(null);}
}
