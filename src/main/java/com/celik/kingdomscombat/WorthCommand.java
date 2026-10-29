package com.celik.kingdomscombat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.reazip.economycraft.EconomyCraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.stream.Stream;
import static net.minecraft.commands.Commands.*;

/** Read-only estimates from exactly the same item validation and curve as /sell. */
public final class WorthCommand {
    public record View(ItemStack item,String key,long base,long unit,int stackSize,long stackTotal,int heldCount,long heldTotal){}
    static View view(SellMarket market,ItemStack item,boolean held){
        if(!market.config.enabled)throw new IllegalArgumentException("The sell market is disabled.");
        var price=market.price(item);
        if(price==null)throw new IllegalArgumentException("This item cannot be sold. Named, protected, damaged or filled items may be blocked.");
        int size=item.getMaxStackSize();long unit=market.quote(List.of(item.copyWithCount(1))).total();
        long total=market.quote(List.of(item.copyWithCount(size))).total();
        int count=held?item.getCount():0;long heldTotal=held?market.quote(List.of(item.copy())).total():0;
        return new View(item.copy(),price.key(),price.base(),unit,size,total,count,heldTotal);
    }
    static View named(SellMarket market,String input){
        String key=input.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+","_");
        Identifier id=Identifier.tryParse(key);if(id==null)throw new IllegalArgumentException("Use an item name such as diamond, iron_ingot or minecraft:diamond.");
        key=id.toString();var configured=market.prices.get(key);
        if(configured==null||configured.unit_sell<1)throw new IllegalArgumentException("No sell price for "+key+".");
        var manager=EconomyCraft.getManager(market.service.server);if(manager==null)throw new IllegalArgumentException("EconomyCraft is unavailable.");
        var entry=manager.getPrices().findByKey(key);if(entry==null)throw new IllegalArgumentException("EconomyCraft does not recognize "+key+".");
        return view(market,manager.getPrices().createPrototype(entry),false);
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        var root=(com.celik.kingdomscombat.mixin.BalanceCommandAccessor)(Object)dispatcher.getRoot();root.combat$children().remove("worth");root.combat$literals().remove("worth");root.combat$arguments().remove("worth");
        dispatcher.register(literal("worth").executes(c->show(c,null)).then(argument("item",StringArgumentType.greedyString()).suggests((c,b)->{
            var service=KingdomsCombat.service;if(service==null)return b.buildFuture();
            return SharedSuggestionProvider.suggest(service.sellMarket.prices.entrySet().stream().filter(e->e.getValue().unit_sell>0).flatMap(e->e.getKey().startsWith("minecraft:")?Stream.of(e.getKey(),e.getKey().substring(10)):Stream.of(e.getKey())),b);
        }).executes(c->show(c,StringArgumentType.getString(c,"item")))));
    }
    private static int show(CommandContext<CommandSourceStack> context,String item){
        CommandSourceStack source=context.getSource();
        try{
            var service=KingdomsCombat.service;if(service==null)throw new IllegalArgumentException("The sell market is not ready.");
            var player=source.getPlayer();if(player!=null&&!me.lucko.fabric.api.permissions.v0.Permissions.check(player,"kingdomscombat.worth",true))throw new IllegalArgumentException("You do not have permission to check item worth.");
            View quote;
            if(item==null){if(player==null)throw new IllegalArgumentException("Console: use worth <itemname>.");ItemStack held=player.getMainHandItem();if(held.isEmpty())throw new IllegalArgumentException("Hold an item or use /worth <itemname>.");quote=view(service.sellMarket,held,true);}
            else quote=named(service.sellMarket,item);
            source.sendSuccess(()->CombatService.text("Worth • "+quote.item.getHoverName().getString(),"gold"),false);
            source.sendSuccess(()->CombatService.text("One item: "+EconomyCraft.formatMoney(quote.unit)+" • Stack ("+quote.stackSize+"): "+EconomyCraft.formatMoney(quote.stackTotal),"aqua"),false);
            if(quote.heldCount>0)source.sendSuccess(()->CombatService.text("Held ("+quote.heldCount+"): "+EconomyCraft.formatMoney(quote.heldTotal),"aqua"),false);
            source.sendSuccess(()->CombatService.text("Base per item: "+EconomyCraft.formatMoney(quote.base)+" • Estimate before tribute; prices can change.","gray"),false);
            return 1;
        }catch(Exception error){source.sendFailure(CombatService.text(error.getMessage()==null?"Item worth is unavailable.":error.getMessage(),"red"));return 0;}
    }
    private WorthCommand(){}
}
