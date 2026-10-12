package com.celik.kingdomscombat.mixin;
import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin({EnderpearlItem.class,WindChargeItem.class})
public abstract class MovementItemMixin {
    @Inject(method="use",at=@At("RETURN"))
    private void used(Level level,Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResult> cir){
        if(player instanceof ServerPlayer p&&KingdomsCombat.service!=null&&cir.getReturnValue().consumesAction())KingdomsCombat.service.movement(p,(Object)this instanceof EnderpearlItem?"ender_pearl":"wind_charge");
    }
}
