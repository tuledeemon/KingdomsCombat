package com.celik.kingdomscombat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import static net.minecraft.commands.Commands.*;

final class EventAreaCommands {
    static void attach(LiteralArgumentBuilder<CommandSourceStack> host){
        var area=literal("area").requires(source->{try{ArenaCommands.require(source,true);return true;}catch(Exception e){return false;}}).executes(c->run(c,()->s().menu(ArenaCommands.actor(c))));
        for(int n:new int[]{1,2})area.then(literal("pos"+n).executes(c->run(c,()->{var p=ArenaCommands.actor(c);s().corner(p,n,p.blockPosition());})).then(argument("position",BlockPosArgument.blockPos()).executes(c->run(c,()->{s().corner(ArenaCommands.actor(c),n,BlockPosArgument.getBlockPos(c,"position"));}))));
        area.then(literal("claim").executes(c->run(c,()->s().claim(ArenaCommands.actor(c),null))).then(argument("name",StringArgumentType.greedyString()).executes(c->run(c,()->s().claim(ArenaCommands.actor(c),StringArgumentType.getString(c,"name"))))));
        area.then(literal("list").executes(c->run(c,()->EventAreaUi.list(s(),ArenaCommands.actor(c),0))));
        area.then(literal("unclaim").executes(c->run(c,()->{var p=ArenaCommands.actor(c);var selected=s().at(p.level(),p.blockPosition());if(selected==null)throw new IllegalArgumentException("Stand inside the event area to unclaim it, or select it from the area list.");s().unclaim(p,selected.id);})));host.then(area);
    }
    private static int run(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c,Runnable action){try{ArenaCommands.require(c.getSource(),true);action.run();c.getSource().sendSuccess(()->CombatService.text("Event area updated.","aqua"),false);return 1;}catch(Exception e){c.getSource().sendFailure(CombatService.text(e.getMessage()==null?"Event area action failed.":e.getMessage(),"red"));return 0;}}
    private static EventAreas s(){return KingdomsCombat.service.areas;}
    private EventAreaCommands(){}
}
