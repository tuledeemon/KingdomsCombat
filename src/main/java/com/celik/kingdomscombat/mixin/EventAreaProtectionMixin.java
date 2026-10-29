package com.celik.kingdomscombat.mixin;

import com.celik.kingdoms.*;
import com.celik.kingdomscombat.*;
import com.celik.kingdomscombat.EventAreaRules.Flag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;

@Mixin(value=Protection.class,remap=false,priority=900)
public abstract class EventAreaProtectionMixin {
    private static EventAreas areas(){var s=KingdomsCombat.service;return s==null?null:s.areas;}
    @Inject(method="permitted",at=@At("HEAD"),cancellable=true)
    private static void permission(Level level,BlockPos pos,UUID actor,Rules.Permission permission,CallbackInfoReturnable<Boolean> cir){var a=areas();if(a!=null&&!a.permission(level,pos,actor,permission))cir.setReturnValue(false);}
    @Inject(method="mutation",at=@At("HEAD"),cancellable=true)
    private static void mutation(Level level,BlockPos pos,BlockState next,CallbackInfoReturnable<Boolean> cir){var a=areas();if(a!=null&&!a.mutation(level,pos,next))cir.setReturnValue(false);}
    @Inject(method="fluid",at=@At("HEAD"),cancellable=true)
    private static void fluid(Level level,BlockPos from,BlockPos to,CallbackInfoReturnable<Boolean> cir){var a=areas();if(a!=null&&!a.edge(level,from,to,Flag.FLUIDS))cir.setReturnValue(false);}
    @Inject(method="piston(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Z)Z",at=@At("HEAD"),cancellable=true)
    private static void piston(Level level,BlockPos from,BlockPos to,boolean wilderness,CallbackInfoReturnable<Boolean> cir){var a=areas();if(a!=null&&!a.edge(level,from,to,Flag.PISTONS))cir.setReturnValue(false);}
    @Inject(method="automation",at=@At("HEAD"),cancellable=true)
    private static void automation(Level level,BlockPos from,BlockPos to,CallbackInfoReturnable<Boolean> cir){var a=areas();if(a!=null&&!a.edge(level,from,to,Flag.CONTAINERS))cir.setReturnValue(false);}
    @Inject(method="explosion",at=@At("HEAD"),cancellable=true)
    private static void explosion(Level level,BlockPos origin,BlockPos target,UUID actor,CallbackInfoReturnable<Boolean> cir){var a=areas();if(a!=null&&!a.allows(level,target,Flag.EXPLOSIONS))cir.setReturnValue(false);}
    @Inject(method="damage",at=@At("HEAD"),cancellable=true)
    private static void damage(Entity target,DamageSource source,CallbackInfoReturnable<Boolean> cir){var a=areas();if(a!=null&&!a.damage(target,source))cir.setReturnValue(false);}
    @Inject(method="admitSpawn",at=@At("HEAD"),cancellable=true)
    private static void spawn(Entity entity,CallbackInfoReturnable<Boolean> cir){var a=areas();if(a!=null&&!a.admit(entity))cir.setReturnValue(false);}
    @Inject(method="flag",at=@At("HEAD"),cancellable=true)
    private static void flag(Level level,BlockPos pos,Rules.Flag flag,CallbackInfoReturnable<Boolean> cir){var a=areas();if(a==null)return;Flag mapped=switch(flag){case FIRE->Flag.FIRE;case MOB_GRIEFING->Flag.MOB_GRIEFING;case PVP->Flag.PVP;default->null;};if(mapped!=null&&!a.allows(level,pos,mapped))cir.setReturnValue(false);}
    @Inject(method="environmental",at=@At("HEAD"),cancellable=true)
    private static void environmental(Level level,BlockPos from,BlockPos to,Rules.Flag flag,CallbackInfoReturnable<Boolean> cir){var a=areas();if(a==null)return;if(flag==Rules.Flag.FIRE&&(!a.allows(level,from,Flag.FIRE)||!a.allows(level,to,Flag.FIRE))||flag==Rules.Flag.MOB_GRIEFING&&!a.allows(level,to,Flag.MOB_GRIEFING))cir.setReturnValue(false);}
}
