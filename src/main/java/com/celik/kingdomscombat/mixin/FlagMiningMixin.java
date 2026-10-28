package com.celik.kingdomscombat.mixin;

import com.celik.kingdomscombat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

@Mixin(ServerPlayerGameMode.class)
public abstract class FlagMiningMixin {
    @Shadow @Final protected ServerPlayer player;
    @Inject(method="destroyBlock",at=@At("HEAD"))
    private void before(BlockPos pos,CallbackInfoReturnable<Boolean> cir){var s=KingdomsCombat.service;if(s!=null)s.flags.observe(()->s.flags.mining(player));}
    @Inject(method="destroyBlock",at=@At("RETURN"))
    private void after(BlockPos pos,CallbackInfoReturnable<Boolean> cir){var s=KingdomsCombat.service;if(s!=null&&cir.getReturnValue())s.flags.observe(()->s.flags.mined(player));}
}
