package com.celik.kingdomscombat.mixin;

import com.celik.kingdomscombat.SellMarket;
import com.reazip.economycraft.PriceRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="com.reazip.economycraft.shop.ShopUi$ItemMenu",remap=false)
public abstract class EconomyShopSellMixin {
    @Shadow @Final private ServerPlayer viewer;
    @Inject(method="handleSell",at=@At("HEAD"),cancellable=true)
    private void sell(PriceRegistry.PriceEntry entry,int requested,CallbackInfo ci){if(SellMarket.redirect(viewer))ci.cancel();}
}
