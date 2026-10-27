package com.celik.kingdomscombat;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.celik.kingdomscombat.ArenaRules.*;

class ArenaRulesTest {
    private Event event(int count,int size){Event event=new Event();event.teamSize=size;for(int i=0;i<count;i++){Entry e=new Entry();e.captain=UUID.randomUUID();e.players.add(e.captain);if(size==2)e.players.add(UUID.randomUUID());event.entries.put(e.captain,e);}return event;}
    @Test void mostRecentExcessEntriesAreExcludedWithoutByes(){for(int count=2;count<=64;count++){Event event=event(count,1);List<UUID> order=new ArrayList<>(event.entries.keySet());int keep=Integer.highestOneBit(count);var excluded=trimToPowerOfTwo(event);assertEquals(order.subList(0,keep),new ArrayList<>(event.entries.keySet()));assertEquals(count-keep,excluded.size());seed(event,new Random(2));assertEquals(keep/2,event.queue.size());assertTrue(event.roundWinners.isEmpty());}}
    @Test void doublesTrimCompleteTeamsInsteadOfIndividuals(){Event event=event(7,2);trimToPowerOfTwo(event);assertEquals(4,event.entries.size());assertEquals(8,event.entries.values().stream().mapToInt(e->e.players.size()).sum());}
    @Test void manualPairingsKeepTheirOrderAndUnpairedEntriesFillAutomatically(){Event e=event(8,1);var ids=new ArrayList<>(e.entries.keySet());Pair manual=new Pair(ids.get(5),ids.get(1));e.queue.add(manual);seed(e,new Random(1));assertSame(manual,e.queue.getFirst());Set<UUID> used=new HashSet<>();for(Pair pair:e.queue){assertTrue(used.add(pair.a));assertTrue(used.add(pair.b));}assertEquals(8,used.size());}
    @Test void duplicateManualEntrantIsRejected(){Event e=event(4,1);var ids=new ArrayList<>(e.entries.keySet());e.queue.add(new Pair(ids.get(0),ids.get(1)));e.queue.add(new Pair(ids.get(0),ids.get(2)));assertThrows(IllegalArgumentException.class,()->seed(e,new Random()));}
    @Test void excludedLateTeamsAreRemovedFromManualPairs(){Event e=event(5,1);var ids=new ArrayList<>(e.entries.keySet());e.queue.add(new Pair(ids.get(0),ids.get(4)));trimToPowerOfTwo(e);seed(e,new Random());assertEquals(2,e.queue.size());assertTrue(e.queue.stream().noneMatch(p->p.a.equals(ids.get(4))||p.b.equals(ids.get(4))));}
    @Test void eloUsesExpectedResultsAndAwardsMoreForUpsets(){assertEquals(16,eloDelta(1000,1000,32,400));assertTrue(eloDelta(800,1200,32,400)>16);assertTrue(eloDelta(1200,800,32,400)<16);}
    @Test void regionHasInclusiveBlockBoundsAndRejectsDifferentDimensions(){Region r=new Region();r.dimension="minecraft:overworld";r.minX=-16;r.maxX=-1;r.minY=0;r.maxY=10;r.minZ=0;r.maxZ=15;assertTrue(r.contains(r.dimension,-.01,10.99,15.99));assertFalse(r.contains(r.dimension,0,10,15));assertFalse(r.contains("minecraft:the_nether",-1,5,5));assertEquals(2816,r.volume());}
    @Test void gearModeDefaultsToKitAndPersistsExplicitOwnGear(){Data old=CombatStore.JSON.fromJson("{}",Data.class);assertFalse(old.setup.useOwnGear);old.event=event(2,1);assertFalse(old.event.useOwnGear);old.setup.useOwnGear=true;old.event.useOwnGear=true;Data saved=CombatStore.JSON.fromJson(CombatStore.JSON.toJson(old),Data.class);assertTrue(saved.setup.useOwnGear);assertTrue(saved.event.useOwnGear);}
    @Test void sharedLadderAndNewSettingsRoundTrip(){CombatConfig config=CombatStore.JSON.fromJson("{}",CombatConfig.class);config.validate();assertEquals(1000,config.arenaEloStart);assertTrue(config.staffMuteVoice);assertTrue(config.arenaAllowedCommands.contains("tournament"));config.arenaMaxTeams=1;assertThrows(IllegalArgumentException.class,config::validate);}
}
