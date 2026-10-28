package com.celik.kingdomscombat;

import com.celik.kingdoms.KingdomsMod;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.*;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.function.BiConsumer;

final class FlagIntegration {
    static void run(CombatService s,long[] time,BiConsumer<Boolean,String> check)throws Exception{
        var originalConfig=s.config;var saved=CombatStore.JSON.fromJson(CombatStore.JSON.toJson(s.flags.data),FlagService.Data.class);var c=CombatStore.JSON.fromJson(CombatStore.JSON.toJson(s.config),CombatConfig.class);s.config=c;
        var l=s.server.overworld();var p=CombatIntegration.player(s.server,l,"FlagSubject",18000.5,57,18000.5);var host=CombatIntegration.player(s.server,l,"FlagReviewer",18005.5,57,18000.5);
        try{
            s.flags.data.flags.clear();s.flags.reset();c.antiCheatClickSamples=32;c.antiCheatClickWindows=2;
            s.flags.action(p,1);long base=System.nanoTime()-65*33_000_000L;for(int i=0;i<65;i++)s.flags.swing(p,base+i*33_000_000L);
            var packet=new ServerboundSwingPacket(InteractionHand.MAIN_HAND);check.accept(((SwingArrival)packet).combat$arrivalNanos()>0,"incoming swing packet carries a monotonic timestamp without reading world state on networking threads");p.connection.handleAnimate(packet);
            check.accept(s.flags.history(p.getUUID()).size()==1&&p.isAlive()&&p.getHealth()==20,"native swing handler completes sustained click evidence without damaging or punishing the player");
            s.flags.flag(p,"Fast clicking","repeat");s.flags.leave(p.getUUID());s.flags.flag(p,"Fast clicking","repeat after reconnect");check.accept(s.flags.history(p.getUUID()).size()==1,"per-check alert cooldown survives observation resets and reconnects");
            s.flags.save();check.accept(new FlagService(s).history(p.getUUID()).size()==1,"suspicion history persists through service restart");
            time[0]+=(c.antiCheatAlertCooldownSeconds+1)*1000L;s.flags.action(p,1);s.flags.miningAction(p,true);long timing=System.nanoTime();for(int i=0;i<80;i++)s.flags.swing(p,timing+i*33_000_000L);check.accept(s.flags.history(p.getUUID()).size()==1,"ordinary ongoing block mining is excluded from clicking flags even after cooldown expires");s.flags.leave(p.getUUID());
            for(int window=0;window<2;window++){s.flags.action(p,1);for(int i=0;i<33;i++)s.flags.swing(p,timing+window*3_000_000_000L+i*33_000_000L);}check.accept(s.flags.history(p.getUUID()).size()==1,"separated short clicking sessions cannot accumulate consecutive-window evidence across long pauses");

            check.accept(!s.flags.receives(p)&&!s.flags.receives(host),"normal players and staff outside staff mode receive no suspicion alerts");
            s.server.getPlayerList().op(host.nameAndId(),Optional.of(LevelBasedPermissionSet.OWNER),Optional.empty());s.permissions.join(host);s.toggleStaffMode(host);
            check.accept(s.flags.receives(host)&&!s.flags.eligible(host),"active authorized staff receive alerts and are exempt from detector sampling");
            check.accept(!s.flags.toggleAlerts(host)&&!s.flags.receives(host)&&s.flags.toggleAlerts(host)&&s.flags.receives(host),"staff alert toggle mutes and restores the current session without disabling detection");
            StaffMenu.flags(host,p.getUUID(),0);check.accept(host.containerMenu instanceof StaffMenu.Menu,"staff flag history opens the existing protected small-caps menu");host.closeContainer();
            s.flags.leave(p.getUUID());c.antiCheatMacroMinPeriod=12;c.antiCheatMacroMaxPeriod=12;c.antiCheatMacroRepeats=3;c.antiCheatMacroMatch=1;
            p.setPos(18000.5,57,18000.5);p.setYRot(0);s.flags.sample(p);
            for(int i=1;i<=36;i++){int at=i%12,side=at/3,step=at%3;double x=side==0?step:side==1?3:side==2?3-step:0,z=side==0?0:side==1?step:side==2?3:3-step;p.setPos(18000.5+x,57,18000.5+z);p.setYRot(side*90);s.flags.action(p,2);s.flags.sample(p);}
            check.accept(s.flags.history(p.getUUID()).stream().anyMatch(f->f.check.equals("Repeated action loop")),"repeated rich movement/farming cycles produce reviewable evidence");
            s.flags.leave(p.getUUID());long slowNanos=System.nanoTime();for(int i=0;i<3*(c.antiCheatSlowSamples+1);i++){time[0]+=1000;s.flags.action(p,1);s.flags.swing(p,slowNanos+i*1_000_000_000L);}
            check.accept(s.flags.history(p.getUUID()).stream().anyMatch(f->f.check.equals("Slow click timing")&&f.evidence.contains("1.00 CPS")),"sustained exact one-hit-per-second attacks create slow-timing evidence independent of the four-CPS check");
            s.flags.leave(p.getUUID());long later=slowNanos+300_000_000_000L;Random jitter=new Random(2);for(int i=0;i<3*(c.antiCheatSlowSamples+1);i++){long interval=1_000_000_000L+(jitter.nextInt(201)-100)*1_000_000L;later+=interval;time[0]+=interval/1_000_000;s.flags.action(p,1);s.flags.swing(p,later);}
            check.accept(s.flags.history(p.getUUID()).stream().filter(f->f.check.equals("Slow click timing")).count()==1,"ordinary low-rate timing variation is not flagged even when the alert cooldown has expired");
            var lower=new FlagService.Flag();lower.player=UUID.randomUUID();lower.name="LowerPriority";lower.check="Click timing";lower.evidence="fixture";lower.dimension="minecraft:overworld";lower.time=time[0]-12*3600000L;lower.profile=net.minecraft.world.item.component.ResolvableProfile.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,net.minecraft.world.item.component.ResolvableProfile.createResolved(new com.mojang.authlib.GameProfile(lower.player,lower.name))).getOrThrow().toString();s.flags.data.flags.add(lower);
            var old=new FlagService.Flag();old.player=UUID.randomUUID();old.name="OldPriority";old.check="Fast clicking";old.evidence="fixture";old.dimension="minecraft:overworld";old.time=time[0]-25*3600000L;s.flags.data.flags.add(old);
            check.accept(s.flags.suspects().getFirst().player().equals(p.getUUID())&&s.flags.suspects().stream().noneMatch(f->f.player().equals(old.player)),"review priority ranks recent repeated flags above old/sparse evidence and excludes expired ranking entries");
            check.accept(StaffMenu.head(lower.player).get(DataComponents.PROFILE).name().orElse("").equals(lower.name),"offline review heads retain their saved player profile without synchronous skin lookups");
            StaffMenu.suspects(host,0);var top=host.containerMenu.getSlot(9).getItem();check.accept(top.is(Items.PLAYER_HEAD)&&top.get(DataComponents.PROFILE).partialProfile().id().equals(p.getUUID()),"most suspicious menu uses the highest-priority player's real profile head");host.closeContainer();
            StaffMenu.home(host,0);check.accept(java.util.stream.IntStream.range(9,45).mapToObj(i->host.containerMenu.getSlot(i).getItem()).anyMatch(item->item.has(DataComponents.PROFILE)&&item.get(DataComponents.PROFILE).partialProfile().id().equals(p.getUUID())),"staff player list displays each player's own profile instead of generic heads");host.closeContainer();
            c.antiCheatEnabled=false;int count=s.flags.history(null).size();s.flags.flag(p,"disabled","nothing");check.accept(s.flags.history(null).size()==count,"disabling the observer prevents new flags without changing gameplay");c.antiCheatEnabled=true;
            s.flags.observe(()->{throw new IllegalStateException("fixture observer failure");});check.accept(p.isAlive(),"detector failures fail open and never interrupt gameplay");
        }finally{
            if(s.staffMode(host))s.toggleStaffMode(host);s.server.getPlayerList().deop(host.nameAndId());s.permissions.leave(host);s.config=originalConfig;s.flags.data.flags.clear();s.flags.data.flags.addAll(saved.flags);s.flags.clear(p.getUUID());s.flags.leave(host.getUUID());s.flags.reset();CombatIntegration.remove(s.server,p);CombatIntegration.remove(s.server,host);
        }
    }
    private FlagIntegration(){}
}
