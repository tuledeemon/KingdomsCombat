package com.celik.kingdomscombat;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FlagRulesTest {
    private List<Long> clicks(double interval,double jitter){List<Long> list=new ArrayList<>();Random r=new Random(3);long now=0;for(int i=0;i<129;i++){list.add(now);now+=(long)((interval+(r.nextDouble()-.5)*jitter)*1e6);}return list;}
    private List<FlagRules.Step> loop(int period,int repeats){List<FlagRules.Step> list=new ArrayList<>();for(int i=0;i<period*repeats;i++)list.add(new FlagRules.Step(i%period,true,i%3==0,1));return list;}
    @Test void steadyFastClickingHasHighCps(){var s=FlagRules.clicks(clicks(33,.2));assertTrue(s.cps()>30);assertTrue(s.deviation()<.1);assertFalse(s.tickAligned());}
    @Test void humanJitterDoesNotLookMetronomic(){assertTrue(FlagRules.clicks(clicks(120,90)).deviation()>20);}
    @Test void tickQuantizedInputIsIdentified(){assertTrue(FlagRules.clicks(clicks(100,0)).tickAligned());}
    @Test void burstAndInvalidArrivalTimesAreRejected(){assertTrue(FlagRules.clicks(clicks(1,0)).burstFraction()>.9);assertEquals(1,FlagRules.clicks(List.of(10L,10L,9L)).burstFraction());}
    @Test void repeatedRichActionCyclesAreFound(){assertEquals(24,FlagRules.repeatedCycle(loop(24,5),24,120,5,.98));}
    @Test void notEnoughRepetitionsDoesNotFlag(){assertEquals(0,FlagRules.repeatedCycle(loop(24,4),24,120,5,.98));}
    @Test void idleInputCannotBecomeAMacroFlag(){assertEquals(0,FlagRules.repeatedCycle(Collections.nCopies(200,new FlagRules.Step(0,false,false,0)),24,120,5,.98));}
    @Test void straightLineOrHeldActionsDoNotFlag(){var steps=loop(24,5).stream().map(s->new FlagRules.Step(s.token(),true,false,8)).toList();assertEquals(0,FlagRules.repeatedCycle(steps,24,120,5,.98));}
    @Test void humanVariationsBreakCycleMatching(){var steps=loop(24,5);for(int i=0;i<steps.size();i+=7)steps.set(i,new FlagRules.Step(1000+i,true,true,1));assertEquals(0,FlagRules.repeatedCycle(steps,24,120,5,.98));}
    @Test void oneClickPerSecondIsEligibleForSlowTimingIncludingTickAlignedInput(){var c=new CombatConfig();assertTrue(FlagRules.slowTiming(clicks(1000,0),c));assertTrue(FlagRules.slowTiming(clicks(1000,2),c));}
    @Test void ordinaryVariationAndFasterManualClicksDoNotTriggerSlowTiming(){var c=new CombatConfig();assertFalse(FlagRules.slowTiming(clicks(1000,120),c));assertFalse(FlagRules.slowTiming(clicks(250,0),c));assertFalse(FlagRules.slowTiming(clicks(1,0),c));}
    @Test void oldConfigurationsEnableSlowTimingWithoutChangingTheExistingFourCpsGate(){var c=CombatStore.JSON.fromJson("{\"antiCheatRegularMinCps\":4}",CombatConfig.class);c.validate();assertTrue(c.antiCheatSlowTiming);assertEquals(.5,c.antiCheatSlowMinCps);assertEquals(4,c.antiCheatRegularMinCps);}
    @Test void suspicionScoresDecayAndIgnoreOldObservations(){long now=1000000000L;assertEquals(10,FlagRules.score(now,now,24));assertEquals(5,FlagRules.score(now-12*3600000L,now,24));assertEquals(0,FlagRules.score(now-25*3600000L,now,24));assertEquals(10,FlagRules.score(now+1000,now,24));}
    @Test void legacyConfigEnablesFlagOnlyDefaults(){var c=CombatStore.JSON.fromJson("{}",CombatConfig.class);c.validate();assertTrue(c.antiCheatClicks&&c.antiCheatMacros);assertEquals(500,c.antiCheatHistoryLimit);}
    @Test void unboundedOrNonfiniteSettingsAreRejected(){var c=new CombatConfig();c.antiCheatMacroMaxPeriod=10000;assertThrows(IllegalArgumentException.class,c::validate);c=new CombatConfig();c.antiCheatMaxCps=Double.NaN;assertThrows(IllegalArgumentException.class,c::validate);}
}
