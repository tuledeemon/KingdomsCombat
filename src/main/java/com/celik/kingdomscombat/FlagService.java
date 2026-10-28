package com.celik.kingdomscombat;

import com.celik.kingdoms.Artifacts;
import net.minecraft.server.level.*;
import java.nio.file.Path;
import java.util.*;

/** Observes server activity; never cancels packets, actions or applies punishments. */
public final class FlagService {
    public static final class Flag {
        public UUID player;public String name,check,evidence,dimension,profile;public long time;public int x,y,z;
    }
    public static final class Data {
        public List<Flag> flags=new ArrayList<>();
    }
    private static final class Track {
        final List<Long> clicks=new ArrayList<>();final List<FlagRules.Step> steps=new ArrayList<>();
        final Map<String,Long> alerts=new HashMap<>();
        boolean mining;int fast,regular,actions,bits;long attackAt,miningAt,assistedUntil,lastSwing;double x,y,z;float yaw,pitch;String world;
    }
    private final CombatService service;
    final Path file;final Data data;
    private final Map<UUID,Track> tracks=new HashMap<>();
    private final Set<UUID> mutedAlerts=new HashSet<>();
    private long lastTickNanos,suspendUntil,lastSave,lastError;
    private int ticks;private boolean dirty,storageHealthy=true;
    FlagService(CombatService service){
        this.service=service;file=service.stateFile.resolveSibling("flags.json");Data loaded;
        try{loaded=CombatStore.read(file,Data.class,new Data());if(loaded.flags==null||loaded.flags.stream().anyMatch(f->f==null||f.player==null||f.name==null||f.check==null||f.evidence==null||f.dimension==null))throw new IllegalStateException("Invalid flag history.");}
        catch(RuntimeException failure){storageHealthy=false;loaded=new Data();KingdomsCombat.LOG.error("Flag history unavailable; keeping the original file and continuing with memory-only observations",failure);}
        data=loaded;
        prune();
    }
    private CombatConfig c(){return service.config;}
    private Track track(ServerPlayer p){return tracks.computeIfAbsent(p.getUUID(),id->new Track());}
    boolean eligible(ServerPlayer p){return c().antiCheatEnabled&&p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!service.staffMode(p)&&!service.staff.frozen(p.getUUID())&&!me.lucko.fabric.api.permissions.v0.Permissions.check(p,"kingdomscombat.anticheat.bypass",false);}
    private boolean timing(){return service.now()>=suspendUntil;}
    public void action(ServerPlayer p,int kind){if(!eligible(p)||!timing())return;Track t=track(p);t.actions=Math.min(32,t.actions+1);t.bits|=kind;if(kind==1)t.attackAt=service.now();}
    public void swing(ServerPlayer p,long receivedNanos){
        if(!c().antiCheatClicks||!eligible(p)||!timing()||p.isUsingItem()){Track old=tracks.get(p.getUUID());if(old!=null){old.clicks.clear();old.fast=old.regular=0;}return;}
        Track t=track(p);long now=service.now();if(t.attackAt==0||now-t.attackAt>3000||t.mining||now-t.miningAt<500){t.clicks.clear();t.fast=t.regular=0;return;}
        if(t.lastSwing!=0&&(receivedNanos<=t.lastSwing||receivedNanos-t.lastSwing>1_000_000_000L)){t.clicks.clear();t.fast=t.regular=0;}t.lastSwing=receivedNanos;
        t.clicks.add(receivedNanos);if(t.clicks.size()<c().antiCheatClickSamples+1)return;
        var stats=FlagRules.clicks(t.clicks);double seconds=(t.clicks.getLast()-t.clicks.getFirst())/1e9;t.clicks.clear();
        if(seconds<1||stats.burstFraction()>.1){t.fast=t.regular=0;return;}
        t.fast=stats.cps()>c().antiCheatMaxCps?t.fast+1:0;
        t.regular=seconds>=8&&stats.cps()>=c().antiCheatRegularMinCps&&!stats.tickAligned()&&stats.deviation()<=c().antiCheatRegularStdMs?t.regular+1:0;
        if(t.fast>=c().antiCheatClickWindows){flag(p,"Fast clicking",String.format(Locale.ROOT,"%.1f CPS across %d separate %d-click windows",stats.cps(),t.fast,c().antiCheatClickSamples));t.fast=0;}
        if(t.regular>=c().antiCheatClickWindows){flag(p,"Click timing",String.format(Locale.ROOT,"%.1f CPS; interval deviation %.2f ms across %d separate windows",stats.cps(),stats.deviation(),t.regular));t.regular=0;}
    }
    public void tick(){
        long nano=System.nanoTime();ticks++;
        if(lastTickNanos!=0&&nano-lastTickNanos>c().antiCheatMaxTickMs*1_000_000L){suspendUntil=service.now()+c().antiCheatLagGraceSeconds*1000L;tracks.clear();}lastTickNanos=nano;
        if(!c().antiCheatEnabled){tracks.clear();return;}
        if(ticks%c().antiCheatMacroSampleTicks==0&&c().antiCheatMacros&&timing())for(ServerPlayer p:service.server.getPlayerList().getPlayers())sample(p);
        if(ticks%200==0){Set<UUID> online=new HashSet<>();for(var p:service.server.getPlayerList().getPlayers())online.add(p.getUUID());tracks.keySet().retainAll(online);prune();}
        if(dirty&&service.now()-lastSave>=30000)save();
    }
    void sample(ServerPlayer p){
        if(!eligible(p)||p.isPassenger()||p.isFallFlying()||service.arenas.freezeMovement(p)){tracks.remove(p.getUUID());return;}
        Track t=track(p);String world=p.level().dimension().identifier().toString();double dx=p.getX()-t.x,dy=p.getY()-t.y,dz=p.getZ()-t.z;
        if(!world.equals(t.world)||dx*dx+dy*dy+dz*dz>256||service.now()<t.assistedUntil){t.steps.clear();t.actions=t.bits=0;baseline(t,p,world);return;}
        float turn=net.minecraft.util.Mth.wrapDegrees(p.getYRot()-t.yaw),pitch=p.getXRot()-t.pitch;
        long token=Objects.hash(Math.round(dx*8),Math.round(dy*8),Math.round(dz*8),Math.round(turn/5),Math.round(pitch/5),p.getInventory().getSelectedSlot(),t.bits,Math.min(7,t.actions));
        t.steps.add(new FlagRules.Step(token,dx*dx+dy*dy+dz*dz>.01,Math.abs(turn)>=10,t.actions));t.actions=t.bits=0;baseline(t,p,world);
        int cap=c().antiCheatMacroMaxPeriod*c().antiCheatMacroRepeats;while(t.steps.size()>cap)t.steps.removeFirst();
        if(ticks%20!=0)return;
        int period=FlagRules.repeatedCycle(t.steps,c().antiCheatMacroMinPeriod,c().antiCheatMacroMaxPeriod,c().antiCheatMacroRepeats,c().antiCheatMacroMatch);
        if(period>0){flag(p,"Repeated action loop",String.format(Locale.ROOT,"%d repeats of a %.1f-second movement/action cycle; at least %.0f%% matching samples",c().antiCheatMacroRepeats,period*c().antiCheatMacroSampleTicks/20.0,c().antiCheatMacroMatch*100));t.steps.clear();}
    }
    private void baseline(Track t,ServerPlayer p,String world){t.x=p.getX();t.y=p.getY();t.z=p.getZ();t.yaw=p.getYRot();t.pitch=p.getXRot();t.world=world;}
    public void observe(Runnable observation){try{observation.run();}catch(RuntimeException failure){if(service.now()-lastError>30000){lastError=service.now();KingdomsCombat.LOG.error("Suspicion check failed; gameplay was allowed to continue",failure);}}}
    public void mining(ServerPlayer p){if(!eligible(p))return;Track t=track(p);t.miningAt=service.now();if(Artifacts.marked(p.getMainHandItem())){t.assistedUntil=service.now()+2000;t.steps.clear();}}
    public void miningAction(ServerPlayer p,boolean active){if(!eligible(p))return;Track t=track(p);t.mining=active;t.miningAt=service.now();}
    public void mined(ServerPlayer p){track(p).mining=false;if(eligible(p)&&!Artifacts.marked(p.getMainHandItem()))action(p,4);}
    void flag(ServerPlayer p,String check,String evidence){
        if(!eligible(p))return;Track t=track(p);long now=service.now();Long previous=t.alerts.get(check);if(previous!=null&&now-previous<c().antiCheatAlertCooldownSeconds*1000L||data.flags.stream().anyMatch(f->f.player.equals(p.getUUID())&&f.check.equals(check)&&now-f.time<c().antiCheatAlertCooldownSeconds*1000L))return;t.alerts.put(check,now);
        Flag f=new Flag();f.player=p.getUUID();f.name=p.getGameProfile().name();f.profile=net.minecraft.world.item.component.ResolvableProfile.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,net.minecraft.world.item.component.ResolvableProfile.createResolved(p.getGameProfile())).getOrThrow().toString();f.check=check;f.evidence=evidence;f.time=now;f.dimension=p.level().dimension().identifier().toString();f.x=p.blockPosition().getX();f.y=p.blockPosition().getY();f.z=p.blockPosition().getZ();data.flags.add(f);prune();dirty=true;
        if(c().antiCheatLog)KingdomsCombat.LOG.warn("[Suspicion flag] {} ({}) {}: {} at {} {},{},{}",f.name,f.player,check,evidence,f.dimension,f.x,f.y,f.z);
        for(ServerPlayer viewer:service.server.getPlayerList().getPlayers())if(receives(viewer))viewer.sendSystemMessage(CombatService.text("[flag] "+f.name+" | "+check+" | "+evidence,"gold"));
    }
    boolean receives(ServerPlayer viewer){return service.staffMode(viewer)&&!mutedAlerts.contains(viewer.getUUID())&&me.lucko.fabric.api.permissions.v0.Permissions.check(viewer,service.config.staffToolPermissionPrefix+"flags",true);}
    public boolean toggleAlerts(ServerPlayer p){service.staff.require(p,"flags");if(mutedAlerts.remove(p.getUUID()))return true;mutedAlerts.add(p.getUUID());return false;}
    public List<Flag> history(UUID id){return data.flags.stream().filter(f->id==null||f.player.equals(id)).sorted(Comparator.comparingLong((Flag f)->f.time).reversed()).toList();}
    public net.minecraft.world.item.component.ResolvableProfile profile(UUID id){
        for(Flag f:history(id))if(f.profile!=null)try{var parsed=net.minecraft.world.item.component.ResolvableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,com.google.gson.JsonParser.parseString(f.profile)).result();if(parsed.isPresent())return parsed.get();}catch(RuntimeException ignored){}
        return net.minecraft.world.item.component.ResolvableProfile.createUnresolved(id);
    }
    public record Suspect(UUID player,String name,double score,int count,long lastTime,Set<String> checks) {}
    public List<Suspect> suspects(){
        Map<UUID,List<Flag>> grouped=new HashMap<>();long now=service.now();
        for(Flag f:data.flags)if(FlagRules.score(f.time,now,c().antiCheatRankingHours)>0)grouped.computeIfAbsent(f.player,id->new ArrayList<>()).add(f);
        List<Suspect> result=new ArrayList<>();for(var entry:grouped.entrySet()){
            List<Flag> flags=entry.getValue();Flag latest=flags.stream().max(Comparator.comparingLong(f->f.time)).orElseThrow();Set<String> checks=new TreeSet<>();flags.forEach(f->checks.add(f.check));
            result.add(new Suspect(entry.getKey(),latest.name,flags.stream().mapToDouble(f->FlagRules.score(f.time,now,c().antiCheatRankingHours)).sum(),flags.size(),latest.time,Set.copyOf(checks)));
        }
        result.sort(Comparator.comparingDouble(Suspect::score).reversed().thenComparing(Comparator.comparingLong(Suspect::lastTime).reversed()).thenComparing(s->s.player.toString()));return List.copyOf(result);
    }
    public void clear(UUID id){data.flags.removeIf(f->f.player.equals(id));tracks.remove(id);dirty=true;save();}
    public void leave(UUID id){tracks.remove(id);mutedAlerts.remove(id);}
    public void reset(){tracks.clear();lastTickNanos=0;suspendUntil=0;prune();}
    private void prune(){long now=service.now();if(data.flags.removeIf(f->now-f.time>c().antiCheatRetentionDays*86400000L))dirty=true;while(data.flags.size()>c().antiCheatHistoryLimit){data.flags.removeFirst();dirty=true;}}
    public void save(){if(!dirty||!storageHealthy)return;lastSave=service.now();try{CombatStore.write(file,data);dirty=false;}catch(RuntimeException failure){if(service.now()-lastError>30000){lastError=service.now();KingdomsCombat.LOG.error("Flag history save failed; observations retained for retry",failure);}}}
}
