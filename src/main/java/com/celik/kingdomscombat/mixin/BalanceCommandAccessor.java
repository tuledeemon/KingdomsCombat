package com.celik.kingdomscombat.mixin;

import com.mojang.brigadier.tree.CommandNode;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=CommandNode.class,remap=false)
public interface BalanceCommandAccessor {
    @Accessor("children") Map<String,?> combat$children();
    @Accessor("literals") Map<String,?> combat$literals();
    @Accessor("arguments") Map<String,?> combat$arguments();
}
