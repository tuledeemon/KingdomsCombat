package com.celik.kingdomscombat.mixin;
import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Set;
@Mixin(ServerPlayer.class) public abstract class StaffTeleportMixin {
    private boolean blocked(){var s=KingdomsCombat.service;return s!=null&&s.staff.frozen(((ServerPlayer)(Object)this).getUUID())&&!s.staff.internalMove();}
    @Inject(method="teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",at=@At("HEAD"),cancellable=true) private void teleport(ServerLevel level,double x,double y,double z,Set<Relative> relative,float yaw,float pitch,boolean reset,CallbackInfoReturnable<Boolean> cir){var s=KingdomsCombat.service;if(blocked()||s!=null&&!s.allowDestination((ServerPlayer)(Object)this,level,x,y,z,relative))cir.setReturnValue(false);}
    @Inject(method="teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",at=@At("HEAD"),cancellable=true) private void dimension(TeleportTransition transition,CallbackInfoReturnable<ServerPlayer> cir){var s=KingdomsCombat.service;var p=transition.position();if(blocked()||s!=null&&!s.allowDestination((ServerPlayer)(Object)this,transition.newLevel(),p.x,p.y,p.z,transition.relatives()))cir.setReturnValue(null);}
}
