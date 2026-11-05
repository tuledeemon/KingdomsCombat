package com.celik.kingdomscombat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CombatPearlRangeTest {
    @Test void inclusiveThreeDimensionalBoundary(){assertTrue(CombatPearlRange.within(true,300,0,0));assertTrue(CombatPearlRange.within(true,0,-300,0));assertTrue(CombatPearlRange.within(true,180,0,240));assertFalse(CombatPearlRange.within(true,300.0001,0,0));assertFalse(CombatPearlRange.within(true,0,301,0));assertFalse(CombatPearlRange.within(true,213,0,213));}
    @Test void noCrossDimensionOrNonFiniteDestination(){assertFalse(CombatPearlRange.within(false,0,0,0));assertFalse(CombatPearlRange.within(true,Double.NaN,0,0));assertFalse(CombatPearlRange.within(true,Double.POSITIVE_INFINITY,0,0));}
}
