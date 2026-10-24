package com.celik.kingdomscombat;

import com.celik.kingdoms.*;
import com.celik.kingdoms.mixin.AirDropPlayerSaveInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;

final class EventChestIntegration {
    static void run(CombatService s,ServerPlayer host,ServerPlayer player,ServerPlayer rival,BiConsumer<Boolean,String> check)throws Exception{
        List<ItemStack> hostInv=inventory(host),playerInv=inventory(player),rivalInv=inventory(rival);var hp=host.position();var pp=player.position();var rp=rival.position();
        BlockPos chest=new BlockPos(11003,160,12003);var level=host.level();var old=level.getBlockState(chest);Path file=s.stateFile.resolveSibling("event-chests.json");
        try{
            host.closeContainer();player.closeContainer();rival.closeContainer();host.setPos(11001,160,12003);player.setPos(11003,160,12004);rival.setPos(11003,160,12005);
            clear(player);clear(rival);level.setBlockAndUpdate(chest,Blocks.CHEST.defaultBlockState());
            s.events.chests.selectChest(host.createCommandSourceStack(),chest);
            host.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND,2));command(s,host,"required");
            host.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.EMERALD,3));command(s,host,"reward");command(s,host,"money 1k");
            check.accept(s.events.chests.status().contains("Money: 1000"),"host commands accept money aliases and held-item reward templates");
            s.events.chests.settings(host);check.accept(host.containerMenu instanceof com.celik.kingdoms.Ui.Menu,"event host chest setup opens the existing small-caps menu framework");host.closeContainer();
            s.events.start(host.createCommandSourceStack(),"Turn-in test","10m");UUID event=s.events.id();
            check.accept(s.events.chests.use(player,chest)&&player.containerMenu instanceof EventChests.InputMenu,"physical event chest opens a private server-side turn-in menu");
            var menu=(EventChests.InputMenu)player.containerMenu;
            menu.clicked(18,0,ContainerInput.PICKUP,player);check.accept(player.getInventory().isEmpty()&&menu.getCarried().isEmpty(),"reward preview cannot be extracted");
            player.getInventory().setItem(0,new ItemStack(Items.DIRT,16));menu.clicked(54,0,ContainerInput.QUICK_MOVE,player);
            check.accept(menu.input.getItem(0).isEmpty()&&player.getInventory().getItem(0).is(Items.DIRT),"event chest rejects unrelated items as storage");
            ItemStack named=new ItemStack(Items.DIAMOND,2);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Different diamond"));player.getInventory().setItem(0,named);menu.clicked(54,0,ContainerInput.QUICK_MOVE,player);check.accept(menu.input.getItem(0).isEmpty(),"requirement matching includes item components rather than material alone");
            player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,1));menu.clicked(54,0,ContainerInput.PICKUP,player);
            menu.clicked(26,0,ContainerInput.PICKUP,player);check.accept(menu.input.getItem(0).getCount()==1&&player.getInventory().countItem(Items.EMERALD)==0,"missing requirements consume nothing and award nothing");
            player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,3));menu.clicked(54,0,ContainerInput.PICKUP,player);
            check.accept(menu.input.getItem(0).getCount()==4&&menu.getCarried().isEmpty(),"click deposits full stacks without leaving unsaved cursor items");
            EventChests.Data saved=CombatStore.read(file,EventChests.Data.class,null);check.accept(saved.accounts.get(player.getUUID()).inputs.stream().filter(v->!v.isEmpty()).count()==1,"private deposits are saved as items leave the player inventory");
            player.closeContainer();s.events.chests.open(player);menu=(EventChests.InputMenu)player.containerMenu;
            check.accept(menu.input.getItem(0).getCount()==4,"closing and reopening preserves personal deposits");
            for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Items.STONE,64));menu.clicked(26,0,ContainerInput.PICKUP,player);check.accept(menu.input.getItem(0).getCount()==4&&player.getInventory().countItem(Items.EMERALD)==0,"full reward inventory consumes none of the deposited requirements");clear(player);
            s.events.chests.open(rival);check.accept(((EventChests.InputMenu)rival.containerMenu).input.isEmpty()==false&&((EventChests.InputMenu)rival.containerMenu).input.getItem(0).isEmpty(),"another player cannot access the depositor's input slots");
            long balance=KingdomsMod.service.api().balances().getBalance(player.getUUID());menu.clicked(26,0,ContainerInput.PICKUP,player);
            check.accept(menu.input.getItem(0).getCount()==2&&player.getInventory().countItem(Items.EMERALD)==3,"successful claim consumes exact requirements and gives item rewards");
            check.accept(KingdomsMod.service.api().balances().getBalance(player.getUUID())==balance+1000,"event money reward reaches the claimant wallet");
            menu.clicked(26,0,ContainerInput.PICKUP,player);check.accept(menu.input.getItem(0).getCount()==2&&player.getInventory().countItem(Items.EMERALD)==3&&s.events.active(),"once-per-player blocks duplicate claims while event remains active");
            menu.clicked(0,0,ContainerInput.PICKUP,player);check.accept(menu.input.getItem(0).isEmpty()&&player.getInventory().countItem(Items.DIAMOND)==2,"players can withdraw excess deposited items");
            s.events.stop(host.createCommandSourceStack());command(s,host,"onceperplayer false");command(s,host,"money 0");
            s.events.start(host.createCommandSourceStack(),"Repeat test","10m");s.events.chests.open(player);menu=(EventChests.InputMenu)player.containerMenu;
            clear(player);player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,4));menu.clicked(54,0,ContainerInput.QUICK_MOVE,player);menu.clicked(26,0,ContainerInput.PICKUP,player);menu.clicked(26,0,ContainerInput.PICKUP,player);
            check.accept(player.getInventory().countItem(Items.EMERALD)==6&&menu.input.getItem(0).isEmpty(),"repeat claims can be enabled independently of event ending");
            s.events.stop(host.createCommandSourceStack());command(s,host,"endafterclaim true");s.events.start(host.createCommandSourceStack(),"Race test","10m");
            clear(player);clear(rival);player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,2));rival.getInventory().setItem(0,new ItemStack(Items.DIAMOND,2));s.events.chests.open(player);s.events.chests.open(rival);
            menu=(EventChests.InputMenu)player.containerMenu;var other=(EventChests.InputMenu)rival.containerMenu;menu.clicked(54,0,ContainerInput.QUICK_MOVE,player);other.clicked(54,0,ContainerInput.QUICK_MOVE,rival);
            menu.clicked(26,0,ContainerInput.PICKUP,player);other.clicked(26,0,ContainerInput.PICKUP,rival);
            check.accept(!s.events.active()&&s.events.bossBar()==null&&player.getInventory().countItem(Items.EMERALD)==3&&rival.getInventory().countItem(Items.EMERALD)==0&&other.input.getItem(0).getCount()==2,"first-claim ending is atomic and leaves another player's inputs untouched");
            rival.closeContainer();s.events.chests.open(rival);other=(EventChests.InputMenu)rival.containerMenu;other.clicked(0,0,ContainerInput.PICKUP,rival);
            check.accept(rival.getInventory().countItem(Items.DIAMOND)==2,"deposits remain recoverable after the event ends");
            player.closeContainer();clear(player);s.events.start(host.createCommandSourceStack(),"End save retry","10m");UUID interrupted=s.events.id();player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,2));s.events.chests.open(player);menu=(EventChests.InputMenu)player.containerMenu;menu.clicked(54,0,ContainerInput.QUICK_MOVE,player);
            Path eventFile=s.stateFile.resolveSibling("event.json"),backup=eventFile.resolveSibling("event-test-backup.json");Files.move(eventFile,backup,StandardCopyOption.REPLACE_EXISTING);Files.createDirectory(eventFile);
            try{menu.clicked(26,0,ContainerInput.PICKUP,player);s.events.chests.tick();check.accept(player.getInventory().countItem(Items.EMERALD)==3&&menu.input.getItem(0).isEmpty()&&CombatStore.read(file,EventChests.Data.class,null).completed.contains(interrupted),"failed event-end save preserves the already-committed claim without throwing from the retry tick");menu.clicked(26,0,ContainerInput.PICKUP,player);check.accept(player.getInventory().countItem(Items.EMERALD)==3,"interrupted event ending cannot award a second claim");}
            finally{Files.delete(eventFile);Files.move(backup,eventFile,StandardCopyOption.REPLACE_EXISTING);}
            s.events.chests.tick();check.accept(!s.events.active()&&s.events.bossBar()==null,"first-claim event automatically finishes after event storage recovers");
            player.closeContainer();rival.closeContainer();
            // Simulate a crash after a player file was saved but before the chest ledger committed.
            EventChests.Data state=CombatStore.read(file,EventChests.Data.class,null);EventChests.Journal journal=new EventChests.Journal();journal.player=player.getUUID();journal.next=CombatStore.JSON.fromJson(CombatStore.JSON.toJson(state),EventChests.Data.class);UUID marker=UUID.randomUUID();journal.next.completed.add(marker);
            ((PenaltyReceipt)player).combat$eventReceipt(journal.receipt);((AirDropPlayerSaveInvoker)s.server.getPlayerList()).kingdoms$saveAirDropPlayer(player);CombatStore.write(file.resolveSibling("event-chest-transaction.json"),journal);new EventChests(s,file);
            check.accept(CombatStore.read(file,EventChests.Data.class,null).completed.contains(marker)&&!Files.exists(file.resolveSibling("event-chest-transaction.json")),"saved player receipt completes interrupted chest transactions exactly once");
            journal.receipt=UUID.randomUUID();UUID absent=UUID.randomUUID();journal.next.completed.add(absent);CombatStore.write(file.resolveSibling("event-chest-transaction.json"),journal);new EventChests(s,file);
            check.accept(!CombatStore.read(file,EventChests.Data.class,null).completed.contains(absent),"unsaved player transaction is rolled back on recovery");
            check.accept(!new HostedEvents(s,s.stateFile.resolveSibling("event.json")).active(),"claimed-once event stays ended after reloading");
        }finally{
            if(s.events.active())s.events.stop(host.createCommandSourceStack());command(s,host,"disable");host.closeContainer();player.closeContainer();rival.closeContainer();restore(host,hostInv);restore(player,playerInv);restore(rival,rivalInv);host.setPos(hp);player.setPos(pp);rival.setPos(rp);level.setBlockAndUpdate(chest,old);
        }
    }
    private static void command(CombatService s,ServerPlayer host,String command){s.server.getCommands().performPrefixedCommand(host.createCommandSourceStack(),"eventhost chest "+command);}
    private static List<ItemStack> inventory(ServerPlayer p){List<ItemStack> result=new ArrayList<>();for(int i=0;i<p.getInventory().getContainerSize();i++)result.add(p.getInventory().getItem(i).copy());return result;}
    private static void restore(ServerPlayer p,List<ItemStack> items){for(int i=0;i<items.size();i++)p.getInventory().setItem(i,items.get(i));}
    private static void clear(ServerPlayer p){for(int i=0;i<p.getInventory().getContainerSize();i++)p.getInventory().setItem(i,ItemStack.EMPTY);}
}
