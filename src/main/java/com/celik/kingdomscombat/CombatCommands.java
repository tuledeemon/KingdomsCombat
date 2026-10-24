package com.celik.kingdomscombat;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.arguments.EntityArgument;
import java.util.*;
import java.lang.reflect.Field;
import static net.minecraft.commands.Commands.*;

public final class CombatCommands {
    private CombatCommands(){}
    private static CombatService s(){return Objects.requireNonNull(KingdomsCombat.service,"Combat service is not ready.");}
    public static void register(CommandDispatcher<CommandSourceStack> d){
        d.register(literal("combat").executes(c->run(c,()->CombatMenu.status(c.getSource().getPlayerOrException()))));
        d.register(literal("event").executes(c->run(c,()->s().events.menu(c.getSource().getPlayerOrException()))).then(literal("chest").executes(c->run(c,()->s().events.chests.open(c.getSource().getPlayerOrException())))).then(literal("teleport").then(argument("event",StringArgumentType.word()).executes(c->run(c,()->s().events.travel(c.getSource().getPlayerOrException(),UUID.fromString(StringArgumentType.getString(c,"event"))))))));
        d.register(literal("staffmode").requires(source->source.getPlayer()!=null&&(s().staffMode(source.getPlayer())||s().permissions.authorize(source.getPlayer()))).executes(c->run(c,()->reply(c,s().toggleStaffMode(c.getSource().getPlayerOrException())?"Staff mode enabled. Use /staff for moderation tools.":"Staff mode disabled. Default-group permissions apply."))));
        StaffCommands.register(d);
        var root=literal("combatadmin").requires(source->CombatService.allowed(source,KingdomsCombat.service==null?"kingdomscombat.admin":s().config.adminPermission,KingdomsCombat.service==null?2:s().config.adminLevel)).executes(c->run(c,()->CombatMenu.admin(c.getSource().getPlayerOrException())));
        root.then(literal("reload").executes(c->run(c,()->{s().reload();reply(c,"Combat configuration reloaded.");})));
        root.then(literal("status").executes(c->run(c,()->reply(c,"Tracked players: "+s().ledger.data.tags.size()+". Pending logout penalties: "+s().ledger.data.logoutPenalties.size()))));
        root.then(literal("set").then(argument("key",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(CombatConfig.class.getFields()).filter(f->f.getType().isPrimitive()||f.getType()==String.class).map(Field::getName),b)).then(argument("value",StringArgumentType.greedyString()).executes(c->run(c,()->{set(StringArgumentType.getString(c,"key"),StringArgumentType.getString(c,"value"));reply(c,"Setting saved.");if(c.getSource().getPlayer()!=null)CombatMenu.admin(c.getSource().getPlayer());})))));
        root.then(literal("commands").then(literal("list").executes(c->run(c,()->reply(c,String.join(", ",s().config.blockedCommands))))).then(literal("add").then(argument("command",StringArgumentType.greedyString()).executes(c->run(c,()->command(StringArgumentType.getString(c,"command"),true))))).then(literal("remove").then(argument("command",StringArgumentType.greedyString()).executes(c->run(c,()->command(StringArgumentType.getString(c,"command"),false))))));
        root.then(literal("tag").then(argument("player",EntityArgument.player()).executes(c->run(c,()->tag(EntityArgument.getPlayer(c,"player"),s().config.durationSeconds))).then(argument("seconds",IntegerArgumentType.integer(1,86400)).executes(c->run(c,()->tag(EntityArgument.getPlayer(c,"player"),IntegerArgumentType.getInteger(c,"seconds")))))));
        root.then(literal("clear").then(argument("player",StringArgumentType.word()).executes(c->run(c,()->{UUID id=target(StringArgumentType.getString(c,"player"));s().ledger.clear(id);s().save();reply(c,"Tag cleared.");}))));
        root.then(literal("pardon").then(argument("player",StringArgumentType.word()).executes(c->run(c,()->{UUID id=target(StringArgumentType.getString(c,"player"));s().ledger.clear(id);s().ledger.data.logoutPenalties.remove(id);s().save();reply(c,"Logout penalty pardoned.");}))));
        d.register(root);
        var event=literal("eventhost").requires(source->CombatService.allowed(source,KingdomsCombat.service==null?"kingdomscombat.eventhost":s().config.eventHostPermission,KingdomsCombat.service==null?2:s().config.adminLevel));
        event.executes(c->run(c,()->reply(c,s().events.description())));
        event.then(literal("stop").executes(c->run(c,()->s().events.stop(c.getSource()))));
        event.then(literal("setlocation").executes(c->run(c,()->{s().events.setLocation(c.getSource());reply(c,"Event location set to your position.");})));
        event.then(literal("description").then(argument("text",StringArgumentType.greedyString()).executes(c->run(c,()->{s().events.setDescription(c.getSource(),StringArgumentType.getString(c,"text"));reply(c,"Event description saved.");}))));
        var chest=literal("chest").executes(c->run(c,()->{if(c.getSource().getPlayer()!=null)s().events.chests.settings(c.getSource().getPlayer());else reply(c,s().events.chests.status());}));
        chest.then(literal("set").executes(c->run(c,()->{s().events.chests.setChest(c.getSource());reply(c,"Event input chest selected.");})));
        chest.then(literal("required").executes(c->run(c,()->{s().events.chests.add(c.getSource(),false);reply(c,"Held stack added to requirements; the template item is not consumed.");})));
        chest.then(literal("reward").executes(c->run(c,()->{s().events.chests.add(c.getSource(),true);reply(c,"Held stack added to rewards; the template item is not consumed.");})));
        chest.then(literal("money").then(argument("amount",StringArgumentType.word()).executes(c->run(c,()->{s().events.chests.setting(c.getSource(),"money",StringArgumentType.getString(c,"amount"));reply(c,"Money reward saved.");}))));
        for(String key:List.of("endafterclaim","onceperplayer"))chest.then(literal(key).then(argument("enabled",BoolArgumentType.bool()).executes(c->run(c,()->{s().events.chests.setting(c.getSource(),key,Boolean.toString(BoolArgumentType.getBool(c,"enabled")));reply(c,"Chest claim rule saved.");}))));
        for(String key:List.of("clearrequired","clearrewards","disable"))chest.then(literal(key).executes(c->run(c,()->{s().events.chests.setting(c.getSource(),key,"");reply(c,"Chest setup updated.");})));
        event.then(chest);
        event.then(argument("name",StringArgumentType.string()).then(argument("time",StringArgumentType.word()).executes(c->run(c,()->s().events.start(c.getSource(),StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"time"))))));
        d.register(event);
    }
    private static int run(CommandContext<CommandSourceStack> c,Action action){try{action.run();return 1;}catch(Exception e){c.getSource().sendFailure(CombatService.text(e.getMessage()==null?"Action failed.":e.getMessage(),"red"));return 0;}}
    private interface Action {void run()throws Exception;}
    private static void reply(CommandContext<CommandSourceStack> c,String text){c.getSource().sendSuccess(()->CombatService.text(text,"aqua"),false);}
    private static UUID target(String name){ServerPlayer p=s().server.getPlayerList().getPlayerByName(name);if(p!=null)return p.getUUID();try{return UUID.fromString(name);}catch(Exception e){throw new IllegalArgumentException("Use an online player name or UUID.");}}
    static void tag(ServerPlayer p,int seconds){s().ledger.manual(p.getUUID(),s().now(),seconds);s().save();}
    static CombatConfig copy(){return CombatStore.JSON.fromJson(CombatStore.JSON.toJson(s().config),CombatConfig.class);}
    public static void set(String key,String value)throws ReflectiveOperationException{
        CombatConfig next=copy();Field f=CombatConfig.class.getField(key);Class<?> t=f.getType();
        if(t==boolean.class){if(!value.equals("true")&&!value.equals("false"))throw new IllegalArgumentException("Use true or false.");f.setBoolean(next,Boolean.parseBoolean(value));}
        else if(t==int.class)f.setInt(next,Integer.parseInt(value));else if(t==double.class)f.setDouble(next,Double.parseDouble(value));else if(t==float.class)f.setFloat(next,Float.parseFloat(value));else if(t==String.class)f.set(next,value);else throw new IllegalArgumentException("Use the commands add/remove menu for command rules.");s().replaceConfig(next);
    }
    public static void command(String command,boolean add){var next=copy();String normalized=CommandRules.normalize(command);if(normalized.isBlank())throw new IllegalArgumentException("Enter a command name or path.");if(add){if(!next.blockedCommands.contains(normalized))next.blockedCommands.add(normalized);}else next.blockedCommands.removeIf(v->CommandRules.normalize(v).equals(normalized));s().replaceConfig(next);}
}
