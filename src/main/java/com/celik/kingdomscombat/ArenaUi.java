package com.celik.kingdomscombat;

import com.celik.kingdoms.Ui;
import net.minecraft.core.Holder;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.util.*;

final class ArenaUi {
    static void player(ArenaService s,ServerPlayer p){Ui.open(p,"Tournaments & duels",m->{
        var e=s.data.event;var r=s.rating(p.getUUID());
        m.button(4,Items.GOLD_INGOT,"Your Elo: "+r.elo,List.of("Shared 1v1 / 2v2 ladder",r.wins+" wins • "+r.losses+" losses"),null);
        m.button(13,Items.IRON_SWORD,e==null?"No tournament":e.name,List.of(e==null?"Staff can open applications":e.phase+" • "+e.teamSize+"v"+e.teamSize,e==null?"":e.entries.size()+" complete entries","Only current fighters can build or PvP"),null);
        if(s.active()&&e.phase==ArenaRules.Phase.APPLICATIONS){
            if(e.teamSize==1)m.button(28,Items.BANNER.white(),"Apply",List.of("Optional: banner existing kit shields","Without a banner, use the normal kit","Latest excess entries are excluded"),()->{s.apply(p);player(s,p);});
            else{m.button(28,Items.BANNER.white(),"Invite a teammate",List.of("Banners are optional for both players","Teammate must accept"),()->input(p,"Invite teammate","Player name","/tournament invite $(value)"));m.button(30,Items.DYE.lime(),"Accept team invitation",List.of("Optional: banner existing kit shields"),()->{s.accept(p);player(s,p);});}
        }
        if(s.active())m.button(22,Items.ENDER_PEARL,"Teleport to arena",List.of(s.data.setup.teleportSpot==null?"Staff have not set a destination":"Visit the arena viewing area","Combat, war and AirDrop locks still apply"),s.data.setup.teleportSpot==null?null:()->s.travel(p,e.id));
        m.button(37,Items.NAME_TAG,"Applicants",List.of("Application order and complete teams"),()->entries(s,p,0));
        m.button(32,Items.WRITABLE_BOOK,"Brackets",List.of("Current match and waiting pairs"),()->brackets(s,p,0));
        m.button(34,Items.GOLD_BLOCK,"Elo leaderboard",List.of("One ladder for duels and doubles"),()->leaderboard(s,p));
        if(s.active())m.button(40,Items.DYE.red(),s.reserved(p.getUUID())?"Forfeit match":"Withdraw application",List.of(s.reserved(p.getUUID())?"Counts as a loss":"Removes your complete team entry"),()->Ui.confirm(p,"Leave tournament",List.of("Confirm withdrawal or match forfeit"),()->{s.withdraw(p);player(s,p);}));
        if(s.admin(p)||CombatService.allowed(p.createCommandSourceStack(),s.service.config.eventHostPermission,s.service.config.adminLevel))m.button(49,Items.COMMAND_BLOCK,"Arena administration",List.of(),()->admin(s,p));
    },false);}
    static void brackets(ArenaService s,ServerPlayer p,int page){Ui.open(p,"Tournament bracket",m->{var e=s.data.event;if(e==null){m.button(22,Items.PAPER,"No tournament",List.of(),null);return;}int slot=10;var match=s.data.match;if(match!=null)m.button(4,Items.NETHERITE_SWORD,"Current • "+s.team(match.pair.a)+" vs "+s.team(match.pair.b),List.of(match.phase.toString(),"Only these fighters have arena permissions"),null);List<ArenaRules.Pair> pairs=e.queue;for(int i=page*21;i<Math.min(pairs.size(),(page+1)*21);i++){var pair=pairs.get(i);int target=10+(i-page*21)/7*9+(i-page*21)%7;m.button(target,Items.PAPER,s.team(pair.a)+" vs "+s.team(pair.b),List.of("Round "+e.round),null);}if(e.champion!=null)m.button(40,Items.GOLD_BLOCK,"Winner • "+s.team(e.champion),List.of(),null);if(page>0)m.button(45,Items.ARROW,"Previous",List.of(),()->brackets(s,p,page-1));if((page+1)*21<pairs.size())m.button(53,Items.ARROW,"Next",List.of(),()->brackets(s,p,page+1));m.button(49,Items.ARROW,"Back",List.of(),()->player(s,p));},false);}
    static void entries(ArenaService s,ServerPlayer p,int page){Ui.open(p,"Tournament applicants",m->{var e=s.data.event;if(e==null)return;List<ArenaRules.Entry> list=new ArrayList<>(e.entries.values());boolean host=CombatService.allowed(p.createCommandSourceStack(),s.service.config.eventHostPermission,s.service.config.adminLevel);for(int i=page*21;i<Math.min(list.size(),(page+1)*21);i++){var entry=list.get(i);int n=i-page*21,slot=10+n/7*9+n%7;m.button(slot,Items.NAME_TAG,"#"+(i+1)+" • "+s.team(entry.captain),List.of(e.teamSize+"v"+e.teamSize,host&&e.phase==ArenaRules.Phase.APPLICATIONS?"Click to remove this application":"Earliest applications have priority"),host&&e.phase==ArenaRules.Phase.APPLICATIONS?()->Ui.confirm(p,"Remove application",List.of(s.team(entry.captain)),()->{ArenaCommands.require(p.createCommandSourceStack(),false);s.remove(entry.captain);entries(s,p,page);}):null);}if(page>0)m.button(45,Items.ARROW,"Previous",List.of(),()->entries(s,p,page-1));if((page+1)*21<list.size())m.button(53,Items.ARROW,"Next",List.of(),()->entries(s,p,page+1));m.button(49,Items.ARROW,"Back",List.of(),()->player(s,p));},false);}
    static void leaderboard(ArenaService s,ServerPlayer p){Ui.open(p,"Arena Elo",m->{var rows=new ArrayList<>(s.data.ratings.entrySet());rows.sort(Comparator.<Map.Entry<UUID,ArenaRules.Rating>>comparingInt(e->e.getValue().elo).reversed().thenComparing(e->e.getKey().toString()));for(int i=0;i<Math.min(7,rows.size());i++){var row=rows.get(i);m.button(19+i,Items.GOLD_INGOT,"#"+(i+1)+" • "+s.service.staff.name(row.getKey()),List.of(row.getValue().elo+" Elo",row.getValue().wins+" wins • "+row.getValue().losses+" losses"),null);}m.button(49,Items.ARROW,"Back",List.of(),()->player(s,p));},false);}
    static void admin(ArenaService s,ServerPlayer p){ArenaCommands.require(p.createCommandSourceStack(),false);Ui.open(p,"Arena administration",m->{
        if(s.admin(p)){
        m.button(10,Items.WOODEN_AXE,"First corner",List.of("Use your current block position"),()->{ArenaCommands.require(p.createCommandSourceStack(),true);s.corner(p,1);admin(s,p);});
        m.button(11,Items.WOODEN_AXE,"Second corner",List.of("Use your current block position","Include arena floor, walls and spectator areas"),()->{ArenaCommands.require(p.createCommandSourceStack(),true);s.corner(p,2);admin(s,p);});
        m.button(12,Items.GRASS_BLOCK,"Claim selected arena",List.of("Protects the selected cuboid","Must be in wilderness"),()->{ArenaCommands.require(p.createCommandSourceStack(),true);s.claim();admin(s,p);});
        m.button(14,Items.WOOL.blue(),"Team 1 spawn",List.of("Current position and facing"),()->{ArenaCommands.require(p.createCommandSourceStack(),true);s.spawn(p,1);admin(s,p);});
        m.button(15,Items.WOOL.red(),"Team 2 spawn",List.of("Current position and facing"),()->{ArenaCommands.require(p.createCommandSourceStack(),true);s.spawn(p,2);admin(s,p);});
        m.button(16,Items.CHEST,"Save kit",List.of("Copies your inventory, armor and offhand","Tracked artifacts and storage items are rejected"),()->{ArenaCommands.require(p.createCommandSourceStack(),true);s.kit(p);admin(s,p);});
        m.button(21,Items.ENDER_PEARL,"Set public teleport spot",List.of("Use your current position","Choose safe spectator ground"),()->{ArenaCommands.require(p.createCommandSourceStack(),true);s.setLocation(p);admin(s,p);});
        m.button(19,Items.IRON_BARS,"Add gate block",List.of("Look at a block within six blocks",s.data.setup.gates.size()+" saved gate blocks","Gates open when the countdown ends"),()->{ArenaCommands.require(p.createCommandSourceStack(),true);s.gate(p,true);admin(s,p);});
        m.button(20,Items.SHEARS,"Remove gate block",List.of("Look at a saved gate block"),()->{ArenaCommands.require(p.createCommandSourceStack(),true);s.gate(p,false);admin(s,p);});
        }
        m.button(22,Items.IRON_SWORD,"Open 1v1 applications",List.of(),()->input(p,"Open 1v1 tournament","Event name","/eventhost tournament open 1v1 \"$(value)\""));
        m.button(23,Items.DIAMOND_SWORD,"Open 2v2 applications",List.of(),()->input(p,"Open 2v2 tournament","Event name","/eventhost tournament open 2v2 \"$(value)\""));
        m.button(28,Items.DYE.lime(),"Close applications & start",List.of("Keep earliest power-of-two entries","Manual pairs first; auto-pair the rest"),()->{ArenaCommands.require(p.createCommandSourceStack(),false);s.start();admin(s,p);});
        m.button(29,Items.CLOCK,"Next / replay match",List.of("Use after a pause or timeout"),()->{ArenaCommands.require(p.createCommandSourceStack(),false);s.next();admin(s,p);});
        m.button(30,Items.NAME_TAG,"Pick fighters / add team",List.of("One player for 1v1; two for 2v2"),()->players(p,"Add tournament entry","/eventhost tournament add $(first) $(second)"));
        m.button(32,Items.PAPER,"Set manual opening pair",List.of("Enter two registered captain names","Unpaired entries are matched automatically"),()->players(p,"Manual bracket","/eventhost tournament bracket $(first) $(second)"));
        m.button(25,Items.NETHERITE_SWORD,"Host a 1v1",List.of("Select two online fighters"),()->players(p,"Select duel fighters","/eventhost duel $(first) $(second)"));
        m.button(31,Items.WRITABLE_BOOK,"Bracket",List.of(),()->brackets(s,p,0));
        m.button(34,Items.BARRIER,"Stop tournament",List.of("Restore all original inventories","No Elo for an unfinished match"),()->Ui.confirm(p,"Stop tournament",List.of("Current match will be voided"),()->{ArenaCommands.require(p.createCommandSourceStack(),false);s.cancel();admin(s,p);}));
        if(s.data.match!=null){var pair=s.data.match.pair;m.button(37,Items.WOOL.blue(),"Award win • "+s.team(pair.a),List.of("Host adjudication updates Elo"),()->{ArenaCommands.require(p.createCommandSourceStack(),false);s.finish(pair.a);admin(s,p);});m.button(43,Items.WOOL.red(),"Award win • "+s.team(pair.b),List.of("Host adjudication updates Elo"),()->{ArenaCommands.require(p.createCommandSourceStack(),false);s.finish(pair.b);admin(s,p);});}
        m.button(49,Items.ARROW,"Player view",List.of(),()->player(s,p));
    },false);}
    private static void players(ServerPlayer p,String title,String command){p.closeContainer();var fields=List.of(new net.minecraft.server.dialog.Input("first",new TextInput(300,CombatService.text("First player / captain","gold"),true,"",16,Optional.empty())),new net.minecraft.server.dialog.Input("second",new TextInput(300,CombatService.text("Second player / teammate (blank for solo entry)","gold"),true,"",16,Optional.empty())));var common=new CommonDialogData(CombatService.text(title,"gold"),Optional.empty(),true,false,DialogAction.CLOSE,List.of(),fields);var submit=new ActionButton(new CommonButtonData(CombatService.text("Confirm","gold"),150),Optional.of(new CommandTemplate(ParsedTemplate.CODEC.parse(JsonOps.INSTANCE,new JsonPrimitive(command)).getOrThrow())));p.openDialog(Holder.direct(new MultiActionDialog(common,List.of(submit),Optional.empty(),1)));}
    private static void input(ServerPlayer p,String title,String label,String command){p.closeContainer();var common=new CommonDialogData(CombatService.text(title,"gold"),Optional.empty(),true,false,DialogAction.CLOSE,List.of(new PlainMessage(CombatService.text("Enter the details below.","gray"),300)),List.of(new net.minecraft.server.dialog.Input("value",new TextInput(300,CombatService.text(label,"gold"),true,"",64,Optional.empty()))));var submit=new ActionButton(new CommonButtonData(CombatService.text("Confirm","gold"),150),Optional.of(new CommandTemplate(ParsedTemplate.CODEC.parse(JsonOps.INSTANCE,new JsonPrimitive(command)).getOrThrow())));p.openDialog(Holder.direct(new MultiActionDialog(common,List.of(submit),Optional.empty(),1)));}
    private ArenaUi(){}
}
