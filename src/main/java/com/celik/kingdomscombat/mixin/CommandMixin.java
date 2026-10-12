package com.celik.kingdomscombat.mixin;
import com.celik.kingdomscombat.KingdomsCombat;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Commands.class)
public abstract class CommandMixin {
    @Inject(method="performCommand",at=@At("HEAD"),cancellable=true)
    private void restrict(ParseResults<CommandSourceStack> parsed,String raw,CallbackInfo ci){if(KingdomsCombat.service!=null&&!KingdomsCombat.service.command(parsed,raw))ci.cancel();}
}
