package com.celik.kingdomscombat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import static net.minecraft.commands.Commands.*;

public final class StaffCommands {
    private static StaffService tools(){return KingdomsCombat.service.staff;}
    private static boolean access(CommandSourceStack s){return s.getPlayer()==null||KingdomsCombat.service!=null&&KingdomsCombat.service.staffMode(s.getPlayer());}
    private interface Action {void run()throws Exception;}
    private static int run(CommandContext<CommandSourceStack> c,Action action){try{action.run();return 1;}catch(Exception e){c.getSource().sendFailure(CombatService.text(e.getMessage()==null?"Staff action failed.":e.getMessage(),"red"));return 0;}}
    private static UUID target(CommandContext<CommandSourceStack> c){return tools().target(StringArgumentType.getString(c,"player"));}
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack,String> player(){return argument("player",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(KingdomsCombat.service.server.getPlayerList().getPlayers().stream().map(p->p.getGameProfile().name()),b));}
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack,String> reason(){return argument("reason",StringArgumentType.greedyString());}
    public static long duration(String raw){
        if(raw.equalsIgnoreCase("permanent")||raw.equalsIgnoreCase("perm"))return -1;
        var matcher=Pattern.compile("([0-9]+(?:\\.[0-9]+)?)([smhdw])",Pattern.CASE_INSENSITIVE).matcher(raw);int end=0;BigDecimal result=BigDecimal.ZERO;
        while(matcher.find()){if(matcher.start()!=end)throw new IllegalArgumentException("Use a duration such as 30m, 1h30m, 7d or permanent.");end=matcher.end();long factor=switch(matcher.group(2).toLowerCase(Locale.ROOT)){case "m"->60;case "h"->3600;case "d"->86400;case "w"->604800;default->1;};result=result.add(new BigDecimal(matcher.group(1)).multiply(BigDecimal.valueOf(factor)));}
        if(end!=raw.length()||end==0||result.compareTo(BigDecimal.ONE)<0||result.compareTo(BigDecimal.valueOf(315360000))>0)throw new IllegalArgumentException("Use a duration from 1 second to 10 years, or permanent.");return result.longValue();
    }
    public static void register(CommandDispatcher<CommandSourceStack> d){
        var root=literal("staff").requires(StaffCommands::access).executes(c->run(c,()->StaffMenu.home(c.getSource().getPlayerOrException(),0)));
        root.then(literal("freeze").then(player().executes(c->run(c,()->tools().freeze(c.getSource(),target(c),true)))));
        root.then(literal("unfreeze").then(player().executes(c->run(c,()->tools().freeze(c.getSource(),target(c),false)))));
        root.then(literal("warn").then(player().then(reason().executes(c->run(c,()->tools().warn(c.getSource(),target(c),StringArgumentType.getString(c,"reason")))))));
        root.then(literal("kick").then(player().then(reason().executes(c->run(c,()->tools().kick(c.getSource(),target(c),StringArgumentType.getString(c,"reason")))))));
        root.then(literal("mute").then(player().then(argument("duration",StringArgumentType.word()).then(reason().executes(c->run(c,()->tools().mute(c.getSource(),target(c),duration(StringArgumentType.getString(c,"duration")),StringArgumentType.getString(c,"reason"))))))));
        root.then(literal("unmute").then(player().executes(c->run(c,()->tools().unmute(c.getSource(),target(c))))));
        root.then(literal("ban").then(player().then(argument("duration",StringArgumentType.word()).then(reason().executes(c->run(c,()->tools().ban(c.getSource(),target(c),duration(StringArgumentType.getString(c,"duration")),StringArgumentType.getString(c,"reason"))))))));
        root.then(literal("unban").then(player().executes(c->run(c,()->tools().unban(c.getSource(),target(c))))));
        root.then(literal("notes").then(player().executes(c->run(c,()->StaffMenu.notes(c.getSource().getPlayerOrException(),target(c),0)))));
        root.then(literal("note").then(player().then(reason().executes(c->run(c,()->tools().note(c.getSource(),target(c),StringArgumentType.getString(c,"reason")))))));
        root.then(literal("chat").then(argument("message",StringArgumentType.greedyString()).executes(c->run(c,()->tools().staffChat(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"message"))))));
        d.register(root);
        d.register(literal("sc").requires(StaffCommands::access).then(argument("message",StringArgumentType.greedyString()).executes(c->run(c,()->tools().staffChat(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"message"))))));
    }
}
