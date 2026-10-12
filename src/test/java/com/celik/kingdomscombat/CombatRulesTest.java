package com.celik.kingdomscombat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import java.util.*;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class CombatRulesTest {
    private final UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
    private final CombatConfig config=new CombatConfig();
    private final TagLedger ledger=new TagLedger(new TagLedger.Data());
    @Test void damageTagsBothParticipants(){ledger.hit(a,b,1000,config);assertEquals(20,ledger.status(a,1000).seconds());assertEquals(Set.of(a),ledger.status(b,1000).opponents());}
    @Test void selfDamageDoesNotTag(){ledger.hit(a,a,1000,config);assertFalse(ledger.status(a,1000).active());}
    @Test void movementRefreshIsDirectional(){ledger.hit(a,b,1000,config);ledger.refreshSelf(a,11000,config);assertEquals(20,ledger.status(a,11000).seconds());assertEquals(10,ledger.status(b,11000).seconds());}
    @Test void movementCannotStartFreshCombat(){assertFalse(ledger.refreshSelf(a,1000,config));assertFalse(ledger.status(a,1000).active());}
    @Test void movementCannotReviveExpiredCombat(){ledger.hit(a,b,1000,config);assertFalse(ledger.refreshSelf(a,21000,config));assertFalse(ledger.status(a,21000).active());}
    @Test void killingOneEnemyKeepsOtherFight(){ledger.hit(a,b,1000,config);ledger.hit(a,c,2000,config);ledger.death(b,a,config);assertEquals(Set.of(c),ledger.status(a,3000).opponents());}
    @Test void killingOnlyEnemyEndsCombat(){ledger.hit(a,b,1000,config);ledger.death(b,a,config);assertEquals(0,ledger.status(a,2000).combatMillis());}
    @Test void optionalKillCanClearAllCombat(){config.killClearsAllOpponents=true;ledger.hit(a,b,1000,config);ledger.hit(a,c,2000,config);ledger.death(b,a,config);assertEquals(0,ledger.status(a,3000).combatMillis());}
    @Test void killingOpponentDoesNotEraseWarRestriction(){ledger.hit(a,b,1000,config);ledger.zone(a,TagLedger.Reason.WAR,1000,20);ledger.death(b,a,config);assertEquals(Set.of(TagLedger.Reason.WAR),ledger.status(a,2000).reasons());}
    @Test void hitCanTagVictimOnly(){config.tagAttacker=false;ledger.hit(a,b,1000,config);assertFalse(ledger.status(a,1000).active());assertTrue(ledger.status(b,1000).active());}
    @Test void zoneRefreshKeepsTwentySecondsThenExpires(){ledger.zone(a,TagLedger.Reason.WAR,1000,20);ledger.zone(a,TagLedger.Reason.WAR,11000,20);assertEquals(20,ledger.status(a,11000).seconds());assertEquals(10,ledger.status(a,21000).seconds());assertFalse(ledger.status(a,31000).active());}
    @Test void eachReasonHasIndependentTimer(){ledger.hit(a,b,1000,config);ledger.zone(a,TagLedger.Reason.AIRDROP,1000,35);assertEquals(35,ledger.status(a,1000).seconds());assertEquals(0,ledger.status(a,22000).combatMillis());assertEquals(14000,ledger.status(a,22000).airDropMillis());}
    @Test void zoneMovementRefreshDoesNotChangeOpponent(){ledger.hit(a,b,1000,config);ledger.zone(a,TagLedger.Reason.WAR,1000,20);ledger.refreshSelf(a,15000,config);assertEquals(20,ledger.status(a,15000).seconds());assertEquals(6,ledger.status(b,15000).seconds());}
    @Test void logoutPenaltyDefaultsToCombatOnly(){ledger.zone(a,TagLedger.Reason.WAR,1000,20);ledger.zone(a,TagLedger.Reason.AIRDROP,1000,20);assertFalse(TagLedger.penalty(ledger.status(a,1000),config));ledger.hit(a,b,1000,config);assertTrue(TagLedger.penalty(ledger.status(a,1000),config));}
    @Test void warLogoutPenaltyIsConfigurable(){config.killOnWarLogout=true;ledger.zone(a,TagLedger.Reason.WAR,1000,20);assertTrue(TagLedger.penalty(ledger.status(a,1000),config));}
    @Test void airDropLogoutPenaltyIsConfigurable(){config.killOnAirDropLogout=true;ledger.zone(a,TagLedger.Reason.AIRDROP,1000,20);assertTrue(TagLedger.penalty(ledger.status(a,1000),config));}
    @Test void clearRemovesOpponentReferences(){ledger.hit(a,b,1000,config);ledger.clear(a);assertFalse(ledger.status(a,1000).active());assertFalse(ledger.status(b,1000).active());}
    @Test void expiredTagsArePruned(){ledger.hit(a,b,1000,config);ledger.prune(21000);assertTrue(ledger.data.tags.isEmpty());}
    @Test void partialSecondsRoundUp(){ledger.manual(a,1000,20);assertEquals(1,ledger.status(a,20999).seconds());assertEquals(0,ledger.status(a,21000).seconds());}
    @Test void highAltitudeCannotBypassDefaultDropRadius(){assertTrue(TagLedger.near("overworld",100,1000,0,"overworld",0,60,0,config));}
    @Test void dropRadiusBoundaryIsInclusive(){assertTrue(TagLedger.near("overworld",100,60,0,"overworld",0,60,0,config));assertFalse(TagLedger.near("overworld",100.001,60,0,"overworld",0,60,0,config));}
    @Test void dropDimensionMatters(){assertFalse(TagLedger.near("nether",0,60,0,"overworld",0,60,0,config));}
    @Test void sphereAndHeightLimitAreOptional(){config.horizontalAirDropRadius=false;assertFalse(TagLedger.near("overworld",0,161,0,"overworld",0,60,0,config));config.horizontalAirDropRadius=true;config.airDropVerticalTolerance=3;assertFalse(TagLedger.near("overworld",0,64,0,"overworld",0,60,0,config));}
    @Test void persistencePreservesRealDeadlinesAndPenaltyReceipts(@TempDir Path dir){ledger.hit(a,b,1000,config);ledger.zone(a,TagLedger.Reason.WAR,1000,20);UUID receipt=UUID.randomUUID();ledger.data.logoutPenalties.put(a,receipt);Path file=dir.resolve("state.json");CombatStore.write(file,ledger.data);TagLedger restored=new TagLedger(CombatStore.read(file,TagLedger.Data.class,null));assertEquals(10,restored.status(a,11000).seconds());assertEquals(receipt,restored.data.logoutPenalties.get(a));assertFalse(restored.status(a,21000).active());}
    @Test void invalidStateFailsWithoutWiping(){var d=new TagLedger.Data();d.schemaVersion=2;assertThrows(IllegalStateException.class,()->new TagLedger(d));}
    @Test void configurationRejectsUnsafeNumbers(){config.airDropRadius=Double.NaN;assertThrows(IllegalArgumentException.class,config::validate);config.airDropRadius=100;config.durationSeconds=0;assertThrows(IllegalArgumentException.class,config::validate);}
    @ParameterizedTest @ValueSource(strings={"/home","home bed","/HOME bed","/huskhomes:home bed","minecraft:tp 0 64 0","/publichome Player.base","/tpyes","/warp shop","/rtp","/back"}) void defaultTeleportCommandsAreBlocked(String command){assertTrue(CommandRules.matches(command,config.blockedCommands));}
    @ParameterizedTest @ValueSource(strings={"homelist","homes","sethome base","delhome base","tpdecline","tpignore","say home","combat","combatadmin clear Player"}) void nonTeleportCommandsStayAvailable(String command){assertFalse(CommandRules.matches(command,config.blockedCommands));}
    @Test void commandPathAndWildcardSettingsWork(){assertTrue(CommandRules.matches("myplugin travel home",List.of("myplugin travel")));assertFalse(CommandRules.matches("myplugin status",List.of("myplugin travel")));assertTrue(CommandRules.matches("example:warp base",List.of("example:*")));}
    @Test void removingCommandRuleAllowsIt(){config.blockedCommands.remove("home");assertFalse(CommandRules.matches("home base",config.blockedCommands));}
    @ParameterizedTest @ValueSource(strings={"600","10m","10M","1.5m","1h30m","30s","1d"}) void eventDurationAliasesWork(String time){assertTrue(HostedEventRules.seconds(time,86400)>0);}
    @Test void eventDurationUsesActualUnits(){assertEquals(600,HostedEventRules.seconds("10m",86400));assertEquals(90,HostedEventRules.seconds("1.5m",86400));assertEquals(5400,HostedEventRules.seconds("1h30m",86400));}
    @ParameterizedTest @ValueSource(strings={"","0","-1m","1x","NaN","1m garbage","2d"}) void invalidEventDurationsAreRejected(String time){assertThrows(IllegalArgumentException.class,()->HostedEventRules.seconds(time,86400));}
    @Test void invalidEventSettingsAreRejected(){config.eventMaxSeconds=0;assertThrows(IllegalArgumentException.class,config::validate);config.eventMaxSeconds=86400;config.eventBossBarColor="missing";assertThrows(IllegalArgumentException.class,config::validate);}
}
