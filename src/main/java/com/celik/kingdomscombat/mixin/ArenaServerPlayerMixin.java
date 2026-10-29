package com.celik.kingdomscombat.mixin;
import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerPlayer.class)
public abstract class ArenaServerPlayerMixin {
    // Fabric ALLOW_DEATH fires before the vanilla totem check. Intercept actual death instead.
    @Inject(method="die",at=@At("HEAD"),cancellable=true)
    private void defeat(DamageSource source,CallbackInfo ci){
        ServerPlayer p=(ServerPlayer)(Object)this;
        if(KingdomsCombat.service!=null)KingdomsCombat.service.sellMarket.beforeDamage(p);
        if(KingdomsCombat.service==null||!KingdomsCombat.service.arenas.reserved(p.getUUID()))return;
        try{KingdomsCombat.service.arenas.defeat(p,source);}
        catch(RuntimeException error){p.setHealth(1);KingdomsCombat.LOG.error("Arena defeat paused; original inventory backup retained for {}",p.getUUID(),error);}
        ci.cancel();
    }
    @Inject(method="drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",at=@At("HEAD"),cancellable=true)
    private void drop(ItemStack stack,boolean random,boolean trace,CallbackInfoReturnable<ItemEntity> cir){if(KingdomsCombat.service!=null&&KingdomsCombat.service.arenas.reserved(((ServerPlayer)(Object)this).getUUID()))cir.setReturnValue(null);}
}
