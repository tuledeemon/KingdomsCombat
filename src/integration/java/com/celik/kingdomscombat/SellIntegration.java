package com.celik.kingdomscombat;

import com.celik.kingdoms.KingdomsMod;
import com.reazip.economycraft.EconomyCraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import java.util.*;
import java.util.function.BiConsumer;

final class SellIntegration {
    static void run(CombatService service,long[] time,BiConsumer<Boolean,String> check)throws Exception{
        SellMarket m=service.sellMarket;var savedData=m.data;var savedConfig=m.config;var savedPrices=m.prices;long oldTime=time[0];
        ServerPlayer p=CombatIntegration.player(service.server,service.server.overworld(),"MarketSeller",17000,160,17000),other=CombatIntegration.player(service.server,service.server.overworld(),"MarketRival",17002,160,17000);
        var manager=EconomyCraft.getManager(service.server);
        try{
            m.data=new SellMarket.Data();m.config=new SellRules.Config();m.config.defaults.volumeForFloor=64;m.config.defaults.recoveryPerHour=64;
            var entries=new HashMap<>(m.prices);SellMarket.Price price=new SellMarket.Price();price.unit_sell=100;entries.put("minecraft:diamond",price);m.prices=entries;
            check.accept(m.prices.size()==1695&&m.prices.entrySet().stream().filter(e->e.getKey().endsWith("_leaves")).allMatch(e->e.getValue().unit_sell==1),"supplied 1695-entry sell list keeps all eleven leaf prices nerfed to one");
            var command=service.server.getCommands().getDispatcher().getRoot().getChild("sell");check.accept(command!=null&&command.getChildren().isEmpty(),"custom sell command replaces the EconomyCraft root and its old subcommands");
            com.reazip.economycraft.sell.SellUi.open(p,manager);check.accept(p.containerMenu instanceof SellMarket.SellMenu,"EconomyCraft sell-menu entry points open the shared dynamic market");p.closeContainer();
            var openItems=com.reazip.economycraft.shop.ShopUi.class.getDeclaredMethod("openItems",ServerPlayer.class,com.reazip.economycraft.EconomyManager.class,String.class);openItems.setAccessible(true);openItems.invoke(null,p,manager,"resources");
            Object shop=p.containerMenu;var handleSell=shop.getClass().getDeclaredMethod("handleSell",com.reazip.economycraft.PriceRegistry.PriceEntry.class,int.class);handleSell.setAccessible(true);handleSell.invoke(shop,manager.getPrices().resolve(new ItemStack(Items.DIAMOND)),1);
            check.accept(p.containerMenu instanceof SellMarket.SellMenu,"shop selling routes into the custom market instead of bypassing supply pressure and named-item checks");p.closeContainer();
            ItemStack named=new ItemStack(Items.DIAMOND,3);named.set(DataComponents.CUSTOM_NAME,Component.literal("Keep me"));p.getInventory().setItem(9,named);
            m.open(p);SellMarket.SellMenu menu=(SellMarket.SellMenu)p.containerMenu;menu.clicked(36,0,ContainerInput.PICKUP,p);
            check.accept(p.getInventory().getItem(9).getCount()==3&&menu.input.getItem(0).isEmpty(),"native sell menu rejects named stacks without moving or deleting them");
            ItemStack custom=new ItemStack(Items.DIAMOND);custom.set(DataComponents.ITEM_NAME,Component.literal("Custom diamond"));check.accept(m.price(custom)==null,"custom item-name components cannot bypass the named-item restriction");
            p.getInventory().setItem(9,new ItemStack(Items.DIAMOND,32));long before=manager.getBalance(p.getUUID(),true);long quoted=m.quote(List.of(p.getInventory().getItem(9))).total();menu.clicked(36,0,ContainerInput.QUICK_MOVE,p);
            check.accept(p.getInventory().getItem(9).isEmpty()&&menu.input.getItem(0).getCount()==32&&CombatStore.read(m.file,SellMarket.Data.class,null).inputs.get(p.getUUID()).getFirst().contains("diamond"),"shift-click transfers sell inputs into durable private slots with a verified player receipt");
            check.accept(menu.getCarried().isEmpty()&&((PenaltyReceipt)p).combat$sellReceipt()!=null,"sell transfers never leave a cursor stack outside the inventory journal");
            p.closeContainer();check.accept(manager.getBalance(p.getUUID(),true)==before+quoted&&!m.deposited(p.getUUID())&&m.data.awards.isEmpty(),"closing the sell menu consumes inputs and credits the EconomyCraft wallet exactly once");
            menu.removed(p);check.accept(manager.getBalance(p.getUUID(),true)==before+quoted,"repeated removal callbacks cannot sell the same contents twice");
            check.accept(m.quote(List.of(new ItemStack(Items.DIAMOND))).total()<100,"one player's sale lowers that item's price for later players");
            m.open(other);SellMarket.SellMenu second=(SellMarket.SellMenu)other.containerMenu;check.accept(m.quote(List.of(new ItemStack(Items.DIAMOND))).total()<100,"another player sees the same server-wide pressure");other.closeContainer();
            time[0]+=3_600_000;check.accept(m.quote(List.of(new ItemStack(Items.DIAMOND))).total()==100,"real elapsed hours recover supply pressure without per-item background ticking");
            p.getInventory().setItem(9,new ItemStack(Items.DIAMOND,4));m.open(p);menu=(SellMarket.SellMenu)p.containerMenu;menu.clicked(36,0,ContainerInput.PICKUP,p);long wallet=manager.getBalance(p.getUUID(),true);menu.clicked(27,0,ContainerInput.PICKUP,p);
            check.accept(total(p,Items.DIAMOND)==4&&!m.deposited(p.getUUID())&&manager.getBalance(p.getUUID(),true)==wallet,"cancel returns exact sell inputs without payment or supply pressure");
            p.getInventory().clearContent();p.getInventory().setItem(9,new ItemStack(Items.DIAMOND,5));m.open(p);menu=(SellMarket.SellMenu)p.containerMenu;menu.clicked(36,0,ContainerInput.PICKUP,p);
            p.invulnerableTime=0;p.hurtServer(p.level(),p.damageSources().playerAttack(other),2);
            check.accept(!(p.containerMenu instanceof SellMarket.SellMenu)&&total(p,Items.DIAMOND)==5&&!m.deposited(p.getUUID())&&manager.getBalance(p.getUUID(),true)==wallet,"damage closes sell storage and refunds deposits before combat damage or death can run");
            boolean denied=false;try{m.open(p);}catch(IllegalArgumentException expected){denied=true;}check.accept(denied,"combat-tagged players cannot open private sell storage");service.ledger.clear(p.getUUID());service.ledger.clear(other.getUUID());
            p.getInventory().clearContent();p.getInventory().setItem(9,new ItemStack(Items.DIAMOND,2));m.open(p);menu=(SellMarket.SellMenu)p.containerMenu;menu.clicked(36,0,ContainerInput.PICKUP,p);entries=new HashMap<>(m.prices);entries.remove("minecraft:diamond");m.prices=entries;p.closeContainer();
            check.accept(m.deposited(p.getUUID())&&manager.getBalance(p.getUUID(),true)==wallet,"removing a price before checkout retains unsellable input stacks instead of deleting them");
            m.open(p);menu=(SellMarket.SellMenu)p.containerMenu;menu.clicked(27,0,ContainerInput.PICKUP,p);check.accept(total(p,Items.DIAMOND)==2&&!m.deposited(p.getUUID()),"unsellable saved items remain withdrawable");
            entries=new HashMap<>(savedPrices);entries.put("minecraft:diamond",price);m.prices=entries;
            p.getInventory().clearContent();p.getInventory().setItem(9,new ItemStack(Items.DIAMOND,6));m.open(p);menu=(SellMarket.SellMenu)p.containerMenu;
            var backup=m.file.resolveSibling("sell-market-test-backup.json");java.nio.file.Files.move(m.file,backup,java.nio.file.StandardCopyOption.REPLACE_EXISTING);java.nio.file.Files.createDirectory(m.file);
            try{menu.clicked(36,0,ContainerInput.PICKUP,p);check.accept(!m.healthy&&java.nio.file.Files.exists(m.journalFile),"failed sell ledger save retains the journal after a verified player-inventory save");menu.cancel=true;p.closeContainer();}
            finally{java.nio.file.Files.delete(m.file);java.nio.file.Files.move(backup,m.file,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            time[0]+=5001;m.tick();check.accept(m.healthy&&m.deposited(p.getUUID())&&!java.nio.file.Files.exists(m.journalFile),"sell journal recovery completes the saved input transfer after storage becomes writable again");
            m.open(p);menu=(SellMarket.SellMenu)p.containerMenu;menu.clicked(27,0,ContainerInput.PICKUP,p);check.accept(total(p,Items.DIAMOND)==6&&!m.deposited(p.getUUID())&&manager.getBalance(p.getUUID(),true)==wallet,"save-failure recovery returns all six original items without a payment or duplication");
            var unverified=new SellMarket.Journal();unverified.player=p.getUUID();unverified.next=CombatStore.JSON.fromJson(CombatStore.JSON.toJson(m.data),SellMarket.Data.class);unverified.next.supply.put("minecraft:stone",new SellRules.Supply());CombatStore.write(m.journalFile,unverified);time[0]+=5001;m.tick();
            check.accept(!m.data.supply.containsKey("minecraft:stone")&&!java.nio.file.Files.exists(m.journalFile)&&total(p,Items.DIAMOND)==6,"an unacknowledged inventory journal is discarded without applying its proposed market changes");
            m.prices=savedPrices;var award=new SellMarket.Award();award.player=p.getUUID();award.amount=25;m.data.awards.add(award);CombatStore.write(m.file,m.data);
            var pending=CombatStore.read(m.file,SellMarket.Data.class,null);time[0]+=5001;m.tick();long credited=manager.getBalance(p.getUUID(),true);
            check.accept(credited==wallet+25&&m.data.awards.isEmpty(),"queued sale payments retry through the durable EconomyCraft reward path");
            CombatStore.write(m.file,pending);var recovered=new SellMarket(service);time[0]+=5001;recovered.tick();check.accept(manager.getBalance(p.getUUID(),true)==credited&&recovered.data.awards.isEmpty(),"restart recovery does not duplicate a payment completed before the sell ledger was saved");
            m.data=recovered.data;
            check.accept(KingdomsMod.service.state.airDropMoneyReceipts.contains(award.id),"payment id is durably acknowledged by the existing Kingdoms economy journal");
            byte[] oldSettings=java.nio.file.Files.readAllBytes(m.settingsFile),oldPrices=java.nio.file.Files.readAllBytes(m.pricesFile);
            try{
                service.server.getCommands().performPrefixedCommand(service.server.createCommandSourceStack(),"combatadmin market set floorMultiplier 0.5");check.accept(m.config.defaults.floorMultiplier==.5&&CombatStore.read(m.settingsFile,SellRules.Config.class,null).defaults.floorMultiplier==.5,"admin market commands persist validated settings");
                service.server.getCommands().performPrefixedCommand(service.server.createCommandSourceStack(),"combatadmin market set floorMultiplier NaN");check.accept(m.config.defaults.floorMultiplier==.5,"non-finite market settings are rejected without replacing the working configuration");
                service.server.getCommands().performPrefixedCommand(service.server.createCommandSourceStack(),"combatadmin market price minecraft:diamond 1k");check.accept(m.prices.get("minecraft:diamond").unit_sell==1000,"admin item prices support the existing money aliases");
                service.server.getCommands().performPrefixedCommand(service.server.createCommandSourceStack(),"combatadmin market reset minecraft:diamond");check.accept(!m.data.supply.containsKey("minecraft:diamond"),"admin reset clears only the chosen item's supply pressure");
            }finally{java.nio.file.Files.write(m.settingsFile,oldSettings);java.nio.file.Files.write(m.pricesFile,oldPrices);}

        }finally{
            if(p.containerMenu instanceof SellMarket.SellMenu menu){menu.cancel=true;p.closeContainer();}if(other.containerMenu instanceof SellMarket.SellMenu menu){menu.cancel=true;other.closeContainer();}
            m.data=savedData;m.config=savedConfig;m.prices=savedPrices;CombatStore.write(m.file,savedData);time[0]=oldTime;
            CombatIntegration.remove(service.server,p);CombatIntegration.remove(service.server,other);
        }
    }
    private static int total(ServerPlayer p,Item item){int n=0;for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(item))n+=p.getInventory().getItem(i).getCount();return n;}
    private SellIntegration(){}
}
