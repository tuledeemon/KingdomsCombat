package com.celik.kingdomscombat.mixin;

import com.celik.kingdomscombat.SwingArrival;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import org.spongepowered.asm.mixin.*;

@Mixin(ServerboundSwingPacket.class)
public abstract class FlagSwingPacketMixin implements SwingArrival {
    @Unique private final long combat$arrival=System.nanoTime();
    @Override public long combat$arrivalNanos(){return combat$arrival;}
}
