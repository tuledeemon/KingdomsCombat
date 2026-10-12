package com.celik.kingdomscombat;

import java.util.*;

/** Real-time rules, independent from Minecraft and client input. */
public final class TagLedger {
    public enum Reason { COMBAT, WAR, AIRDROP }
    public static final class Tag {
        public Map<UUID,Long> opponents=new HashMap<>();
        public Map<Reason,Long> zones=new EnumMap<>(Reason.class);
        public long manualUntil;
    }
    public static final class Data {
        public int schemaVersion=1;
        public Map<UUID,Tag> tags=new HashMap<>();
        public Map<UUID,UUID> logoutPenalties=new HashMap<>();
    }
    public record Status(long combatMillis,long warMillis,long airDropMillis,Set<UUID> opponents){
        public long millis(){return Math.max(combatMillis,Math.max(warMillis,airDropMillis));}
        public boolean active(){return millis()>0;}
        public int seconds(){return (int)((millis()+999)/1000);}
        public Set<Reason> reasons(){Set<Reason> out=EnumSet.noneOf(Reason.class);if(combatMillis>0)out.add(Reason.COMBAT);if(warMillis>0)out.add(Reason.WAR);if(airDropMillis>0)out.add(Reason.AIRDROP);return out;}
    }
    public final Data data;
    public TagLedger(Data data){validate(data);this.data=data;}
    private Tag tag(UUID id){return data.tags.computeIfAbsent(id,x->new Tag());}
    public void hit(UUID attacker,UUID victim,long now,CombatConfig c){
        if(attacker.equals(victim))return;
        if(c.tagAttacker)tag(attacker).opponents.put(victim,now+c.durationSeconds*1000L);
        if(c.tagVictim)tag(victim).opponents.put(attacker,now+c.durationSeconds*1000L);
    }
    public void manual(UUID id,long now,int seconds){tag(id).manualUntil=now+seconds*1000L;}
    public void zone(UUID id,Reason reason,long now,int seconds){if(reason==Reason.COMBAT)throw new IllegalArgumentException("Use hit/manual for combat.");tag(id).zones.put(reason,now+seconds*1000L);}
    public Status status(UUID id,long now){
        Tag t=data.tags.get(id);if(t==null)return new Status(0,0,0,Set.of());
        Set<UUID> active=new HashSet<>();long combat=Math.max(0,t.manualUntil-now);
        for(var e:t.opponents.entrySet())if(e.getValue()>now){active.add(e.getKey());combat=Math.max(combat,e.getValue()-now);}
        return new Status(combat,Math.max(0,t.zones.getOrDefault(Reason.WAR,0L)-now),Math.max(0,t.zones.getOrDefault(Reason.AIRDROP,0L)-now),Set.copyOf(active));
    }
    public boolean refreshSelf(UUID id,long now,CombatConfig c){
        Tag t=data.tags.get(id);if(t==null||!status(id,now).active())return false;
        t.opponents.replaceAll((opponent,until)->until>now?now+c.durationSeconds*1000L:until);
        if(t.manualUntil>now)t.manualUntil=now+c.durationSeconds*1000L;
        if(t.zones.getOrDefault(Reason.WAR,0L)>now)t.zones.put(Reason.WAR,now+c.warLockSeconds*1000L);
        if(t.zones.getOrDefault(Reason.AIRDROP,0L)>now)t.zones.put(Reason.AIRDROP,now+c.airDropLockSeconds*1000L);
        return true;
    }
    public void clearCombat(UUID id){Tag t=data.tags.get(id);if(t!=null){t.opponents.clear();t.manualUntil=0;}}
    public void death(UUID dead,UUID killer,CombatConfig c){
        data.tags.remove(dead);
        if(c.killEndsCombat){for(Tag t:data.tags.values())t.opponents.remove(dead);if(c.killClearsAllOpponents&&killer!=null)clearCombat(killer);}
    }
    public void clear(UUID id){data.tags.remove(id);for(Tag t:data.tags.values())t.opponents.remove(id);}
    public void prune(long now){
        data.tags.entrySet().removeIf(e->{Tag t=e.getValue();t.opponents.values().removeIf(until->until<=now);t.zones.values().removeIf(until->until<=now);if(t.manualUntil<=now)t.manualUntil=0;return t.opponents.isEmpty()&&t.zones.isEmpty()&&t.manualUntil==0;});
    }
    public static boolean penalty(Status s,CombatConfig c){return c.killOnCombatLogout&&s.combatMillis>0||c.killOnWarLogout&&s.warMillis>0||c.killOnAirDropLogout&&s.airDropMillis>0;}
    public static boolean near(String dimension,double px,double py,double pz,String dropDimension,double dx,double dy,double dz,CombatConfig c){
        if(!Objects.equals(dimension,dropDimension))return false;
        double x=px-dx,y=py-dy,z=pz-dz;
        if(c.airDropVerticalTolerance>=0&&Math.abs(y)>c.airDropVerticalTolerance)return false;
        return x*x+z*z+(c.horizontalAirDropRadius?0:y*y)<=c.airDropRadius*c.airDropRadius;
    }
    public static void validate(Data d){
        if(d==null||d.schemaVersion!=1||d.tags==null||d.logoutPenalties==null)throw new IllegalStateException("Invalid combat state.");
        for(var e:d.tags.entrySet()){Tag t=e.getValue();if(e.getKey()==null||t==null||t.opponents==null||t.zones==null||t.manualUntil<0||t.opponents.entrySet().stream().anyMatch(x->x.getKey()==null||x.getValue()==null||x.getValue()<0)||t.zones.entrySet().stream().anyMatch(x->x.getKey()==null||x.getKey()==Reason.COMBAT||x.getValue()==null||x.getValue()<0))throw new IllegalStateException("Invalid combat tag.");}
        if(d.logoutPenalties.entrySet().stream().anyMatch(e->e.getKey()==null||e.getValue()==null))throw new IllegalStateException("Invalid logout penalty.");
    }
}
