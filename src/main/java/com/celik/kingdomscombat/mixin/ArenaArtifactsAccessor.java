package com.celik.kingdomscombat.mixin;

import com.celik.kingdoms.Artifacts;
import com.celik.kingdoms.ArtifactRules;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Back up only the authenticated records carried into an own-gear match. */
@Mixin(value=Artifacts.class,remap=false)
public interface ArenaArtifactsAccessor {
    @Accessor("data") ArtifactRules.Data combat$artifactData();
    @Accessor("leases") Map<UUID,ItemStack> combat$artifactLeases();
    @Invoker("record") ArtifactRules.Record combat$artifactRecord(ItemStack stack);
    @Invoker("save") void combat$saveArtifacts();
}
