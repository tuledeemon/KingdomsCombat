package com.celik.kingdomscombat;
import com.celik.kingdoms.AirDropRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import java.util.*;
import java.util.function.BiConsumer;

final class AirDropArrivalIntegration {
    static void run(CombatService s,ServerPlayer player,AirDropRules.Drop drop,BiConsumer<Boolean,String> check){
        var origin=player.position();var level=player.level();var phase=drop.phase;String dimension=drop.dimension;
        try{
            player.setPos(drop.x+201,drop.y,drop.z+.5);var outside=player.position();s.ledger.clear(player.getUUID());
            check.accept(s.allowTeleport(player,false),"untagged player outside drop remains eligible for ordinary teleport commands");
            check.accept(!player.teleportTo(level,drop.x+.5,drop.y,drop.z+.5,Set.of(),0,0,false)&&player.position().equals(outside),"native server teleport rejects arrival in an incoming AirDrop area");
            player.connection.teleport(drop.x+.5,drop.y,drop.z+.5,0,0);
            check.accept(player.position().equals(outside),"direct connection teleport cannot bypass the AirDrop destination guard");
            check.accept(!player.teleportTo(level,drop.x+.5-player.getX(),0,0,Set.of(Relative.X,Relative.Y,Relative.Z),0,0,false),"relative teleport coordinates resolve before AirDrop destination checks");
            check.accept(s.allowDestination(player,level,drop.x+100.501,drop.y,drop.z+.5,Set.of())&&!s.allowDestination(player,level,drop.x+100.5,drop.y,drop.z+.5,Set.of()),"incoming guard uses the same inclusive configured radius as exposure tags");
            drop.phase=AirDropRules.Phase.CAPTURABLE;
            check.accept(!s.allowDestination(player,level,drop.x+.5,drop.y,drop.z+.5,Set.of()),"landed unclaimed AirDrops still reject inbound teleports");
            player.setPos(drop.x+.5,drop.y,drop.z+.5);
            check.accept(s.allowDestination(player,level,drop.x+2,drop.y,drop.z+.5,Set.of()),"destination guard permits local movement within the same drop area");
            player.setPos(outside);drop.phase=AirDropRules.Phase.CLAIMED;
            check.accept(s.allowDestination(player,level,drop.x+.5,drop.y,drop.z+.5,Set.of()),"winning an AirDrop immediately removes the inbound destination restriction");
            drop.phase=AirDropRules.Phase.DESCENDING;s.config.airDropBlockIncomingTeleports=false;
            check.accept(s.allowDestination(player,level,drop.x+.5,drop.y,drop.z+.5,Set.of()),"incoming teleport guard is independently configurable");s.config.airDropBlockIncomingTeleports=true;
            drop.dimension="minecraft:the_nether";
            check.accept(s.allowDestination(player,level,drop.x+.5,drop.y,drop.z+.5,Set.of()),"matching destination coordinates in another dimension are allowed");
            var nether=s.server.getLevel(net.minecraft.world.level.Level.NETHER);
            if(nether!=null)check.accept(!player.teleportTo(nether,drop.x+.5,drop.y,drop.z+.5,Set.of(),0,0,false)&&player.level()==level,"cross-dimension teleport is denied before destination chunk loading");
        }finally{drop.phase=phase;drop.dimension=dimension;s.config.airDropBlockIncomingTeleports=true;player.setPos(origin);s.ledger.clear(player.getUUID());}
    }
}
