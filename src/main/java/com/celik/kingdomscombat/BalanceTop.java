package com.celik.kingdomscombat;

import com.celik.kingdoms.Ui;
import com.celik.kingdomscombat.mixin.BalanceCommandAccessor;
import com.reazip.economycraft.EconomyCraft;
import net.minecraft.commands.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.concurrent.*;
import static net.minecraft.commands.Commands.*;

public final class BalanceTop {
    public record Row(UUID player,long balance) {}
    public record Snapshot(List<Row> rows,Map<UUID,Integer> ranks,long time) {}
    private final CombatService service;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Kingdoms balance ranking");t.setDaemon(true);return t;});
    private Snapshot snapshot=new Snapshot(List.of(),Map.of(),0);private boolean loading,closed;private long attempted;
    public BalanceTop(CombatService service){this.service=service;}
    public static List<Row> rank(Map<UUID,Long> balances,int limit){return balances.entrySet().stream().filter(e->e.getKey()!=null&&e.getValue()!=null&&e.getValue()>=0).sorted(Map.Entry.<UUID,Long>comparingByValue().reversed().thenComparing(e->e.getKey().toString())).limit(limit).map(e->new Row(e.getKey(),e.getValue())).toList();}
    public void refresh(){
        if(closed||loading||service.now()-attempted<service.config.baltopCacheSeconds*1000L)return;attempted=service.now();loading=true;
        var manager=EconomyCraft.getManager(service.server);if(manager==null){loading=false;return;}var balances=manager.getBalancesSnapshot();long now=service.now();int limit=service.config.baltopMaxEntries;
        CompletableFuture.supplyAsync(()->rank(balances,limit),worker).whenComplete((rows,error)->service.server.execute(()->{loading=false;if(closed)return;if(error!=null){KingdomsCombat.LOG.warn("Balance ranking refresh failed",error);return;}Map<UUID,Integer> ranks=new HashMap<>();for(int i=0;i<rows.size();i++)ranks.put(rows.get(i).player,i+1);snapshot=new Snapshot(List.copyOf(rows),Map.copyOf(ranks),now);}));
    }
    public void menu(ServerPlayer p,int requested){
        if(!me.lucko.fabric.api.permissions.v0.Permissions.check(p,"kingdomscombat.baltop",true))throw new IllegalArgumentException("You do not have permission to view the balance leaderboard.");
        refresh();Ui.open(p,"Balance leaderboard",m->{
            refresh();Snapshot view=snapshot;int size=service.config.baltopPageSize,pages=Math.max(1,(view.rows.size()+size-1)/size),page=Math.max(0,Math.min(requested,pages-1));var manager=EconomyCraft.getManager(service.server);
            for(int i=0;i<54;i++)if(i<9||i>=45||i%9==0||i%9==8)m.button(i,Items.GLASS_PANE," ",List.of(),null);
            m.button(4,Items.GOLD_BLOCK,"Richest players",List.of("Page "+(page+1)+" / "+pages,view.rows.size()+" recorded accounts","Player wallets • includes offline accounts",loading?"Updating balances...":"Refresh interval: "+service.config.baltopCacheSeconds+"s"),null);
            int start=page*size;for(int n=start;n<Math.min(start+size,view.rows.size());n++){Row row=view.rows.get(n);String name=manager==null?null:manager.getBestName(row.player);if(name==null||name.isBlank())name=service.staff.name(row.player);int local=n-start,slot=10+local/7*9+local%7;String status=service.server.getPlayerList().getPlayer(row.player)==null?"Offline":"Online";m.button(slot,StaffMenu.head(row.player),"#"+(n+1)+" "+name,List.of("Balance: "+EconomyCraft.formatMoney(row.balance),status),null);}
            if(view.rows.isEmpty())m.button(22,Items.CLOCK,loading?"Loading balances":"No balances recorded",List.of("Includes EconomyCraft's stored player wallets"),null);
            Long wallet=manager==null?null:manager.getBalance(p.getUUID(),false);long own=wallet==null?0:wallet;Integer position=view.ranks.get(p.getUUID());m.button(49,StaffMenu.head(p.getUUID()),"Your balance: "+EconomyCraft.formatMoney(own),List.of(position==null?"Outside the displayed top "+service.config.baltopMaxEntries:"Your rank: #"+position,"Rankings use the cached snapshot"),null);
            if(page>0)m.button(45,Items.ARROW,"Previous page",List.of(),()->menu(p,page-1));if(page+1<pages)m.button(53,Items.ARROW,"Next page",List.of(),()->menu(p,page+1));m.button(47,Items.CLOCK,"Refresh",List.of("Uses the shared refresh interval"),()->menu(p,page));
        },false);
    }
    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher){
        var root=(BalanceCommandAccessor)(Object)dispatcher.getRoot();root.combat$children().remove("baltop");root.combat$literals().remove("baltop");root.combat$arguments().remove("baltop");
        dispatcher.register(literal("baltop").executes(c->open(c,0)).then(argument("page",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1)).executes(c->open(c,com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"page")-1))));
    }
    private static int open(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c,int page){try{var s=KingdomsCombat.service;if(c.getSource().getPlayer()!=null)s.balanceTop.menu(c.getSource().getPlayer(),page);else{var manager=EconomyCraft.getManager(s.server);for(Row row:rank(manager.getBalancesSnapshot(),10))c.getSource().sendSuccess(()->CombatService.text(Optional.ofNullable(manager.getBestName(row.player)).orElse(row.player.toString())+" | "+EconomyCraft.formatMoney(row.balance),"gold"),false);}return 1;}catch(Exception error){c.getSource().sendFailure(CombatService.text(error.getMessage()==null?"Balance leaderboard unavailable.":error.getMessage(),"red"));return 0;}}
    public void stop(){closed=true;worker.shutdownNow();}
}
