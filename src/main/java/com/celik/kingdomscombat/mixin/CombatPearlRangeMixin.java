package com.celik.kingdomscombat.mixin;

import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownEnderpearl.class)
public abstract class CombatPearlRangeMixin {
    @Inject(method="onHit",at=@At("HEAD"),cancellable=true)
    private void range(HitResult hit,CallbackInfo ci){
        var pearl=(ThrownEnderpearl)(Object)this;var service=KingdomsCombat.service;
        // Vanilla teleports to oldPosition(), not the hit location/current projectile position.
        if(hit.getType()!=HitResult.Type.MISS&&service!=null&&pearl.level() instanceof ServerLevel destination&&pearl.getOwner() instanceof ServerPlayer player&&!service.allowCombatPearl(player,destination,pearl.oldPosition())){
            pearl.discard();ci.cancel();
        }
    }
}
