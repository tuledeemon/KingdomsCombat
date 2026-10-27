package com.celik.kingdomscombat.mixin;
import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemEntity.class)
public abstract class ArenaPickupMixin {
    @Inject(method="playerTouch",at=@At("HEAD"),cancellable=true)
    private void pickup(Player player,CallbackInfo ci){var service=KingdomsCombat.service;if(service!=null&&player instanceof ServerPlayer p&&(service.arenas.reserved(p.getUUID())||service.arenas.inside(p.level(),p.blockPosition())&&!service.arenas.admin(p)))ci.cancel();}
}
