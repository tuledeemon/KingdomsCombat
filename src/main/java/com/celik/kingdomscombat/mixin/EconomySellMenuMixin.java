package com.celik.kingdomscombat.mixin;

import com.celik.kingdomscombat.SellMarket;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.sell.SellUi;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=SellUi.class,remap=false)
public abstract class EconomySellMenuMixin {
    @Inject(method="open",at=@At("HEAD"),cancellable=true)
    private static void open(ServerPlayer player,EconomyManager economy,CallbackInfo ci){if(SellMarket.redirect(player))ci.cancel();}
}
