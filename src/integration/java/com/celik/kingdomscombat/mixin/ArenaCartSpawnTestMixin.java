package com.celik.kingdomscombat.mixin;
import com.celik.kingdomscombat.CartSpawnCapture;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ServerLevel.class)
public abstract class ArenaCartSpawnTestMixin {
    @WrapMethod(method="addFreshEntity")
    private boolean capture(Entity entity,Operation<Boolean> original){boolean accepted=original.call(entity);if(CartSpawnCapture.enabled&&entity instanceof AbstractMinecart cart)CartSpawnCapture.attempts.add(new CartSpawnCapture.Attempt(cart,accepted));return accepted;}
}
