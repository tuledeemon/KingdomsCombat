package com.celik.kingdomscombat.mixin;
import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerPlayer.class)
public abstract class ArenaServerPlayerMixin {
    @Inject(method="drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",at=@At("HEAD"),cancellable=true)
    private void drop(ItemStack stack,boolean random,boolean trace,CallbackInfoReturnable<ItemEntity> cir){if(KingdomsCombat.service!=null&&KingdomsCombat.service.arenas.reserved(((ServerPlayer)(Object)this).getUUID()))cir.setReturnValue(null);}
}
