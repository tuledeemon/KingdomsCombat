package com.celik.kingdomscombat;

/** The limit is teleport displacement, including height, rather than projectile travel. */
public final class CombatPearlRange {
    public static final int MAX_DISTANCE=300;
    public static boolean within(boolean sameDimension,double dx,double dy,double dz){
        return sameDimension&&Double.isFinite(dx)&&Double.isFinite(dy)&&Double.isFinite(dz)&&dx*dx+dy*dy+dz*dz<=MAX_DISTANCE*MAX_DISTANCE;
    }
    private CombatPearlRange(){}
}
