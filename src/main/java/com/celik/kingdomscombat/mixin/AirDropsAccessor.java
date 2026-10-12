package com.celik.kingdomscombat.mixin;
import com.celik.kingdoms.AirDrops;
import com.celik.kingdoms.AirDropRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=AirDrops.class,remap=false)
public interface AirDropsAccessor { @Accessor("data") AirDropRules.Data combat$data(); }
