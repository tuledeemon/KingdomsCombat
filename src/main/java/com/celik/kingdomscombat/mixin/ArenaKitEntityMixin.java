package com.celik.kingdomscombat.mixin;
import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Temporary projectiles/loot cannot return to normal inventories after their match or a restart. */
@Mixin(Entity.class)
public abstract class ArenaKitEntityMixin {
    @Inject(method="baseTick",at=@At("HEAD"),cancellable=true)
    private void arenaExpiry(CallbackInfo ci){Entity self=(Entity)(Object)this;var service=KingdomsCombat.service;if(service!=null&&self.entityTags().contains("kingdomscombat_arena")&&!service.arenas.entityActive(self)){self.discard();ci.cancel();}}
}
