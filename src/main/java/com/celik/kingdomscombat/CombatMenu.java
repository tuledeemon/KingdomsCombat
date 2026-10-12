package com.celik.kingdomscombat;
import com.celik.kingdoms.Ui;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.core.Holder;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.dialog.action.*;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.util.*;

public final class CombatMenu {
    private CombatMenu(){}
    private static CombatService s(){return KingdomsCombat.service;}
    public static void status(ServerPlayer p){Ui.open(p,"Combat status",m->{var status=s().status(p);m.button(13,Items.CLOCK,"Time remaining: "+status.seconds()+"s",List.of("Reasons: "+(status.active()?s().reasons(status):"None"),"Combat: "+Ui.clock(status.combatMillis()),"War: "+Ui.clock(status.warMillis()),"AirDrop: "+Ui.clock(status.airDropMillis())),null);m.button(29,Items.ENDER_PEARL,"Teleport commands",List.of(s().allowTeleport(p,false)?"Available":"Blocked until the timers end"),null);m.button(33,Items.TOTEM_OF_UNDYING,"Logging out",List.of(TagLedger.penalty(status,s().config)?"Logging out now kills you":"No logout penalty active","Opponents: "+status.opponents().size()),null);m.button(49,Items.ARROW,"Kingdom menu",List.of(),()->Ui.home(p));},false);}
    private static void require(ServerPlayer p){if(!CombatService.admin(p.createCommandSourceStack(),s().config.adminLevel))throw new IllegalArgumentException("Administrator permission required.");}
    public static void admin(ServerPlayer p){require(p);Ui.open(p,"Combat settings",m->{require(p);var c=s().config;
        m.button(10,Items.IRON_SWORD,"Combat time: "+c.durationSeconds+"s",List.of("PvP hits tag both players"),()->input(p,"durationSeconds","Combat duration",Integer.toString(c.durationSeconds)));
        m.button(12,Items.NETHERITE_SWORD,"War time: "+c.warLockSeconds+"s",List.of("Refreshes during active war","Preparation is allowed"),()->input(p,"warLockSeconds","War cooldown",Integer.toString(c.warLockSeconds)));
        m.button(14,Items.CHEST,"AirDrop time: "+c.airDropLockSeconds+"s",List.of("Refreshes before the crate is won"),()->input(p,"airDropLockSeconds","AirDrop cooldown",Integer.toString(c.airDropLockSeconds)));
        m.button(16,Items.COMPASS,"AirDrop radius: "+c.airDropRadius,List.of("Horizontal distance by default"),()->input(p,"airDropRadius","AirDrop radius",Double.toString(c.airDropRadius)));
        m.button(28,Items.PAPER,"Action bar: "+c.actionBar,List.of("Disable to use placeholders elsewhere"),()->toggle(p,"actionBar",!c.actionBar));
        m.button(30,Items.TOTEM_OF_UNDYING,"Logout death: "+c.killOnCombatLogout,List.of("Applies to PvP combat tags"),()->toggle(p,"killOnCombatLogout",!c.killOnCombatLogout));
        m.button(32,Items.ENDER_PEARL,"Blocked commands",List.of(c.blockedCommands.size()+" command rules","Add or remove through a dialog"),()->commands(p));
        m.button(34,Items.BELL,"Sounds: "+c.sounds,List.of("Tag start/end feedback"),()->toggle(p,"sounds",!c.sounds));
        m.button(40,Items.WRITABLE_BOOK,"All settings",List.of("Every scalar setting is available","Enter a key and value in a dialog","Collections can also be edited in JSON"),()->allSettings(p));
        m.button(49,Items.ARROW,"Your status",List.of(),()->status(p));
    },false);}
    private static void toggle(ServerPlayer p,String key,boolean value){require(p);try{CombatCommands.set(key,Boolean.toString(value));admin(p);}catch(ReflectiveOperationException e){throw new IllegalArgumentException(e);}}
    private static ActionButton button(String label,String command){return new ActionButton(new CommonButtonData(CombatService.text(label,"gold"),140),Optional.of(new CommandTemplate(ParsedTemplate.CODEC.parse(JsonOps.INSTANCE,new JsonPrimitive(command)).getOrThrow())));}
    private static void dialog(ServerPlayer p,String title,List<net.minecraft.server.dialog.Input> inputs,List<ActionButton> actions){require(p);p.closeContainer();var common=new CommonDialogData(CombatService.text(title,"gold"),Optional.empty(),true,false,DialogAction.CLOSE,List.of(new PlainMessage(CombatService.text("Changes apply immediately and are saved.","gray"),300)),inputs);p.openDialog(Holder.direct(new MultiActionDialog(common,actions,Optional.of(new ActionButton(new CommonButtonData(CombatService.text("Cancel","gray"),140),Optional.empty())),actions.size())));}
    private static net.minecraft.server.dialog.Input field(String id,String label,String initial,int max){return new net.minecraft.server.dialog.Input(id,new TextInput(300,CombatService.text(label,"gold"),true,initial,max,Optional.empty()));}
    private static void input(ServerPlayer p,String key,String label,String current){dialog(p,label,List.of(field("value",label,current,64)),List.of(button("Save","/combatadmin set "+key+" $(value)")));}
    private static void allSettings(ServerPlayer p){dialog(p,"Change combat setting",List.of(field("key","Setting key","durationSeconds",64),field("value","Value","20",512)),List.of(button("Save","/combatadmin set $(key) $(value)")));}
    private static void commands(ServerPlayer p){dialog(p,"Blocked commands",List.of(field("command","Command name or path","home",256)),List.of(button("Add","/combatadmin commands add $(command)"),button("Remove","/combatadmin commands remove $(command)")));}
}
