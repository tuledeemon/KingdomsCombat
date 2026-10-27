package com.celik.kingdomscombat.mixin;
import com.celik.kingdoms.KingdomService;
import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=KingdomService.class,remap=false)
public abstract class ArenaClaimMixin {
    @Inject(method="create",at=@At("HEAD"))
    private void create(ServerPlayer player,String name,CallbackInfo ci){check(player);}
    @Inject(method={"claim","outpost"},at=@At("HEAD"))
    private void claim(ServerPlayer player,CallbackInfo ci){check(player);}
    private static void check(ServerPlayer p){var service=KingdomsCombat.service;if(service!=null&&service.arenas.overlapsChunk(p.level(),p.blockPosition().getX()>>4,p.blockPosition().getZ()>>4))throw new IllegalArgumentException("This chunk overlaps a protected event arena.");}
}
