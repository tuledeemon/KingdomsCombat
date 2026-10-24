package com.celik.kingdomscombat;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

public final class StaffMenu {
    private static CombatService s(){return KingdomsCombat.service;}
    private static StaffService tools(){return s().staff;}
    public static void open(ServerPlayer p,String title,Consumer<Menu> render){tools().require(p,"menu");p.openMenu(new SimpleMenuProvider((id,inv,who)->new Menu(id,inv,p,render),CombatService.text(title,"aqua")));}
    public static void home(ServerPlayer p,int page){open(p,"Staff moderation",m->{
        var players=s().server.getPlayerList().getPlayers().stream().sorted(Comparator.comparing(q->q.getGameProfile().name().toLowerCase(Locale.ROOT))).toList();
        m.button(4,Items.SPYGLASS,"Staff mode is active",List.of("Moderation tools are enabled","Off mode uses only the default group permissions"),null);
        int from=Math.max(0,page)*36;for(int n=from;n<Math.min(from+36,players.size());n++){var target=players.get(n);UUID id=target.getUUID();m.button(9+n-from,Items.PLAYER_HEAD,target.getGameProfile().name(),List.of("Health: "+Math.round(target.getHealth())+" / "+Math.round(target.getMaxHealth()),"World: "+target.level().dimension().identifier(),tools().frozen(id)?"Frozen for review":tools().muted(id)?"Muted":"Click to moderate"),()->player(p,id));}
        if(page>0)m.button(48,Items.ARROW,"Previous",List.of(),()->home(p,page-1));
        m.button(49,Items.REDSTONE,"Leave staff mode",List.of("Disable moderation tools","Restore default-group permissions"),()->s().toggleStaffMode(p));
        if(from+36<players.size())m.button(50,Items.ARROW,"Next",List.of(),()->home(p,page+1));
    });}
    public static void player(ServerPlayer p,UUID id){open(p,"Staff • "+tools().name(id),m->{
        var target=tools().online(id);
        m.button(4,Items.PLAYER_HEAD,tools().name(id),List.of("Health: "+Math.round(target.getHealth()),"Position: "+target.blockPosition().toShortString(),target.level().dimension().identifier().toString()),null);
        m.button(20,Items.PACKED_ICE,tools().frozen(id)?"Unfreeze player":"Freeze for review",List.of("Stops movement, combat and item actions","Logout is recorded; review survives reconnects"),()->{tools().freeze(p.createCommandSourceStack(),id,!tools().frozen(id));player(p,id);});
        m.button(22,Items.PAPER,"Warn player",List.of("Enter the warning in a dialog"),()->input(p,id,"warn"));
        m.button(24,Items.WRITABLE_BOOK,"Mute player",List.of("Choose a duration and reason"),()->input(p,id,"mute"));
        m.button(31,Items.BOOK,"Notes and history",List.of("View recorded moderation actions"),()->notes(p,id,0));
        m.button(38,Items.OAK_DOOR,"Kick player",List.of("Reason is required"),()->input(p,id,"kick"));
        m.button(40,Items.BARRIER,"Ban player",List.of("Temporary or permanent • reason required"),()->input(p,id,"ban"));
        m.button(42,Items.EMERALD,"Remove mute",List.of(),()->{tools().unmute(p.createCommandSourceStack(),id);player(p,id);});
        m.button(49,Items.ARROW,"Back",List.of(),()->home(p,0));
    });}
    public static void notes(ServerPlayer p,UUID id,int page){tools().require(p,"notes");open(p,"History • "+tools().name(id),m->{tools().require(p,"notes");var entries=new ArrayList<>(tools().notes(id));Collections.reverse(entries);for(int i=page*36;i<Math.min(entries.size(),(page+1)*36);i++){var note=entries.get(i);m.button(9+i-page*36,Items.PAPER,note.action,List.of(note.actor,Instant.ofEpochMilli(note.time).toString(),note.reason),null);}if(page>0)m.button(48,Items.ARROW,"Previous",List.of(),()->notes(p,id,page-1));m.button(49,Items.ARROW,"Back",List.of(),()->home(p,0));if((page+1)*36<entries.size())m.button(50,Items.ARROW,"Next",List.of(),()->notes(p,id,page+1));m.button(53,Items.WRITABLE_BOOK,"Add note",List.of(),()->input(p,id,"note"));});}
    private static void input(ServerPlayer p,UUID id,String action){tools().require(p,action.equals("note")?"notes":action);p.closeContainer();var fields=new ArrayList<net.minecraft.server.dialog.Input>();boolean duration=action.equals("mute")||action.equals("ban");if(duration)fields.add(new net.minecraft.server.dialog.Input("duration",new TextInput(300,CombatService.text("Duration: 30m, 1h30m, 7d or permanent","gold"),true,"30m",32,Optional.empty())));fields.add(new net.minecraft.server.dialog.Input("reason",new TextInput(300,CombatService.text("Reason","gold"),true,"",s().config.staffReasonMaxLength,Optional.empty())));String command="/staff "+action+" "+id+(duration?" $(duration)":"")+" $(reason)";var button=new ActionButton(new CommonButtonData(CombatService.text(action+" "+tools().name(id),"gold"),300),Optional.of(new CommandTemplate(ParsedTemplate.CODEC.parse(JsonOps.INSTANCE,new JsonPrimitive(command)).getOrThrow())));var common=new CommonDialogData(CombatService.text(action+" • "+tools().name(id),"aqua"),Optional.empty(),true,false,DialogAction.CLOSE,List.of(new PlainMessage(CombatService.text("This action is recorded in the staff audit log.","gray"),300)),fields);p.openDialog(Holder.direct(new MultiActionDialog(common,List.of(button),Optional.of(new ActionButton(new CommonButtonData(CombatService.text("Cancel","gray"),140),Optional.empty())),1)));}
    public static final class Menu extends ChestMenu {
        private final ServerPlayer viewer;private final Consumer<Menu> render;private final Map<Integer,Runnable> actions=new HashMap<>();private int ticks;
        Menu(int id,Inventory inventory,ServerPlayer p,Consumer<Menu> render){super(MenuType.GENERIC_9x6,id,inventory,new SimpleContainer(54),6);viewer=p;this.render=render;refresh();}
        private void refresh(){actions.clear();getContainer().clearContent();render.accept(this);}
        public void raw(int slot,ItemStack item){getContainer().setItem(slot,item.copy());}
        public void button(int slot,Item item,String title,List<String> lore,Runnable action){var stack=new ItemStack(item);stack.set(DataComponents.CUSTOM_NAME,CombatService.text(title,"aqua"));stack.set(DataComponents.LORE,new ItemLore(lore.stream().map(t->CombatService.text(t,"gray")).map(t->(net.minecraft.network.chat.Component)t).toList()));raw(slot,stack);if(action!=null)actions.put(slot,action);}
        @Override public boolean stillValid(Player p){return p==viewer&&s()!=null&&s().staffMode(viewer);}
        @Override public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}
        @Override public void clicked(int slot,int button,ContainerInput input,Player p){if(!stillValid(p)||input!=ContainerInput.PICKUP||button!=0)return;var action=actions.get(slot);if(action!=null)try{action.run();}catch(IllegalArgumentException e){viewer.sendSystemMessage(CombatService.text(e.getMessage(),"red"));}broadcastFullState();}
        @Override public void broadcastChanges(){if(!stillValid(viewer)){viewer.closeContainer();return;}if(++ticks%20==0)try{refresh();}catch(IllegalArgumentException e){viewer.closeContainer();viewer.sendSystemMessage(CombatService.text(e.getMessage(),"red"));return;}super.broadcastChanges();}
    }
}
