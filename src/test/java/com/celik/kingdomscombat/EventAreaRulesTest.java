package com.celik.kingdomscombat;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EventAreaRulesTest {
    private EventAreaRules.Area area(String dim,int x,int y,int z,int x2,int y2,int z2){var a=new EventAreaRules.Area();a.name="Test";var r=new ArenaRules.Region();r.dimension=dim;r.minX=x;r.minY=y;r.minZ=z;r.maxX=x2;r.maxY=y2;r.maxZ=z2;a.region=r;return a;}
    @Test void exactInclusiveCornersDoNotProtectTheRestOfAChunk(){var a=area("minecraft:overworld",1,60,2,4,70,5);var index=new EventAreaRules.Index(List.of(a));assertSame(a,index.at("minecraft:overworld",1,60,2));assertSame(a,index.at("minecraft:overworld",4,70,5));assertNull(index.at("minecraft:overworld",5,70,5));assertNull(index.at("minecraft:overworld",1,59,2));assertNull(index.at("minecraft:the_nether",1,60,2));}
    @Test void flagsBelongToEachArea(){var a=new EventAreaRules.Area();var b=new EventAreaRules.Area();a.flags.put(EventAreaRules.Flag.PVP,false);assertFalse(a.allows(EventAreaRules.Flag.PVP));assertTrue(b.allows(EventAreaRules.Flag.PVP));assertFalse(b.allows(EventAreaRules.Flag.BREAK));}
    @Test void fullWorldSizedAreasDoNotExpandIntoMillionsOfChunks(){var a=area("minecraft:overworld",-30000000,-64,-30000000,30000000,319,30000000);var index=new EventAreaRules.Index(List.of(a));assertSame(a,index.at("minecraft:overworld",20000000,60,-20000000));assertTrue(index.chunk("minecraft:overworld",1250000,-1250000));}
    @Test void thousandsOfIndependentAreasRemainQueryable(){var all=new ArrayList<EventAreaRules.Area>();for(int i=0;i<4096;i++)all.add(area("minecraft:overworld",i*32,0,0,i*32+3,10,3));var index=new EventAreaRules.Index(all);for(int i:new int[]{0,2047,4095})assertSame(all.get(i),index.at("minecraft:overworld",i*32,0,0));assertNull(index.at("minecraft:overworld",400,20,0));}
    @Test void verticallySeparateAreasAndNegativeChunksAreSupported(){var a=area("minecraft:overworld",-17,-10,-17,-16,0,-16);var b=area("minecraft:overworld",-17,1,-17,-16,10,-16);assertFalse(EventAreaRules.intersects(a.region,b.region));var index=new EventAreaRules.Index(List.of(a,b));assertSame(b,index.at("minecraft:overworld",-16,5,-16));assertTrue(index.chunk("minecraft:overworld",-2,-2));assertFalse(index.chunk("minecraft:overworld",0,0));}
    @Test void corruptBoundsAreRejectedAndFlagsPersist(){var a=area("minecraft:overworld",0,0,0,4,4,4);var d=new EventAreaRules.Data();d.areas.put(a.id,a);a.flags.put(EventAreaRules.Flag.INTERACT,false);var roundtrip=CombatStore.JSON.fromJson(CombatStore.JSON.toJson(d),EventAreaRules.Data.class);EventAreaRules.validate(roundtrip);assertFalse(roundtrip.areas.get(a.id).allows(EventAreaRules.Flag.INTERACT));a.region.maxX=-1;assertThrows(IllegalStateException.class,()->EventAreaRules.validate(d));}
}
