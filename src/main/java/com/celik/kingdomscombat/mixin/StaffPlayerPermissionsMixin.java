package com.celik.kingdomscombat.mixin;
import com.celik.kingdomscombat.StaffPermissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerPlayer.class) public abstract class StaffPlayerPermissionsMixin {
    @Inject(method="permissions",at=@At("HEAD"),cancellable=true) private void defaultOnly(CallbackInfoReturnable<PermissionSet> cir){var p=(ServerPlayer)(Object)this;if(StaffPermissions.restricted(p))cir.setReturnValue(StaffPermissions.defaultSet(p));}
}
