package com.celik.kingdomscombat.mixin;
import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Temporary projectiles/loot cannot return to normal inventories after their match or a restart. */
@Mixin(Entity.class)
public abstract class ArenaKitEntityMixin {
    @Inject(method="spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",at=@At("HEAD"),cancellable=true)
    private void cartDrop(net.minecraft.server.level.ServerLevel level,net.minecraft.world.item.ItemStack stack,net.minecraft.world.phys.Vec3 offset,CallbackInfoReturnable<net.minecraft.world.entity.item.ItemEntity> cir){if(com.celik.kingdomscombat.ArenaService.temporaryMinecart((Entity)(Object)this))cir.setReturnValue(null);}
    @Inject(method="baseTick",at=@At("HEAD"),cancellable=true)
    private void arenaExpiry(CallbackInfo ci){Entity self=(Entity)(Object)this;var service=KingdomsCombat.service;if(service!=null&&self.entityTags().contains("kingdomscombat_arena")&&!service.arenas.entityActive(self)){self.discard();ci.cancel();}}
}
