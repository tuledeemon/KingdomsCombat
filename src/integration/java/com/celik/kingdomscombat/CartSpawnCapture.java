package com.celik.kingdomscombat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import java.util.*;
/** Native spawn results remain observable while asynchronous chunks become queryable. */
public final class CartSpawnCapture {
    public record Attempt(AbstractMinecart cart,boolean accepted) {}
    public static boolean enabled;
    public static final List<Attempt> attempts=new ArrayList<>();
    private CartSpawnCapture(){}
}
