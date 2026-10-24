package com.celik.kingdomscombat.mixin;
import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.server.players.*;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Optional;
@Mixin(PlayerList.class) public abstract class StaffOperatorMixin {
    @Inject(method="op(Lnet/minecraft/server/players/NameAndId;Ljava/util/Optional;Ljava/util/Optional;)V",at=@At("TAIL")) private void op(NameAndId id,Optional<LevelBasedPermissionSet> permissions,Optional<Boolean> bypass,CallbackInfo ci){refresh(id);}
    @Inject(method="deop",at=@At("TAIL")) private void deop(NameAndId id,CallbackInfo ci){refresh(id);}
    private void refresh(NameAndId id){var s=KingdomsCombat.service;if(s!=null){var p=s.server.getPlayerList().getPlayer(id.id());if(p!=null){s.permissions.refreshOperator(p);s.permissions.refreshCommands(p);}}}
}
