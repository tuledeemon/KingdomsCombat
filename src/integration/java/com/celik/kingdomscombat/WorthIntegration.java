package com.celik.kingdomscombat;

import com.celik.kingdoms.Ui;
import com.reazip.economycraft.EconomyCraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import java.util.*;
import java.util.function.BiConsumer;

final class WorthIntegration {
    static void run(CombatService service,ServerPlayer p,long[] time,BiConsumer<Boolean,String> check)throws Exception{
        SellMarket market=service.sellMarket;var manager=EconomyCraft.getManager(service.server);
        var dispatcher=service.server.getCommands().getDispatcher();var source=p.createCommandSourceStack();
        var pressure=new SellRules.Supply();pressure.volume=48;pressure.time=time[0];market.data.supply.put("minecraft:diamond",pressure);
        ItemStack held=new ItemStack(Items.DIAMOND,17);p.getInventory().setItem(0,held);
        String state=CombatStore.JSON.toJson(market.data);var balances=manager.getBalancesSnapshot();
        try{
            var quote=WorthCommand.named(market,"diamond");
            check.accept(quote.unit()==market.quote(List.of(new ItemStack(Items.DIAMOND))).total()&&quote.unit()<quote.base(),"worth uses current shared supply pressure rather than the static starting price");
            check.accept(quote.stackSize()==64&&quote.stackTotal()==market.quote(List.of(new ItemStack(Items.DIAMOND,64))).total()&&quote.stackTotal()!=quote.unit()*64,"worth stack totals use marginal batch pricing exactly like closing the sell menu");
            check.accept(WorthCommand.named(market,"minecraft:diamond").unit()==quote.unit()&&WorthCommand.named(market,"Iron Ingot").key().equals("minecraft:iron_ingot"),"worth accepts namespaced ids and item names with spaces");
            check.accept(WorthCommand.named(market,"ender_pearl").stackSize()==16&&WorthCommand.named(market,"potion_of_healing_1").stackSize()==1,"worth respects real stack sizes and EconomyCraft potion price aliases");
            var hand=WorthCommand.view(market,held,true);check.accept(hand.heldCount()==17&&hand.heldTotal()==market.quote(List.of(held)).total(),"held-item worth quotes the actual stack count without changing the item");
            CombatIntegration.packets(p).clear();check.accept(dispatcher.execute("worth diamond",source)==1&&CombatIntegration.packets(p).stream().anyMatch(packet->packet instanceof ClientboundSystemChatPacket chat&&chat.content().getString().contains(Ui.caps("Worth"))),"worth item command sends the styled native chat quote");
            check.accept(dispatcher.execute("worth",source)==1,"worth with no argument reads the main hand");
            var suggestions=dispatcher.getCompletionSuggestions(dispatcher.parse("worth dia",source)).join();check.accept(suggestions.getList().stream().anyMatch(s->s.getText().equals("diamond")),"worth tab completion offers configured item price keys");
            held.set(DataComponents.CUSTOM_NAME,Component.literal("Protected name"));check.accept(dispatcher.execute("worth",source)==0,"held named items are rejected by worth instead of advertising a sellable price");
            p.getInventory().setItem(0,ItemStack.EMPTY);check.accept(dispatcher.execute("worth",source)==0&&dispatcher.execute("worth not_a_real_item",source)==0,"worth gives clear errors for an empty hand and unknown price key");
            check.accept(dispatcher.execute("worth diamond",service.server.createCommandSourceStack())==1,"console can look up a named item without a player inventory");
            check.accept(state.equals(CombatStore.JSON.toJson(market.data))&&balances.equals(manager.getBalancesSnapshot()),"worth and completions do not mutate supply pressure, deposits, payments or EconomyCraft balances");
        }finally{market.data.supply.remove("minecraft:diamond");p.getInventory().setItem(0,ItemStack.EMPTY);}
    }
    private WorthIntegration(){}
}
