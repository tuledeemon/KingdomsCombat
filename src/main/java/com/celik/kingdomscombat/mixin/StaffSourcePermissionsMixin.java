package com.celik.kingdomscombat.mixin;
import com.celik.kingdomscombat.StaffPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(CommandSourceStack.class) public abstract class StaffSourcePermissionsMixin {
    @Inject(method="permissions",at=@At("HEAD"),cancellable=true) private void defaultOnly(CallbackInfoReturnable<PermissionSet> cir){var p=((CommandSourceStack)(Object)this).getPlayer();if(p!=null&&StaffPermissions.restricted(p))cir.setReturnValue(StaffPermissions.defaultSet(p));}
}
