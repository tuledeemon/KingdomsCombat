package com.celik.kingdomscombat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import static net.minecraft.commands.Commands.*;

public final class ArenaCommands {
    static ArenaService s(){return KingdomsCombat.service.arenas;}
    public static void require(CommandSourceStack source,boolean admin){var service=KingdomsCombat.service;ArenaService.require(CombatService.allowed(source,admin?service.config.adminPermission:service.config.eventHostPermission,service.config.adminLevel),admin?"Administrator permission required.":"Event-host permission required.");}
    static int run(CommandContext<CommandSourceStack> c,boolean admin,Runnable action){try{require(c.getSource(),admin);action.run();c.getSource().sendSuccess(()->CombatService.text("Arena updated.","aqua"),false);return 1;}catch(Exception e){c.getSource().sendFailure(CombatService.text(e.getMessage()==null?"Arena action failed.":e.getMessage(),"red"));return 0;}}
    static int player(CommandContext<CommandSourceStack> c,java.util.function.Consumer<ServerPlayer> action){try{action.accept(c.getSource().getPlayerOrException());return 1;}catch(Exception e){c.getSource().sendFailure(CombatService.text(e.getMessage()==null?"Tournament action failed.":e.getMessage(),"red"));return 0;}}
    static ServerPlayer p(CommandContext<CommandSourceStack> c,String key){try{return EntityArgument.getPlayer(c,key);}catch(Exception e){throw new IllegalArgumentException("Choose an online player.");}}
    static ServerPlayer actor(CommandContext<CommandSourceStack> c){try{return c.getSource().getPlayerOrException();}catch(Exception e){throw new IllegalArgumentException("Use this command in game.");}}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        var player=literal("tournament").executes(c->player(c,p->s().menu(p)));
        player.then(literal("apply").executes(c->player(c,p->s().apply(p))));
        player.then(literal("invite").then(argument("player",EntityArgument.player()).executes(c->player(c,p->s().invite(p,p(c,"player"))))));
        player.then(literal("accept").executes(c->player(c,p->s().accept(p))));
        player.then(literal("leave").executes(c->player(c,p->s().withdraw(p))));
        player.then(literal("bracket").executes(c->player(c,p->ArenaUi.brackets(s(),p,0))));
        player.then(literal("entries").executes(c->player(c,p->ArenaUi.entries(s(),p,0))));
        player.then(literal("elo").executes(c->player(c,p->ArenaUi.leaderboard(s(),p))));dispatcher.register(player);
    }
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> host){
        var arena=literal("arena").requires(source->{try{require(source,true);return true;}catch(Exception e){return false;}}).executes(c->run(c,true,()->ArenaUi.admin(s(),actor(c))));
        for(int number:new int[]{1,2})arena.then(literal("pos"+number).executes(c->run(c,true,()->s().corner(actor(c),number))));
        arena.then(literal("claim").executes(c->run(c,true,()->s().claim())));
        arena.then(literal("unclaim").executes(c->run(c,true,()->s().unclaim())));
        arena.then(literal("spawn").then(argument("side",IntegerArgumentType.integer(1,2)).executes(c->run(c,true,()->s().spawn(actor(c),IntegerArgumentType.getInteger(c,"side"))))));
        arena.then(literal("setlocation").executes(c->run(c,true,()->s().setLocation(actor(c)))));
        arena.then(literal("kit").executes(c->run(c,true,()->s().kit(actor(c)))));
        var gate=literal("gate");for(boolean add:new boolean[]{true,false})gate.then(literal(add?"add":"remove").executes(c->run(c,true,()->s().gate(actor(c),add))));arena.then(gate);host.then(arena);
        host.then(literal("duel").then(argument("first",EntityArgument.player()).then(argument("second",EntityArgument.player()).executes(c->run(c,false,()->s().duel(p(c,"first"),p(c,"second")))))));
        var tournament=literal("tournament").executes(c->run(c,false,()->ArenaUi.admin(s(),actor(c))));
        var name=argument("name",StringArgumentType.string()).executes(c->run(c,false,()->open(c,null)));
        name.then(argument("gear",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of("own","kit"),b)).executes(c->run(c,false,()->open(c,gear(StringArgumentType.getString(c,"gear"))))));
        tournament.then(literal("open").then(argument("format",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of("1v1","2v2"),b)).then(name)));
        tournament.then(literal("gear").then(argument("mode",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of("own","kit"),b)).executes(c->run(c,false,()->s().setGear(gear(StringArgumentType.getString(c,"mode")))))));
        tournament.then(literal("add").then(argument("captain",EntityArgument.player()).executes(c->run(c,false,()->s().add(List.of(p(c,"captain"))))).then(argument("teammate",EntityArgument.player()).executes(c->run(c,false,()->s().add(List.of(p(c,"captain"),p(c,"teammate"))))))));
        tournament.then(literal("remove").then(argument("player",StringArgumentType.word()).executes(c->run(c,false,()->s().remove(KingdomsCombat.service.staff.target(StringArgumentType.getString(c,"player")))))));
        tournament.then(literal("bracket").then(argument("first",EntityArgument.player()).then(argument("second",EntityArgument.player()).executes(c->run(c,false,()->s().bracket(p(c,"first").getUUID(),p(c,"second").getUUID()))))));
        tournament.then(literal("start").executes(c->run(c,false,()->s().start())));
        tournament.then(literal("next").executes(c->run(c,false,()->s().next())));
        tournament.then(literal("stop").executes(c->run(c,false,()->s().cancel())));
        tournament.then(literal("winner").then(argument("captain",EntityArgument.player()).executes(c->run(c,false,()->s().finish(p(c,"captain").getUUID())))));
        host.then(tournament);
    }
    private static boolean gear(String mode){ArenaService.require(mode.equals("own")||mode.equals("kit"),"Choose own or kit.");return mode.equals("own");}
    private static void open(CommandContext<CommandSourceStack> c,Boolean own){String format=StringArgumentType.getString(c,"format");ArenaService.require(format.equals("1v1")||format.equals("2v2"),"Choose 1v1 or 2v2.");if(own==null)s().open(StringArgumentType.getString(c,"name"),format.equals("1v1")?1:2);else s().open(StringArgumentType.getString(c,"name"),format.equals("1v1")?1:2,own);}
    private ArenaCommands(){}
}
