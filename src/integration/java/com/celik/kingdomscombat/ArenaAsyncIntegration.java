package com.celik.kingdomscombat;

import com.celik.kingdomscombat.ArenaRules.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Exercise the real multi-tick ticket/load/prepare/return path without fixture shortcuts. */
final class ArenaAsyncIntegration {
    static void start(MinecraftServer server,BiConsumer<Boolean,String> check,Consumer<Throwable> done)throws Exception{
        CombatService original=KingdomsCombat.service;
        var cfg=new CombatConfig();cfg.staffDefaultPermissionsWhenOff=false;cfg.arenaAutoNext=false;cfg.sounds=false;
        var file=server.getWorldPath(LevelResource.ROOT).resolve("async-arena/state.json");CombatStore.write(file.resolveSibling("arena.json"),new Data());
        long[] clock={System.currentTimeMillis()};CombatService s=new CombatService(server,cfg,file.resolveSibling("config.json"),file,()->clock[0]);KingdomsCombat.service=s;
        var level=server.overworld();int x=18000,z=18000;level.getChunk(x>>4,z>>4);
        for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++){level.setBlock(new BlockPos(x+dx,160,z+dz),Blocks.STONE.defaultBlockState(),3);for(int y=161;y<=164;y++)level.setBlock(new BlockPos(x+dx,y,z+dz),Blocks.AIR.defaultBlockState(),3);}
        ServerPlayer a=CombatIntegration.player(server,level,"AsyncArenaA",x+.5,161,z+.5),b=CombatIntegration.player(server,level,"AsyncArenaB",x+14.5,161,z+14.5);
        a.getInventory().setItem(0,new ItemStack(Items.DIAMOND_SWORD));s.arenas.kit(a);a.setPos(x,160,z);s.arenas.corner(a,1);a.setPos(x+15,164,z+15);s.arenas.corner(a,2);s.arenas.claim();a.setPos(x+2.5,161,z+2.5);s.arenas.spawn(a,1);s.arenas.spawn(b,2);
        a.setPos(x+7.5,161,z+7.5);s.arenas.setLocation(a);
        ServerPlayer visitor=CombatIntegration.player(server,level,"ArenaVisitor",19002.5,161,19002.5);
        a.getInventory().setItem(0,new ItemStack(Items.DIAMOND,3));b.getInventory().setItem(0,new ItemStack(Items.EMERALD,4));a.setPos(19000.5,161,19000.5);b.setPos(19003.5,161,19003.5);level.getChunk(19000>>4,19000>>4);
        for(int i=0;i<5;i++)for(int j=0;j<5;j++)level.setBlock(new BlockPos(19000+i,160,19000+j),Blocks.STONE.defaultBlockState(),3);
        s.arenas.open("Asynchronous arena",1);s.arenas.add(List.of(a));s.arenas.add(List.of(b));s.arenas.start();
        s.ledger.manual(visitor.getUUID(),clock[0],20);s.arenas.travel(visitor,s.arenas.data.event.id);check.accept(visitor.getX()>18900,"combat-tagged visitors cannot use the public arena teleport");s.ledger.clear(visitor.getUUID());
        check.accept(!s.allowDestination(visitor,level,x+7.5,161,z+7.5,Set.of()),"ordinary teleports into the active arena remain blocked");
        s.arenas.travel(visitor,s.arenas.data.event.id);
        int[] step={0},ticks={0};boolean[] finished={false};
        ServerTickEvents.END_SERVER_TICK.register(current->{if(finished[0]||current!=server)return;try{
            if(++ticks[0]>240)throw new AssertionError("Real asynchronous arena load/restore timed out at step "+step[0]);
            if(step[0]==0&&s.arenas.data.match!=null&&s.arenas.data.match.phase==MatchPhase.COUNTDOWN&&Math.abs(visitor.getX()-(x+7.5))<.01){check.accept(s.arenas.data.snapshots.size()==2&&a.getInventory().getItem(0).is(Items.DIAMOND_SWORD)&&a.getX()<18100,"real chunk-ticket future prepares the match and applies kits on a later server tick");check.accept(s.arenas.inside(visitor.level(),visitor.blockPosition())&&!s.arenas.reserved(visitor.getUUID())&&!s.arenas.build(visitor,level,visitor.blockPosition()),"public event teleport reaches the chosen viewing spot without granting fighter permissions");clock[0]+=cfg.arenaCountdownSeconds*1000L;step[0]=1;}
            else if(step[0]==1&&s.arenas.data.match!=null&&s.arenas.data.match.phase==MatchPhase.FIGHTING){check.accept(s.arenas.fighter(a.getUUID()),"real multi-tick countdown enters active fighting");b.invulnerableTime=0;b.hurtServer(level,a.damageSources().playerAttack(a),1000);step[0]=2;}
            else if(step[0]==2&&s.arenas.data.snapshots.isEmpty()){check.accept(s.arenas.data.event.phase==Phase.FINISHED&&a.getInventory().getItem(0).is(Items.DIAMOND)&&b.getInventory().getItem(0).is(Items.EMERALD)&&Math.abs(a.getX()-19000.5)<.01&&Math.abs(b.getX()-19003.5)<.01,"real multi-tick match restores both original inventories and return positions");finished[0]=true;s.stop();CombatIntegration.remove(server,a);CombatIntegration.remove(server,b);CombatIntegration.remove(server,visitor);KingdomsCombat.service=original;done.accept(null);}
        }catch(Throwable error){finished[0]=true;try{s.stop();CombatIntegration.remove(server,a);CombatIntegration.remove(server,b);CombatIntegration.remove(server,visitor);}catch(Throwable cleanup){error.addSuppressed(cleanup);}KingdomsCombat.service=original;done.accept(error);}});
    }
    private ArenaAsyncIntegration(){}
}
