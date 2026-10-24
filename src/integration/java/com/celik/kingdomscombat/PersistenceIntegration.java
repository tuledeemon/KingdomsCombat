package com.celik.kingdomscombat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Injects real filesystem failures through native death/disconnect callbacks. */
final class PersistenceIntegration {
    static void run(MinecraftServer server,BiConsumer<Boolean,String> check)throws Exception{
        var original=KingdomsCombat.service;
        var dir=server.getWorldPath(LevelResource.ROOT).resolve("save-failure-"+UUID.randomUUID());
        var target=dir.resolve("state.json");long[] time={System.currentTimeMillis()};
        var config=new CombatConfig();config.staffDefaultPermissionsWhenOff=false;config.chatNotifications=false;config.sounds=false;config.titles=false;config.warRestrictions=false;config.airDropRestrictions=false;
        var s=new CombatService(server,config,dir.resolve("config.json"),target,()->time[0]);
        var level=server.overworld();
        var dead=CombatIntegration.player(server,level,"SaveDeath",11000,160,12000);
        var logout=CombatIntegration.player(server,level,"SaveLogout",11000,160,12003);
        var outage=CombatIntegration.player(server,level,"SaveOutage",11000,160,12006);
        KingdomsCombat.service=s;
        try{
            s.ledger.manual(dead.getUUID(),time[0],20);s.save();
            var backup=block(target);
            dead.kill(level);
            check.accept(!dead.isAlive()&&!s.healthy,"real player death survives a combat-save I/O failure");
            check.accept(!s.allowTeleport(logout,false),"failed persistence blocks ordinary teleport access");
            check.accept(Files.exists(backup)&&Files.readString(backup).contains(dead.getUUID().toString()),"last committed state survives a failed write");
            s.ledger.manual(outage.getUUID(),time[0],20);s.disconnect(outage);
            UUID deferred=s.ledger.data.logoutPenalties.get(outage.getUUID());s.disconnect(outage);
            check.accept(outage.isAlive()&&deferred!=null&&deferred.equals(s.ledger.data.logoutPenalties.get(outage.getUUID())),"logouts during an existing outage retain one stable pending penalty without an unsafe death");
            time[0]+=config.saveIntervalSeconds*1000L;s.tick();
            check.accept(!s.healthy,"scheduled retry during an outage does not crash the server tick");
            restore(target,backup);time[0]+=config.saveIntervalSeconds*1000L;s.tick();
            check.accept(s.healthy&&!CombatStore.read(target,TagLedger.Data.class,null).tags.containsKey(dead.getUUID()),"scheduled retry commits pending death changes and restores persistence health");
            check.accept(deferred.equals(CombatStore.read(target,TagLedger.Data.class,null).logoutPenalties.get(outage.getUUID())),"penalties created during an ongoing outage are persisted on recovery");
            check.accept(s.allowTeleport(logout,false),"teleport restriction lifts after successful persistence recovery");

            s.ledger.manual(logout.getUUID(),time[0],20);s.save();backup=block(target);
            s.disconnect(logout);
            UUID penalty=s.ledger.data.logoutPenalties.get(logout.getUUID());
            check.accept(logout.isAlive()&&penalty!=null&&((PenaltyReceipt)logout).combat$penaltyReceipt()==null,"failed logout-journal write defers death without discarding its pending penalty");
            s.join(logout);
            check.accept(logout.isAlive()&&!s.healthy,"rejoining during an outage cannot apply an unjournaled death");
            restore(target,backup);time[0]+=config.saveIntervalSeconds*1000L;s.tick();
            check.accept(penalty.equals(CombatStore.read(target,TagLedger.Data.class,null).logoutPenalties.get(logout.getUUID())),"recovery durably writes the deferred logout penalty");
            s.join(logout);
            check.accept(!logout.isAlive()&&penalty.equals(((PenaltyReceipt)logout).combat$penaltyReceipt()),"deferred logout death recovers only after the journal is safely saved");
        }finally{
            CombatIntegration.remove(server,dead);CombatIntegration.remove(server,logout);CombatIntegration.remove(server,outage);KingdomsCombat.service=original;
        }
    }
    private static Path block(Path target)throws Exception{
        var backup=target.resolveSibling("committed.json");Files.move(target,backup);
        Files.createDirectory(target);Files.writeString(target.resolve("blocked"),"inject a replace failure");return backup;
    }
    private static void restore(Path target,Path backup)throws Exception{
        Files.delete(target.resolve("blocked"));Files.delete(target);Files.move(backup,target);
    }
}
