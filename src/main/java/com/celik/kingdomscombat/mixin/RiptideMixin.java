package com.celik.kingdomscombat.mixin;

import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TridentItem.class)
public abstract class RiptideMixin {
    private static boolean blocked(Player player,ItemStack stack){return player instanceof ServerPlayer p&&KingdomsCombat.service!=null&&EnchantmentHelper.getTridentSpinAttackStrength(stack,p)>0&&KingdomsCombat.service.riptideBlocked(p);}
    @Inject(method="use",at=@At("HEAD"),cancellable=true)
    private void use(Level level,Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResult> cir){if(blocked(player,player.getItemInHand(hand)))cir.setReturnValue(InteractionResult.FAIL);}
    @Inject(method="releaseUsing",at=@At("HEAD"),cancellable=true)
    private void release(ItemStack stack,Level level,LivingEntity entity,int remaining,CallbackInfoReturnable<Boolean> cir){if(entity instanceof Player player&&blocked(player,stack))cir.setReturnValue(false);}
    @Inject(method="releaseUsing",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;startAutoSpinAttack(IFLnet/minecraft/world/item/ItemStack;)V",shift=At.Shift.AFTER))
    private void launched(ItemStack stack,Level level,LivingEntity entity,int remaining,CallbackInfoReturnable<Boolean> cir){if(entity instanceof ServerPlayer p&&KingdomsCombat.service!=null)KingdomsCombat.service.riptide(p);}
}
