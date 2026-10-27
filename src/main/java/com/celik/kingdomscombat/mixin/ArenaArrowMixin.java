package com.celik.kingdomscombat.mixin;
import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractArrow.class)
public abstract class ArenaArrowMixin {
    @Inject(method="playerTouch",at=@At("HEAD"),cancellable=true)
    private void arenaPickup(Player player,CallbackInfo ci){var service=KingdomsCombat.service;AbstractArrow arrow=(AbstractArrow)(Object)this;if(service!=null&&arrow.entityTags().contains("kingdomscombat_arena")&&(!service.arenas.entityActive(arrow)||!service.arenas.fighter(player.getUUID())||!service.arenas.inside(arrow.level(),arrow.blockPosition())))ci.cancel();}
}
