package com.celik.kingdomscombat;

import com.celik.kingdoms.*;
import com.celik.kingdoms.mixin.AirDropPlayerSaveInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.*;

/** Private, durable deposits behind a physical event chest. No reward commands are executed. */
public final class EventChests {
    public static final class Plan {
        public String dimension;public int x,y,z;
        public List<String> required=new ArrayList<>(),rewards=new ArrayList<>();
        public long money;public boolean endAfterClaim=false,oncePerPlayer=true;
        public Set<UUID> claimed=new HashSet<>();
    }
    public static final class Account {public UUID event;public List<String> inputs=new ArrayList<>();}
    public static final class Cash {public UUID id=UUID.randomUUID(),player;public long amount;}
    public static final class Data {
        public int schemaVersion=1;public Plan setup=new Plan();
        public Map<UUID,Plan> contracts=new HashMap<>();public Map<UUID,Account> accounts=new HashMap<>();
        public List<Cash> cash=new ArrayList<>();public Set<UUID> completed=new HashSet<>();
    }
    public static final class Journal {public UUID player,receipt=UUID.randomUUID();public Data next;}
    private final CombatService service;private final Path file,journalFile;private Data data;
    private boolean healthy=true;
    private long lastFailureLog;
    EventChests(CombatService service,Path file){this.service=service;this.file=file;this.journalFile=file.resolveSibling("event-chest-transaction.json");data=CombatStore.read(file,Data.class,new Data());validate(data);recoverJournal();}
    private Data copy(){return CombatStore.JSON.fromJson(CombatStore.JSON.toJson(data),Data.class);}
    private void store(Data next){validate(next);CombatStore.write(file,next);data=next;}
    private void check(){if(!healthy||Files.exists(journalFile))throw new IllegalArgumentException("An event chest transaction needs recovery before more items can move.");}
    private static void validate(Data d){
        if(d==null||d.schemaVersion!=1||d.setup==null||d.contracts==null||d.accounts==null||d.cash==null||d.completed==null)throw new IllegalStateException("Invalid event chest data.");
        List<Plan> plans=new ArrayList<>(d.contracts.values());plans.add(d.setup);
        for(Plan p:plans)if(p==null||p.required==null||p.rewards==null||p.claimed==null||p.money<0||p.required.size()>9||p.rewards.size()>8)throw new IllegalStateException("Invalid event chest plan.");
        for(Account a:d.accounts.values())if(a==null||a.event==null||a.inputs==null||a.inputs.size()!=9)throw new IllegalStateException("Invalid event chest deposits.");
        for(Cash c:d.cash)if(c==null||c.id==null||c.player==null||c.amount<=0)throw new IllegalStateException("Invalid event cash delivery.");
    }
    private String encode(ItemStack stack){return stack.isEmpty()?"":AirDropLoot.encode(service.server,stack);}
    private ItemStack decode(String value){return value.isEmpty()?ItemStack.EMPTY:AirDropLoot.decode(service.server,value);}
    private void host(net.minecraft.commands.CommandSourceStack source){if(!CombatService.allowed(source,service.config.eventHostPermission,service.config.adminLevel))throw new IllegalArgumentException("Event host permission required.");check();if(service.events.active())throw new IllegalArgumentException("Stop the event before changing its chest setup.");}
    public void setChest(net.minecraft.commands.CommandSourceStack source)throws Exception {
        host(source);ServerPlayer p=source.getPlayerOrException();var hit=p.pick(6,1,false);
        if(!(hit instanceof net.minecraft.world.phys.BlockHitResult block)||!(p.level().getBlockEntity(block.getBlockPos()) instanceof ChestBlockEntity))throw new IllegalArgumentException("Look at a chest within six blocks.");
        selectChest(source,block.getBlockPos());
    }
    void selectChest(net.minecraft.commands.CommandSourceStack source,BlockPos pos)throws Exception{host(source);ServerPlayer p=source.getPlayerOrException();if(p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>36||!(p.level().getBlockEntity(pos) instanceof ChestBlockEntity))throw new IllegalArgumentException("Choose a nearby chest.");Data next=copy();Plan plan=next.setup;plan.dimension=p.level().dimension().identifier().toString();plan.x=pos.getX();plan.y=pos.getY();plan.z=pos.getZ();store(next);}
    public void add(net.minecraft.commands.CommandSourceStack source,boolean reward)throws Exception {
        host(source);ItemStack held=source.getPlayerOrException().getMainHandItem();
        if(held.isEmpty()||KingdomCores.isToken(held)||Artifacts.marked(held)||Artifacts.fixerMarked(held))throw new IllegalArgumentException("Hold an ordinary item stack. Unique cores, artifacts and fixers cannot be used as templates.");
        Data next=copy();List<String> list=reward?next.setup.rewards:next.setup.required;
        if(list.size()>=(reward?8:9))throw new IllegalArgumentException("Maximum "+(reward?8:9)+" item stacks.");
        list.add(encode(held));store(next);
    }
    public void setting(net.minecraft.commands.CommandSourceStack source,String key,String value){host(source);Data next=copy();switch(key){
        case "money"->next.setup.money=MoneyInput.parse(value);
        case "endafterclaim"->next.setup.endAfterClaim=Boolean.parseBoolean(value);
        case "onceperplayer"->next.setup.oncePerPlayer=Boolean.parseBoolean(value);
        case "clearrequired"->next.setup.required.clear();case "clearrewards"->{next.setup.rewards.clear();next.setup.money=0;}
        case "disable"->next.setup=new Plan();default->throw new IllegalArgumentException("Unknown chest setting.");
    }store(next);}
    public String status(){Plan p=data.setup;return "Chest: "+(p.dimension==null?"not set":p.dimension+" "+p.x+", "+p.y+", "+p.z)+" • Required stacks: "+p.required.size()+" • Reward stacks: "+p.rewards.size()+" • Money: "+p.money+" • End after claim: "+p.endAfterClaim+" • Once per player: "+p.oncePerPlayer;}
    public String information(){Plan p=data.contracts.get(service.events.id());if(p==null)return "";return "Input chest: "+p.x+", "+p.y+", "+p.z+" • "+p.dimension+". Requirements: "+p.required.stream().map(v->{ItemStack stack=decode(v);return stack.getCount()+" × "+stack.getHoverName().getString();}).collect(java.util.stream.Collectors.joining(", "))+". Rewards: "+p.rewards.stream().map(v->{ItemStack stack=decode(v);return stack.getCount()+" × "+stack.getHoverName().getString();}).collect(java.util.stream.Collectors.joining(", "))+" • Money: "+p.money+". "+(p.oncePerPlayer?"Once per player.":"Repeat turn-ins allowed.")+ (p.endAfterClaim?" First claim ends the event.":"");}
    public void settings(ServerPlayer player){
        if(!CombatService.allowed(player.createCommandSourceStack(),service.config.eventHostPermission,service.config.adminLevel))throw new IllegalArgumentException("Event host permission required.");
        Ui.open(player,"Event chest setup",menu->{Plan p=data.setup;
            menu.button(0,Items.CHEST,"Select chest",List.of("Look at a chest, then click.",p.dimension==null?"Not selected":p.dimension+" "+p.x+", "+p.y+", "+p.z),()->{try{setChest(player.createCommandSourceStack());settings(player);}catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}});
            menu.button(2,Items.DIAMOND,"Add required held stack",List.of("Copies the exact item, components and count.","The item in your hand is not consumed."),()->{try{add(player.createCommandSourceStack(),false);settings(player);}catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}});
            menu.button(4,Items.EMERALD,"Add reward held stack",List.of("Staff-created reward template.","Keep the item in your main hand before opening this menu."),()->{try{add(player.createCommandSourceStack(),true);settings(player);}catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}});
            menu.button(6,Items.GOLD_INGOT,"Money reward: "+p.money,List.of("Paid to the claimant wallet.","Click to enter an amount."),()->moneyDialog(player));
            menu.button(8,Items.PLAYER_HEAD,"Once per player: "+p.oncePerPlayer,List.of("Off allows repeat turn-ins."),()->{setting(player.createCommandSourceStack(),"onceperplayer",Boolean.toString(!p.oncePerPlayer));settings(player);});
            for(int i=0;i<p.required.size();i++)menu.button(10+i,decode(p.required.get(i)),"Required stack "+(i+1),List.of("Amount: "+decode(p.required.get(i)).getCount()),null);
            menu.button(20,Items.BELL,"End after first claim: "+p.endAfterClaim,List.of("The first successful claim ends the event.","Other deposits remain recoverable."),()->{setting(player.createCommandSourceStack(),"endafterclaim",Boolean.toString(!p.endAfterClaim));settings(player);});
            menu.button(22,Items.BARRIER,"Clear requirements",List.of(),()->{setting(player.createCommandSourceStack(),"clearrequired","");settings(player);});
            menu.button(24,Items.BARRIER,"Clear rewards",List.of("Also resets the money reward."),()->{setting(player.createCommandSourceStack(),"clearrewards","");settings(player);});
            for(int i=0;i<p.rewards.size();i++)menu.button(28+i,decode(p.rewards.get(i)),"Reward stack "+(i+1),List.of("Amount: "+decode(p.rewards.get(i)).getCount()),null);
            menu.button(49,Items.OAK_DOOR,"Close",List.of(),player::closeContainer);
            menu.button(51,Items.BARRIER,"Disable input chest",List.of("Future events use announcements only."),()->{setting(player.createCommandSourceStack(),"disable","");settings(player);});
        },false);
    }
    private void moneyDialog(ServerPlayer player){
        var field=new net.minecraft.server.dialog.Input("amount",new net.minecraft.server.dialog.input.TextInput(300,CombatService.text("Reward amount • 0 disables cash",service.config.eventColor),true,Long.toString(data.setup.money),32,Optional.empty()));
        var command=net.minecraft.server.dialog.action.ParsedTemplate.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,new com.google.gson.JsonPrimitive("/eventhost chest money $(amount)")).getOrThrow();
        var action=new net.minecraft.server.dialog.ActionButton(new net.minecraft.server.dialog.CommonButtonData(CombatService.text("Save",service.config.eventColor),140),Optional.of(new net.minecraft.server.dialog.action.CommandTemplate(command)));
        var common=new net.minecraft.server.dialog.CommonDialogData(CombatService.text("Event reward",service.config.eventColor),Optional.empty(),true,false,net.minecraft.server.dialog.DialogAction.CLOSE,List.of(),List.of(field));
        player.closeContainer();player.openDialog(net.minecraft.core.Holder.direct(new net.minecraft.server.dialog.MultiActionDialog(common,List.of(action),Optional.empty(),1)));
    }
    public void validateStart(){check();Plan p=data.setup;if(p.dimension!=null&&(p.required.isEmpty()||p.rewards.isEmpty()&&p.money==0))throw new IllegalArgumentException("Set at least one required item and an item or money reward, or disable the event chest.");}
    public void start(UUID event){if(data.setup.dimension==null)return;Data next=copy();Plan plan=CombatStore.JSON.fromJson(CombatStore.JSON.toJson(data.setup),Plan.class);plan.claimed.clear();next.contracts.put(event,plan);store(next);}
    private boolean live(UUID event){return service.events.active()&&event.equals(service.events.id())&&!data.completed.contains(event);}
    private boolean nearby(ServerPlayer p,Plan plan){return plan!=null&&plan.dimension.equals(p.level().dimension().identifier().toString())&&p.distanceToSqr(plan.x+.5,plan.y+.5,plan.z+.5)<=64&&p.level().getBlockEntity(new BlockPos(plan.x,plan.y,plan.z)) instanceof ChestBlockEntity;}
    public boolean use(ServerPlayer player,BlockPos pos){
        Plan plan=data.contracts.get(service.events.id());
        if(plan==null||!plan.dimension.equals(player.level().dimension().identifier().toString())||plan.x!=pos.getX()||plan.y!=pos.getY()||plan.z!=pos.getZ())return false;
        try{open(player);}catch(Exception e){tell(player,e.getMessage(),true);}return true;
    }
    public void open(ServerPlayer player){
        check();if(service.staff.frozen(player.getUUID())||player.isSpectator()||!player.isAlive())throw new IllegalArgumentException("You cannot use this chest right now.");
        if(!player.containerMenu.getCarried().isEmpty())throw new IllegalArgumentException("Put the item on your cursor away first.");
        Account saved=data.accounts.get(player.getUUID());UUID event=service.events.id();
        if(saved!=null&&saved.inputs.stream().anyMatch(v->!v.isEmpty())&&!Objects.equals(saved.event,event))event=saved.event;
        Plan plan=data.contracts.get(event);if(plan==null)throw new IllegalArgumentException("There is no event input chest or saved deposit to recover.");
        if(live(event)&&!nearby(player,plan))throw new IllegalArgumentException("Go to the event input chest to turn in items.");
        UUID selected=event;player.openMenu(new SimpleMenuProvider((id,inventory,p)->new InputMenu(id,player,selected,plan),CombatService.text("event input chest",service.config.eventColor)));
    }
    public void tick(){recoverJournal();if(!healthy)return;for(ServerPlayer p:service.server.getPlayerList().getPlayers())deliverCash(p);if(service.events.active()&&data.completed.contains(service.events.id()))try{service.events.claimed();}catch(RuntimeException e){failure("Claimed event could not be ended yet; retrying",e);}}
    public void join(ServerPlayer player){recoverJournal();deliverCash(player);Account a=data.accounts.get(player.getUUID());if(a!=null&&a.inputs.stream().anyMatch(v->!v.isEmpty()))tell(player,"Your event chest items are saved. Use /event chest to recover them.",false);}
    private void deliverCash(ServerPlayer player){
        if(!healthy||KingdomsMod.service==null)return;
        for(Cash award:new ArrayList<>(data.cash))if(award.player.equals(player.getUUID()))try{
            KingdomsMod.service.airDropMoney(award.id,null,award.player,award.amount);
            Data next=copy();next.cash.removeIf(c->c.id.equals(award.id));store(next);tell(player,"Event reward: "+award.amount+" paid to your wallet.",false);
        }catch(RuntimeException e){KingdomsCombat.LOG.error("Event cash reward remains pending for {}",player.getUUID(),e);break;}
    }
    private boolean saved(UUID player,UUID receipt){
        Path path=service.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player+".dat");if(!Files.exists(path))return false;
        try{return NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap()).getStringOr("kingdomscombat_event_receipt","").equals(receipt.toString());}
        catch(Exception e){throw new IllegalStateException("Cannot verify event chest player save.",e);}
    }
    private void recoverJournal(){
        try{Journal pending=CombatStore.read(journalFile,Journal.class,null);if(pending==null)return;if(pending.player==null||pending.receipt==null||pending.next==null)throw new IllegalStateException("Invalid event chest journal.");
            if(saved(pending.player,pending.receipt))store(pending.next);
            Files.delete(journalFile);healthy=true;
        }catch(Exception e){healthy=false;failure("Event chest transaction is locked pending recovery",e);}
    }
    private void failure(String message,Exception error){if(lastFailureLog==0||service.now()-lastFailureLog>=30000){lastFailureLog=service.now();KingdomsCombat.LOG.error(message,error);}}
    private void commit(ServerPlayer player,Data next){
        Journal journal=new Journal();journal.player=player.getUUID();journal.next=next;CombatStore.write(journalFile,journal);
        ((PenaltyReceipt)player).combat$eventReceipt(journal.receipt);
        ((AirDropPlayerSaveInvoker)service.server.getPlayerList()).kingdoms$saveAirDropPlayer(player);
        if(!saved(player.getUUID(),journal.receipt)){healthy=false;throw new IllegalStateException("Event transaction awaits player-save recovery. Reconnect before moving more items.");}
        store(next);try{Files.delete(journalFile);}catch(Exception e){healthy=false;throw new IllegalStateException("Event transaction awaits journal cleanup.",e);}
    }
    private void tell(ServerPlayer p,String text,boolean error){p.sendSystemMessage(CombatService.text(text,error?service.config.errorColor:service.config.eventColor));}
    final class InputMenu extends ChestMenu {
        final ServerPlayer player;final UUID event;final Plan plan;final SimpleContainer input;
        InputMenu(int id,ServerPlayer player,UUID event,Plan plan){this(id,player,event,plan,new SimpleContainer(27){@Override public boolean canPlaceItem(int slot,ItemStack stack){return slot<9&&!KingdomCores.isToken(stack)&&!Artifacts.marked(stack)&&!Artifacts.fixerMarked(stack)&&plan.required.stream().anyMatch(v->ItemStack.isSameItemSameComponents(stack,decode(v)));}});}
        private InputMenu(int id,ServerPlayer player,UUID event,Plan plan,SimpleContainer container){
            super(MenuType.GENERIC_9x3,id,player.getInventory(),container,3);this.player=player;this.event=event;this.plan=plan;input=container;
            for(int i=0;i<9;i++){Slot old=slots.get(i);Slot safe=new Slot(input,i,old.x,old.y){@Override public boolean mayPlace(ItemStack stack){return live(event)&&input.canPlaceItem(getContainerSlot(),stack);}};safe.index=old.index;slots.set(i,safe);}
            Account account=data.accounts.get(player.getUUID());if(account!=null&&account.event.equals(event))for(int i=0;i<9;i++)input.setItem(i,decode(account.inputs.get(i)));
            for(int i=9;i<27;i++)input.setItem(i,icon(Items.PAPER,"Click inventory stacks to deposit • Top row: your items"));
            for(int i=0;i<plan.required.size();i++)input.setItem(9+i,decode(plan.required.get(i)));
            for(int i=0;i<plan.rewards.size();i++)input.setItem(18+i,decode(plan.rewards.get(i)));
            input.setItem(26,icon(Items.EMERALD,"Claim reward • Money: "+plan.money));
        }
        @Override public boolean stillValid(Player p){return p==player&&p.isAlive()&&!service.staff.frozen(p.getUUID())&&(!live(event)||nearby(player,plan));}
        @Override public void clicked(int slot,int button,ContainerInput type,Player p){
            if(p!=player||!stillValid(p)||!healthy||Files.exists(journalFile))return;
            if(type!=ContainerInput.PICKUP&&type!=ContainerInput.QUICK_MOVE&&type!=ContainerInput.SWAP||slot<0)return;
            if(slot>=9&&slot<27){if(slot==26&&type==ContainerInput.PICKUP&&button==0)transaction(true,()->{});return;}
            if(slot>=0&&slot<9&&!live(event)&&!getCarried().isEmpty())return;
            transaction(false,()->{if(type==ContainerInput.PICKUP)quickMoveStack(p,slot);else super.clicked(slot,button,type,p);});
        }
        @Override public ItemStack quickMoveStack(Player p,int index){
            if(index<0||index>=slots.size()||index>=9&&index<27)return ItemStack.EMPTY;
            var slot=slots.get(index);ItemStack stack=slot.getItem(),before=stack.copy();if(stack.isEmpty())return ItemStack.EMPTY;
            if(index<9){if(!moveItemStackTo(stack,27,slots.size(),true))return ItemStack.EMPTY;}
            else if(!live(event)||!moveItemStackTo(stack,0,9,false))return ItemStack.EMPTY;
            if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();return before;
        }
        private void transaction(boolean claim,Runnable action){
            List<ItemStack> inventory=new ArrayList<>(),before=new ArrayList<>();for(int i=0;i<player.getInventory().getContainerSize();i++)inventory.add(player.getInventory().getItem(i).copy());for(int i=0;i<9;i++)before.add(input.getItem(i).copy());ItemStack carried=getCarried().copy();UUID oldReceipt=((PenaltyReceipt)player).combat$eventReceipt();
            boolean committed=false;
            try{check();Data next=copy();if(claim)claim(next);else action.run();
                Account a=new Account();a.event=event;for(int i=0;i<9;i++)a.inputs.add(encode(input.getItem(i)));next.accounts.put(player.getUUID(),a);
                commit(player,next);committed=true;broadcastChanges();if(claim){tell(player,"Required items accepted. Event reward claimed.",false);deliverCash(player);if(data.completed.contains(event))service.events.claimed();}
            }catch(Exception e){
                // Before a verified player save, the durable input ledger still describes the old inventory.
                Journal pending;boolean persisted;
                try{pending=CombatStore.read(journalFile,Journal.class,null);persisted=pending!=null&&saved(player.getUUID(),pending.receipt);}catch(Exception uncertain){healthy=false;tell(player,"Event chest save could not be verified. Reconnect after staff restore storage.",true);return;}
                if(!committed&&!persisted){for(int i=0;i<inventory.size();i++)player.getInventory().setItem(i,inventory.get(i));for(int i=0;i<9;i++)input.setItem(i,before.get(i));setCarried(carried);((PenaltyReceipt)player).combat$eventReceipt(oldReceipt);}
                if(pending!=null)healthy=false;tell(player,e.getMessage()==null?"Event chest is temporarily unavailable.":e.getMessage(),true);broadcastChanges();
            }
        }
        private void claim(Data next){
            if(!live(event)||!nearby(player,plan))throw new IllegalArgumentException("This event chest is no longer active.");
            Plan current=next.contracts.get(event);if(current.oncePerPlayer&&current.claimed.contains(player.getUUID()))throw new IllegalArgumentException("You have already claimed this event reward.");
            List<ItemStack> remaining=new ArrayList<>();for(int i=0;i<9;i++)remaining.add(input.getItem(i).copy());
            for(String encoded:plan.required){ItemStack requirement=decode(encoded);int count=requirement.getCount();for(ItemStack stack:remaining)if(ItemStack.isSameItemSameComponents(stack,requirement)){int n=Math.min(count,stack.getCount());stack.shrink(n);count-=n;}if(count>0)throw new IllegalArgumentException("Missing required items. Match the item type and components shown in the middle row.");}
            List<ItemStack> storage=new ArrayList<>();for(int i=0;i<36;i++)storage.add(player.getInventory().getItem(i).copy());
            for(String encoded:plan.rewards){ItemStack reward=decode(encoded);for(ItemStack slot:storage)if(ItemStack.isSameItemSameComponents(slot,reward)){int n=Math.min(reward.getCount(),Math.max(0,slot.getMaxStackSize()-slot.getCount()));slot.grow(n);reward.shrink(n);}for(int i=0;i<storage.size()&&!reward.isEmpty();i++)if(storage.get(i).isEmpty()){int n=Math.min(reward.getCount(),reward.getMaxStackSize());storage.set(i,reward.copyWithCount(n));reward.shrink(n);}if(!reward.isEmpty())throw new IllegalArgumentException("Make room in your inventory for the reward first.");}
            for(int i=0;i<9;i++)input.setItem(i,remaining.get(i));for(int i=0;i<36;i++)player.getInventory().setItem(i,storage.get(i));
            current.claimed.add(player.getUUID());if(current.endAfterClaim)next.completed.add(event);
            if(plan.money>0){Cash award=new Cash();award.player=player.getUUID();award.amount=plan.money;next.cash.add(award);}
        }
        @Override public void removed(Player p){super.removed(p);/* Inputs remain in the durable private account, never in physical chest storage. */}
    }
    private ItemStack icon(Item item,String title){ItemStack stack=new ItemStack(item);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,CombatService.text(title,service.config.eventColor));return stack;}
}
