package com.celik.kingdomscombat.mixin;

import com.celik.kingdoms.CrownPowers;
import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only the synchronous escrow swap may replace otherwise bound equipment. */
@Mixin(value=CrownPowers.class,remap=false)
public abstract class ArenaBoundEquipmentMixin {
    @Inject(method="mayReplace",at=@At("HEAD"),cancellable=true)
    private void exchange(ServerPlayer player,ItemStack next,CallbackInfoReturnable<Boolean> cir){var service=KingdomsCombat.service;if(service!=null&&service.arenas!=null&&service.arenas.swappingInventory(player))cir.setReturnValue(true);}
}
