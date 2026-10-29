package com.celik.kingdomscombat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Minecraft state stays on its server thread; Discord requests use one bounded worker. */
public final class PlaytimeRoles {
    enum Result { GRANTED, ALREADY_PRESENT, UNLINKED, UNAVAILABLE }
    interface Gateway {Result grant(UUID player,String role)throws Exception;}
    private record Completion(UUID player,long revision,Result result,String error) {}
    private final CombatService service;private final Gateway gateway;private final Executor executor;private final ExecutorService owned;
    private final Set<UUID> pending=new HashSet<>();private final Map<UUID,Long> next=new HashMap<>();
    private final Queue<Completion> completed=new ConcurrentLinkedQueue<>();private final AtomicBoolean closed=new AtomicBoolean();
    private volatile long revision;private long lastError;private int ticks;
    PlaytimeRoles(CombatService service){this.service=service;gateway=new PlaytimeRoleBridge();owned=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Kingdoms playtime role");t.setDaemon(true);return t;});executor=owned;}
    PlaytimeRoles(CombatService service,Gateway gateway,Executor executor){this.service=service;this.gateway=gateway;this.executor=executor;owned=null;}
    public static boolean qualifies(long ticks,double hours){return ticks>=0&&ticks>=Math.ceil(hours*72000);}
    private boolean configured(){return service.config.playtimeRoleEnabled&&!service.config.playtimeRoleId.isBlank();}
    public void tick(){
        Completion done;while((done=completed.poll())!=null){pending.remove(done.player);if(done.revision!=revision)continue;long delay=done.error==null&&done.result!=Result.UNAVAILABLE?service.config.playtimeRoleCheckSeconds:service.config.playtimeRoleRetrySeconds;next.put(done.player,service.now()+delay*1000L);if(done.error!=null&&service.now()-lastError>=60000){lastError=service.now();KingdomsCombat.LOG.warn("Discord playtime role could not be granted: {}. Check the bot's Manage Roles permission and role hierarchy.",done.error);}else if(done.result==Result.GRANTED)KingdomsCombat.LOG.info("Granted Discord playtime role {} for Minecraft player {}",service.config.playtimeRoleId,done.player);}
        if(++ticks%20!=0||!configured()||closed.get())return;
        Set<UUID> online=new HashSet<>();for(ServerPlayer p:service.server.getPlayerList().getPlayers()){online.add(p.getUUID());consider(p);}next.keySet().retainAll(online);
    }
    void consider(ServerPlayer p){
        if(closed.get()||!configured()||pending.size()>=service.config.playtimeRoleQueueLimit||pending.contains(p.getUUID())||service.now()<next.getOrDefault(p.getUUID(),0L)||!qualifies(p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)),service.config.playtimeRoleHours))return;
        UUID id=p.getUUID();String role=service.config.playtimeRoleId;long request=revision;pending.add(id);
        try{executor.execute(()->{if(closed.get()||request!=revision){completed.add(new Completion(id,request,Result.UNAVAILABLE,null));return;}try{Result result=gateway.grant(id,role);completed.add(new Completion(id,request,result,null));}catch(Exception failure){Throwable cause=failure.getCause()==null?failure:failure.getCause();completed.add(new Completion(id,request,Result.UNAVAILABLE,cause.getClass().getSimpleName()+": "+cause.getMessage()));}});}catch(RejectedExecutionException failure){pending.remove(id);next.put(id,service.now()+60000);}
    }
    public void reset(){revision++;next.clear();}
    public void stop(){closed.set(true);if(owned!=null)owned.shutdownNow();}
}
