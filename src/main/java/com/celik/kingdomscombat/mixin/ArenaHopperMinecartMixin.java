package com.celik.kingdomscombat.mixin;

import com.celik.kingdomscombat.ArenaService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecartHopper.class)
public abstract class ArenaHopperMinecartMixin {
    @Inject(method="suckInItems",at=@At("HEAD"),cancellable=true)
    private void collect(CallbackInfoReturnable<Boolean> cir){if(ArenaService.temporaryMinecart((Entity)(Object)this))cir.setReturnValue(false);}
}
