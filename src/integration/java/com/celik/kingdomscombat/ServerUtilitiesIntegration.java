package com.celik.kingdomscombat;

import com.celik.kingdoms.Ui;
import com.reazip.economycraft.EconomyCraft;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

final class ServerUtilitiesIntegration {
    static void run(CombatService s,long[] time,BiConsumer<Boolean,String> check)throws Exception{
        var original=s.config;var cfg=CombatStore.JSON.fromJson(CombatStore.JSON.toJson(original),CombatConfig.class);s.config=cfg;var p=CombatIntegration.player(s.server,s.server.overworld(),"UtilityPlayer",19000.5,160,19000.5);
        List<Runnable> queued=new ArrayList<>();AtomicInteger calls=new AtomicInteger();var rewards=new PlaytimeRoles(s,(id,role)->{calls.incrementAndGet();return PlaytimeRoles.Result.GRANTED;},queued::add);
        try{
            check.accept(s.server.getCommands().getDispatcher().getRoot().getChild("staff").getChild("flags")==null,"all flag commands are removed from the registered staff command tree");
            cfg.playtimeRoleEnabled=true;cfg.playtimeRoleId="1565255666283581520";cfg.playtimeRoleHours=4;p.getStats().setValue(p,Stats.CUSTOM.get(Stats.PLAY_TIME),287999);rewards.consider(p);check.accept(queued.isEmpty(),"Discord reward does not enqueue below four recorded hours");
            p.getStats().setValue(p,Stats.CUSTOM.get(Stats.PLAY_TIME),288000);rewards.consider(p);rewards.consider(p);check.accept(queued.size()==1&&calls.get()==0,"existing four-hour playtime qualifies while network work is queued once outside the server observation path");queued.removeFirst().run();rewards.tick();rewards.consider(p);check.accept(calls.get()==1&&queued.isEmpty(),"completed Discord role checks respect their cooldown");
            time[0]+=(cfg.playtimeRoleCheckSeconds+1)*1000L;rewards.consider(p);rewards.reset();queued.removeFirst().run();rewards.tick();check.accept(calls.get()==1,"a queued grant from a superseded configuration is skipped");
            check.accept(new PlaytimeRoleBridge().grant(p.getUUID(),cfg.playtimeRoleId)==PlaytimeRoles.Result.UNAVAILABLE,"Discord Integration is optional and absent integration never issues live role changes");
            p.getEnderChestInventory().setItem(0,new ItemStack(Items.DIAMOND,3));var provider=new SimpleMenuProvider((id,inv,who)->ChestMenu.threeRows(id,inv,p.getEnderChestInventory()),Component.literal("Ender chest"));
            s.ledger.clear(p.getUUID());check.accept(p.openMenu(provider).isPresent()&&CombatStorage.enderChest(p,p.containerMenu),"normal ender-chest menus open outside PvP combat");s.ledger.manual(p.getUUID(),time[0],20);CombatStorage.enforce(p);check.accept(p.containerMenu==p.inventoryMenu,"receiving a combat tag closes an already-open ender chest");
            check.accept(p.openMenu(provider).isEmpty()&&p.containerMenu==p.inventoryMenu&&p.getEnderChestInventory().getItem(0).getCount()==3,"native or command-provided ender-chest menus are refused during combat without losing contents");
            var normal=new SimpleMenuProvider((id,inv,who)->ChestMenu.threeRows(id,inv,new SimpleContainer(27)),Component.literal("Normal chest"));check.accept(p.openMenu(normal).isPresent(),"the ender-chest restriction does not block ordinary chests");p.closeContainer();
            s.ledger.clear(p.getUUID());s.ledger.zone(p.getUUID(),TagLedger.Reason.WAR,time[0],20);check.accept(p.openMenu(provider).isPresent(),"war-only teleport restrictions do not become PvP ender-chest restrictions");p.closeContainer();s.ledger.clear(p.getUUID());s.ledger.manual(p.getUUID(),time[0],20);cfg.blockEnderChestsInCombat=false;check.accept(p.openMenu(provider).isPresent(),"administrators can disable the combat ender-chest rule");p.closeContainer();cfg.blockEnderChestsInCombat=true;s.ledger.clear(p.getUUID());
            var manager=EconomyCraft.getManager(s.server);var before=manager.getBalancesSnapshot();s.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(),"baltop");check.accept(p.containerMenu instanceof Ui.Menu,"the actual baltop command opens the custom public leaderboard instead of EconomyCraft's menu");p.closeContainer();s.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(),"baltop 2");check.accept(p.containerMenu instanceof Ui.Menu&&manager.getBalancesSnapshot().equals(before),"baltop page commands preserve the economy's wallet balances");p.closeContainer();
            s.server.getCommands().performPrefixedCommand(s.server.createCommandSourceStack(),"combatadmin playtimerole 4 1565255666283581520");check.accept(s.config.playtimeRoleEnabled&&s.config.playtimeRoleHours==4&&s.config.playtimeRoleId.equals("1565255666283581520"),"the setup command atomically saves and enables the requested four-hour Discord role");
        }finally{rewards.stop();p.closeContainer();s.ledger.clear(p.getUUID());s.config=original;CombatStore.write(s.configFile,original);s.playtimeRoles.reset();CombatIntegration.remove(s.server,p);}
    }
    private ServerUtilitiesIntegration(){}
}
