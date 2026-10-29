package com.celik.kingdomscombat;

import com.celik.kingdoms.*;
import com.celik.kingdoms.mixin.AirDropPlayerSaveInvoker;
import com.google.gson.reflect.TypeToken;
import com.reazip.economycraft.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static net.minecraft.commands.Commands.*;

/** Receipt-backed private input slots; checkout and shared supply commit together. */
public final class SellMarket {
    public static final class Price {public String category;public int stack;public long unit_buy,unit_sell;}
    public static final class Award {public UUID id=UUID.randomUUID(),player;public long amount;}
    public static final class Data {
        public int schemaVersion=1;
        public Map<UUID,List<String>> inputs=new HashMap<>();
        public Map<String,SellRules.Supply> supply=new HashMap<>();
        public List<Award> awards=new ArrayList<>();
    }
    public static final class Journal {public UUID player,receipt=UUID.randomUUID();public Data next;}
    public record ItemPrice(String key,long base){}
    public record Cart(long total,int count,Map<String,SellRules.Supply> supply){}
    final CombatService service;final Path file,journalFile,settingsFile,pricesFile;
    SellRules.Config config;Map<String,Price> prices;Data data;boolean healthy=true;
    private long lastRetry;
    SellMarket(CombatService service){
        this.service=service;file=service.stateFile.resolveSibling("sell-market.json");journalFile=file.resolveSibling("sell-transaction.json");
        settingsFile=service.configFile.resolveSibling("sell-settings.json");pricesFile=service.configFile.resolveSibling("sell-prices.json");
        // Test fixtures may place configuration next to world state; keep them distinct as well.
        if(file.toAbsolutePath().normalize().equals(settingsFile.toAbsolutePath().normalize()))throw new IllegalStateException("Sell settings and world state must have different paths.");
        reload();data=CombatStore.read(file,Data.class,new Data());validate(data);recover();
    }
    public void reload(){
        SellRules.Config next=CombatStore.read(settingsFile,SellRules.Config.class,new SellRules.Config());next.validate();
        try{
            if(!Files.exists(pricesFile)){
                Files.createDirectories(pricesFile.toAbsolutePath().getParent());
                try(var input=SellMarket.class.getResourceAsStream("/sell-prices.json")){if(input==null)throw new IllegalStateException("Missing default sell prices.");Files.copy(input,pricesFile);}
            }
            Map<String,Price> parsed=CombatStore.JSON.fromJson(Files.readString(pricesFile,StandardCharsets.UTF_8),new TypeToken<Map<String,Price>>(){}.getType());
            if(parsed==null||parsed.isEmpty()||parsed.size()>20000)throw new IllegalArgumentException("Invalid sell price list.");
            parsed.forEach((id,p)->{if(id==null||id.isBlank()||p==null||p.unit_sell<0||p.unit_sell>1_000_000_000_000L)throw new IllegalArgumentException("Invalid sell price: "+id);});
            CombatStore.write(settingsFile,next);config=next;prices=Map.copyOf(parsed);
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot load sell prices.",e);}
    }
    private void validate(Data d){
        if(d==null||d.schemaVersion!=1||d.inputs==null||d.supply==null||d.awards==null)throw new IllegalStateException("Invalid sell market data.");
        d.inputs.forEach((id,slots)->{if(id==null||slots==null||slots.size()!=27||slots.stream().anyMatch(Objects::isNull))throw new IllegalStateException("Invalid saved sell inputs.");});
        d.supply.forEach((id,s)->{if(id==null||s==null||!Double.isFinite(s.volume)||s.volume<0)throw new IllegalStateException("Invalid saved sell supply.");});
        for(Award a:d.awards)if(a==null||a.id==null||a.player==null||a.amount<1)throw new IllegalStateException("Invalid pending sale.");
    }
    private Data copy(){return CombatStore.JSON.fromJson(CombatStore.JSON.toJson(data),Data.class);}
    private void store(Data next){validate(next);CombatStore.write(file,next);data=next;}
    private String encode(ItemStack s){return s.isEmpty()?"":AirDropLoot.encode(service.server,s);}
    private ItemStack decode(String s){return s.isEmpty()?ItemStack.EMPTY:AirDropLoot.decode(service.server,s);}
    private void check(){if(!healthy||Files.exists(journalFile))throw new IllegalArgumentException("Sell storage needs recovery. Your saved items remain protected.");}
    public boolean deposited(UUID player){return data.inputs.getOrDefault(player,List.of()).stream().anyMatch(s->!s.isEmpty());}
    public ItemPrice price(ItemStack stack){
        if(stack==null||stack.isEmpty()||stack.has(DataComponents.CUSTOM_NAME)||stack.getComponentsPatch().entrySet().stream().anyMatch(e->e.getKey()==DataComponents.ITEM_NAME&&e.getValue().isPresent())||Artifacts.marked(stack)||Artifacts.fixerMarked(stack)||KingdomCores.isToken(stack))return null;
        // Never dispose of hidden contents, regardless of EconomyCraft's container settings.
        if(stack.has(DataComponents.CONTAINER)&&stack.get(DataComponents.CONTAINER).nonEmptyItems().iterator().hasNext()||stack.has(DataComponents.BUNDLE_CONTENTS)&&!stack.get(DataComponents.BUNDLE_CONTENTS).isEmpty())return null;
        var manager=EconomyCraft.getManager(service.server);if(manager==null)return null;
        var resolved=SellService.sellableResolved(manager.getPrices(),stack);if(resolved==null)return null;
        Price entry=prices.get(resolved.key());if(entry==null||entry.unit_sell<1)return null;
        return new ItemPrice(resolved.key(),entry.unit_sell);
    }
    public Cart quote(List<ItemStack> items){
        Map<String,Integer> counts=new TreeMap<>();Map<String,Long> bases=new HashMap<>();
        for(ItemStack stack:items){if(stack.isEmpty())continue;ItemPrice p=price(stack);if(p==null)throw new IllegalArgumentException("An item is no longer sellable. Reopen /sell and take it back.");if(stack.getCount()>stack.getMaxStackSize())throw new IllegalArgumentException("Invalid item stack size.");counts.merge(p.key,stack.getCount(),Math::addExact);bases.put(p.key,p.base);}
        Map<String,SellRules.Supply> supply=new HashMap<>();long total=0;int count=0;long now=service.now();
        for(var e:counts.entrySet()){var q=SellRules.quote(bases.get(e.getKey()),e.getValue(),data.supply.get(e.getKey()),config.policy(e.getKey()),now);total=Math.addExact(total,q.total());count=Math.addExact(count,e.getValue());supply.put(e.getKey(),q.supply());}
        return new Cart(total,count,supply);
    }
    private boolean eligible(ServerPlayer p){return config.enabled&&p.isAlive()&&!p.isSpectator()&&!service.staff.frozen(p.getUUID())&&!service.arenas.reserved(p.getUUID())&&(!config.blockInCombat||service.status(p).combatMillis()==0);}
    public void open(ServerPlayer p){
        check();if(!me.lucko.fabric.api.permissions.v0.Permissions.check(p,"kingdomscombat.sell",true))throw new IllegalArgumentException("You do not have permission to sell.");
        if(!eligible(p))throw new IllegalArgumentException("You cannot sell while fighting, frozen, spectating or in an arena.");
        if(!p.containerMenu.getCarried().isEmpty())throw new IllegalArgumentException("Put the item on your cursor away first.");
        p.closeContainer();p.openMenu(new SimpleMenuProvider((id,inventory,owner)->new SellMenu(id,p),CombatService.text("sell • close to sell","gold")));
    }
    private boolean saved(UUID player,UUID receipt){
        Path path=service.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player+".dat");
        if(!Files.exists(path))return false;
        try{return NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap()).getStringOr("kingdomscombat_sell_receipt","").equals(receipt.toString());}
        catch(Exception e){throw new IllegalStateException("Cannot verify sell inventory save.",e);}
    }
    private void commit(ServerPlayer p,Data next){
        Journal j=new Journal();j.player=p.getUUID();j.next=next;CombatStore.write(journalFile,j);
        ((PenaltyReceipt)p).combat$sellReceipt(j.receipt);((AirDropPlayerSaveInvoker)service.server.getPlayerList()).kingdoms$saveAirDropPlayer(p);
        if(!saved(j.player,j.receipt))throw new IllegalStateException("Sell inventory save could not be verified.");
        store(next);try{Files.delete(journalFile);}catch(Exception e){throw new IllegalStateException("Sale needs journal cleanup.",e);}
    }
    private void recover(){
        try{Journal j=CombatStore.read(journalFile,Journal.class,null);if(j==null){healthy=true;return;}if(j.player==null||j.receipt==null||j.next==null)throw new IllegalStateException("Invalid sell journal.");
            if(saved(j.player,j.receipt))store(j.next);Files.delete(journalFile);healthy=true;
        }catch(Exception e){healthy=false;KingdomsCombat.LOG.error("Sell transaction retained for recovery",e);}
    }
    public void tick(){
        if(service.now()>=lastRetry&&service.now()-lastRetry<5000)return;lastRetry=service.now();recover();if(!healthy)return;
        for(Award a:new ArrayList<>(data.awards))pay(a);
        for(ServerPlayer p:service.server.getPlayerList().getPlayers())if(p.containerMenu instanceof SellMenu menu&&!eligible(p)){menu.cancel=true;p.closeContainer();}
    }
    private void pay(Award award){
        try{
            // Reuse the existing durable EconomyCraft reward journal, including core tribute.
            KingdomsMod.service.airDropMoney(award.id,null,award.player,award.amount);
            Data next=copy();next.awards.removeIf(a->a.id.equals(award.id));store(next);
            ServerPlayer p=service.server.getPlayerList().getPlayer(award.player);if(p!=null)tell(p,"Sold items for "+EconomyCraft.formatMoney(award.amount)+" before any kingdom tribute.",false);
        }catch(RuntimeException e){KingdomsCombat.LOG.warn("Sale payment {} remains queued",award.id,e);}
    }
    public void join(ServerPlayer p){if(deposited(p.getUUID()))tell(p,"Your unsold items are saved. Use /sell to retrieve or sell them.",false);}
    public void beforeDamage(ServerPlayer p){if(p.containerMenu instanceof SellMenu menu){menu.cancel=true;p.closeContainer();}}
    public void stop(){for(ServerPlayer p:service.server.getPlayerList().getPlayers())if(p.containerMenu instanceof SellMenu menu){menu.cancel=true;p.closeContainer();}}
    private void tell(ServerPlayer p,String message,boolean error){p.sendSystemMessage(CombatService.text(message,error?"red":"gold"));}
    private ItemStack icon(Item item,String name,List<String> lore){ItemStack s=new ItemStack(item);s.set(DataComponents.CUSTOM_NAME,CombatService.text(name,"gold"));s.set(DataComponents.LORE,new net.minecraft.world.item.component.ItemLore(lore.stream().map(l->(net.minecraft.network.chat.Component)CombatService.text(l,"gray")).toList()));return s;}

    final class SellMenu extends ChestMenu {
        final ServerPlayer player;final SimpleContainer input;boolean cancel,closed;
        SellMenu(int id,ServerPlayer p){this(id,p,new SimpleContainer(36));}
        private SellMenu(int id,ServerPlayer p,SimpleContainer input){
            super(MenuType.GENERIC_9x4,id,p.getInventory(),input,4);player=p;this.input=input;
            for(int i=0;i<27;i++){Slot old=slots.get(i);Slot safe=new Slot(input,i,old.x,old.y){@Override public boolean mayPlace(ItemStack s){return price(s)!=null;}};safe.index=old.index;slots.set(i,safe);}
            List<String> saved=data.inputs.get(p.getUUID());if(saved!=null)for(int i=0;i<27;i++)input.setItem(i,decode(saved.get(i)));refresh();
        }
        private List<ItemStack> stacks(){List<ItemStack> list=new ArrayList<>();for(int i=0;i<27;i++)list.add(input.getItem(i).copy());return list;}
        private void refresh(){
            for(int i=27;i<36;i++)input.setItem(i,icon(Items.GLASS_PANE," ",List.of()));
            input.setItem(27,icon(Items.CHEST,"Return items and cancel",List.of("No sale. Items go back into your inventory.","Any overflow remains saved for /sell.")));
            try{Cart quote=quote(stacks());input.setItem(31,icon(Items.EMERALD,"Total: "+EconomyCraft.formatMoney(quote.total),List.of(quote.count+" items • close this menu to sell","Click or shift-click a stack to move it.","Named items cannot be sold.","Prices can change until checkout.")));}
            catch(Exception e){input.setItem(31,icon(Items.BARRIER,"Take back unsellable items",List.of("An item or price changed. Nothing will be deleted.")));}
            input.setItem(35,icon(Items.OAK_DOOR,"Close and sell",List.of("Shared supply changes prices for everyone.","Prices recover over time.")));
        }
        @Override public boolean stillValid(Player p){return p==player&&eligible(player);}
        @Override public void clicked(int index,int button,ContainerInput type,Player p){
            if(p!=player||closed||!stillValid(p))return;
            if(!getCarried().isEmpty()){tell(player,"Put the cursor item back first.",true);return;}
            if(index==27&&type==ContainerInput.PICKUP){cancel=true;player.closeContainer();return;}
            if(index==35&&type==ContainerInput.PICKUP){player.closeContainer();return;}
            if(type!=ContainerInput.PICKUP&&type!=ContainerInput.QUICK_MOVE||index<0||index>=slots.size()||index>=27&&index<36)return;
            if(slots.get(index).getItem().isEmpty())return;
            if(index>=36&&price(slots.get(index).getItem())==null){tell(player,"That item cannot be sold. Named items are blocked.",true);return;}
            transaction(()->move(index));
        }
        @Override public ItemStack quickMoveStack(Player p,int index){if(p!=player||closed||!stillValid(p)||index<0||index>=slots.size()||index>=27&&index<36)return ItemStack.EMPTY;ItemStack before=slots.get(index).getItem().copy();if(index>=36&&price(before)==null)return ItemStack.EMPTY;transaction(()->move(index));return before;}
        private void move(int index){Slot slot=slots.get(index);ItemStack stack=slot.getItem();if(stack.isEmpty())return;if(index<27)moveItemStackTo(stack,36,slots.size(),true);else moveItemStackTo(stack,0,27,false);if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();}
        private void record(Data next){List<String> list=stacks().stream().map(SellMarket.this::encode).toList();if(list.stream().allMatch(String::isEmpty))next.inputs.remove(player.getUUID());else next.inputs.put(player.getUUID(),new ArrayList<>(list));}
        private void transaction(Runnable action){
            List<ItemStack> inventory=new ArrayList<>();for(int i=0;i<player.getInventory().getContainerSize();i++)inventory.add(player.getInventory().getItem(i).copy());List<ItemStack> before=stacks();UUID old=((PenaltyReceipt)player).combat$sellReceipt();
            try{check();action.run();Data next=copy();record(next);commit(player,next);}
            catch(Exception e){
                try{Journal j=CombatStore.read(journalFile,Journal.class,null);boolean persisted=j!=null&&saved(j.player,j.receipt);if(!persisted){for(int i=0;i<inventory.size();i++)player.getInventory().setItem(i,inventory.get(i));for(int i=0;i<27;i++)input.setItem(i,before.get(i));((PenaltyReceipt)player).combat$sellReceipt(old);if(j!=null)Files.delete(journalFile);}else healthy=false;}
                catch(Exception uncertain){healthy=false;}
                tell(player,"Sell storage could not save. Your items are retained; contact staff if this continues.",true);
            }
            refresh();broadcastChanges();
        }
        private void refund(){transaction(()->{for(int i=0;i<27;i++)move(i);});}
        @Override public void removed(Player p){
            if(closed)return;closed=true;
            if(cancel||!eligible(player)){refund();super.removed(p);return;}
            try{check();Cart q=quote(stacks());if(q.count>0){var manager=EconomyCraft.getManager(service.server);long pending=data.awards.stream().filter(a->a.player.equals(player.getUUID())).mapToLong(a->a.amount).reduce(0,Math::addExact);if(Math.addExact(Math.addExact(Optional.ofNullable(manager.getBalance(player.getUUID(),true)).orElse(0L),pending),q.total)>EconomyManager.MAX)throw new IllegalArgumentException("Your balance would exceed EconomyCraft’s limit.");Data next=copy();next.inputs.remove(player.getUUID());next.supply.putAll(q.supply);Award a=new Award();a.player=player.getUUID();a.amount=q.total;next.awards.add(a);store(next);for(int i=0;i<27;i++)input.setItem(i,ItemStack.EMPTY);pay(a);}}
            catch(Exception e){tell(player,"Nothing sold: "+e.getMessage()+" Your items are saved in /sell.",true);}
            super.removed(p);
        }
    }

    public static boolean redirect(ServerPlayer player){
        var service=KingdomsCombat.service;if(service==null||service.sellMarket==null||!service.sellMarket.config.enabled)return false;
        try{service.sellMarket.open(player);}catch(RuntimeException e){service.sellMarket.tell(player,e.getMessage(),true);}return true;
    }
    public static void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher){
        var root=(com.celik.kingdomscombat.mixin.BalanceCommandAccessor)(Object)dispatcher.getRoot();root.combat$children().remove("sell");root.combat$literals().remove("sell");root.combat$arguments().remove("sell");
        dispatcher.register(literal("sell").executes(c->{try{KingdomsCombat.service.sellMarket.open(c.getSource().getPlayerOrException());return 1;}catch(Exception e){c.getSource().sendFailure(CombatService.text(e.getMessage(),"red"));return 0;}}));
    }
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> admin(){
        var command=literal("market").executes(c->{var m=KingdomsCombat.service.sellMarket;c.getSource().sendSuccess(()->CombatService.text("Sell market: "+m.prices.size()+" prices • "+m.data.awards.size()+" pending payments • "+(m.healthy?"ready":"recovery required"),"gold"),false);return 1;});
        command.then(literal("reload").executes(c->run(c,()->KingdomsCombat.service.sellMarket.reload())));
        command.then(literal("set").then(argument("key",com.mojang.brigadier.arguments.StringArgumentType.word()).then(argument("value",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c->run(c,()->{var m=KingdomsCombat.service.sellMarket;var next=CombatStore.JSON.fromJson(CombatStore.JSON.toJson(m.config),SellRules.Config.class);String key=com.mojang.brigadier.arguments.StringArgumentType.getString(c,"key"),value=com.mojang.brigadier.arguments.StringArgumentType.getString(c,"value");switch(key){case "enabled","blockInCombat"->{if(!value.equals("true")&&!value.equals("false"))throw new IllegalArgumentException("Use true or false.");if(key.equals("enabled"))next.enabled=Boolean.parseBoolean(value);else next.blockInCombat=Boolean.parseBoolean(value);}case "floorMultiplier"->next.defaults.floorMultiplier=Double.parseDouble(value);case "volumeForFloor"->next.defaults.volumeForFloor=Double.parseDouble(value);case "recoveryPerHour"->next.defaults.recoveryPerHour=Double.parseDouble(value);default->throw new IllegalArgumentException("Use enabled, blockInCombat, floorMultiplier, volumeForFloor or recoveryPerHour.");}next.validate();CombatStore.write(m.settingsFile,next);m.config=next;})))));
        command.then(literal("reset").then(argument("item",net.minecraft.commands.arguments.IdentifierArgument.id()).executes(c->run(c,()->{var m=KingdomsCombat.service.sellMarket;m.check();String id=net.minecraft.commands.arguments.IdentifierArgument.getId(c,"item").toString();if(!m.prices.containsKey(id))throw new IllegalArgumentException("Unknown item price key.");Data next=m.copy();next.supply.remove(id);m.store(next);}))));
        var editPrice=literal("price").then(argument("item",net.minecraft.commands.arguments.IdentifierArgument.id()).then(argument("amount",com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(c->run(c,()->{
            var m=KingdomsCombat.service.sellMarket;String id=net.minecraft.commands.arguments.IdentifierArgument.getId(c,"item").toString();
            if(!m.prices.containsKey(id))throw new IllegalArgumentException("Unknown item price key.");
            var next=new HashMap<>(m.prices);Price entry=CombatStore.JSON.fromJson(CombatStore.JSON.toJson(next.get(id)),Price.class);entry.unit_sell=MoneyInput.parse(com.mojang.brigadier.arguments.StringArgumentType.getString(c,"amount"));if(entry.unit_sell>1_000_000_000_000L)throw new IllegalArgumentException("Price is too large.");next.put(id,entry);
            CombatStore.write(m.pricesFile,next);m.prices=Map.copyOf(next);
        }))));
        command.then(editPrice);
        return command;
    }
    private static int run(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,Runnable action){try{action.run();c.getSource().sendSuccess(()->CombatService.text("Sell market settings saved.","gold"),false);return 1;}catch(Exception e){c.getSource().sendFailure(CombatService.text(e.getMessage(),"red"));return 0;}}
}
