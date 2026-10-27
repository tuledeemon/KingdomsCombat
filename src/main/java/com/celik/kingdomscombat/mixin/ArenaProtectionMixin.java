package com.celik.kingdomscombat.mixin;

import com.celik.kingdoms.Protection;
import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Integrate the arena at the same decision point used by Kingdoms' player/projectile hooks. */
@Mixin(value=Protection.class,remap=false)
public abstract class ArenaProtectionMixin {
    @Inject(method="damage",at=@At("HEAD"),cancellable=true)
    private static void pvp(Entity target,DamageSource source,CallbackInfoReturnable<Boolean> cir){var service=KingdomsCombat.service;if(service!=null&&service.arenas!=null){Boolean allowed=service.arenas.pvp(target,source);if(allowed!=null)cir.setReturnValue(allowed);}}
    @Inject(method="mutation",at=@At("HEAD"),cancellable=true)
    private static void blocks(Level level,BlockPos pos,BlockState state,CallbackInfoReturnable<Boolean> cir){var service=KingdomsCombat.service;if(service!=null&&service.arenas!=null&&!service.arenas.mutation(level,pos,state))cir.setReturnValue(false);}
    @Inject(method="explosion",at=@At("HEAD"),cancellable=true)
    private static void explosion(Level level,BlockPos origin,BlockPos target,java.util.UUID actor,CallbackInfoReturnable<Boolean> cir){var service=KingdomsCombat.service;if(service!=null&&service.arenas!=null&&service.arenas.inside(level,target))cir.setReturnValue(false);}
    @Inject(method="admitSpawn",at=@At("HEAD"),cancellable=true)
    private static void entity(Entity entity,CallbackInfoReturnable<Boolean> cir){var service=KingdomsCombat.service;if(service!=null&&service.arenas!=null&&!service.arenas.admit(entity))cir.setReturnValue(false);}
}
