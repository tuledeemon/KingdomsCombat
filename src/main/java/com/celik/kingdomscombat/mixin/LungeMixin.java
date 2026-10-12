package com.celik.kingdomscombat.mixin;
import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.item.enchantment.effects.ApplyEntityImpulse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ApplyEntityImpulse.class)
public abstract class LungeMixin {
    @Inject(method="apply",at=@At("RETURN"))
    private void lunged(ServerLevel level,int enchantmentLevel,EnchantedItemInUse item,Entity target,Vec3 pos,CallbackInfo ci){
        if(target instanceof ServerPlayer p&&target==item.owner()&&KingdomsCombat.service!=null&&item.itemStack().has(DataComponents.PIERCING_WEAPON)&&EnchantmentHelper.getItemEnchantmentLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LUNGE),item.itemStack())>0)KingdomsCombat.service.movement(p,"spear_lunge");
    }
}
