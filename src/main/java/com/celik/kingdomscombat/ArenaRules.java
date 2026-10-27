package com.celik.kingdomscombat;

import java.util.*;

/** Persistent tournament state. All ratings use one individual ladder, including doubles. */
public final class ArenaRules {
    public enum Phase { APPLICATIONS, RUNNING, FINISHED, CANCELLED }
    public enum MatchPhase { PREPARING, COUNTDOWN, FIGHTING }
    public static final class Point {
        public String dimension; public double x,y,z; public float yaw,pitch;
        public Point(){}
        public Point(String dim,double x,double y,double z,float yaw,float pitch){dimension=dim;this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;}
    }
    public static final class Region {
        public String dimension; public int minX,minY,minZ,maxX,maxY,maxZ;
        public boolean contains(String dim,double x,double y,double z){return dimension.equals(dim)&&x>=minX&&x<maxX+1d&&y>=minY&&y<maxY+1d&&z>=minZ&&z<maxZ+1d;}
        public long volume(){return (long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1);}
    }
    public static final class Gate {public int x,y,z;public String state;}
    public static final class Setup {
        public Point corner1,corner2,spawn1,spawn2,teleportSpot;
        public int regionPadding;
        public Region region;
        public List<String> kit=new ArrayList<>();
        public List<Gate> gates=new ArrayList<>();
    }
    public static final class Entry {
        public UUID captain;
        public List<UUID> players=new ArrayList<>();
        public Map<UUID,String> banners=new HashMap<>();
    }
    public static final class Pair {
        public UUID a,b;
        public Pair(){}
        public Pair(UUID a,UUID b){this.a=a;this.b=b;}
    }
    public static final class Event {
        public UUID id=UUID.randomUUID();
        public String name;
        public int teamSize=1,round=1;
        public Phase phase=Phase.APPLICATIONS;
        public Map<UUID,Entry> entries=new LinkedHashMap<>();
        public List<Pair> queue=new ArrayList<>();
        public List<UUID> roundWinners=new ArrayList<>();
        public Map<UUID,UUID> invitations=new HashMap<>();
        public UUID champion;
        public boolean paused;
        public long nextAt;
    }
    public static final class Match {
        public UUID id=UUID.randomUUID();
        public Pair pair;
        public MatchPhase phase=MatchPhase.PREPARING;
        public Set<UUID> alive=new HashSet<>();
        public long startsAt,endsAt;
    }
    public static final class Rating {public int elo,wins,losses;public Rating(){}public Rating(int elo){this.elo=elo;}}
    public static final class Snapshot {
        public UUID receipt=UUID.randomUUID(),match;
        public Point origin;
        public List<String> items=new ArrayList<>(),effects=new ArrayList<>();
        public String food,mode;
        public float health,absorption,xpProgress;
        public int xpLevel,xpTotal,selected,fire,air;
        public boolean restoring;
    }
    public static final class Data {
        public int schemaVersion=1;
        public Setup setup=new Setup();
        public Event event;
        public Match match;
        public Map<UUID,Rating> ratings=new HashMap<>();
        public Map<UUID,Snapshot> snapshots=new HashMap<>();
        public Map<String,String> changedBlocks=new LinkedHashMap<>();
    }
    public static int eloDelta(double winnerAverage,double loserAverage,int k,int scale){return Math.max(1,(int)Math.round(k*(1-1/(1+Math.pow(10,(loserAverage-winnerAverage)/scale)))));}
    public static UUID side(Event event,UUID player){if(event==null)return null;for(Entry entry:event.entries.values())if(entry.players.contains(player))return entry.captain;return null;}
    public static List<Entry> trimToPowerOfTwo(Event event){
        int keep=Integer.highestOneBit(event.entries.size());
        List<UUID> order=new ArrayList<>(event.entries.keySet());List<Entry> excluded=new ArrayList<>();
        for(int i=keep;i<order.size();i++)excluded.add(event.entries.remove(order.get(i)));
        event.queue.removeIf(p->!event.entries.containsKey(p.a)||!event.entries.containsKey(p.b));
        return excluded;
    }
    /** Manual pairs stay in order; shuffle and pair the rest. Latest excess entries are excluded first. */
    public static void seed(Event event,Random random){
        if(event.entries.size()<2||Integer.bitCount(event.entries.size())!=1)throw new IllegalArgumentException("Close entries at a power of two before seeding.");
        Set<UUID> used=new HashSet<>();
        for(Pair p:event.queue)if(p.a==null||p.b==null||p.a.equals(p.b)||!event.entries.containsKey(p.a)||!event.entries.containsKey(p.b)||!used.add(p.a)||!used.add(p.b))throw new IllegalArgumentException("Manual brackets must use distinct registered teams once each.");
        List<UUID> remaining=new ArrayList<>(event.entries.keySet());remaining.removeAll(used);Collections.shuffle(remaining,random);
        for(int i=0;i+1<remaining.size();i+=2)event.queue.add(new Pair(remaining.get(i),remaining.get(i+1)));
    }
    public static void validate(Data data){
        if(data==null||data.schemaVersion!=1||data.setup==null||data.ratings==null||data.snapshots==null||data.changedBlocks==null)throw new IllegalStateException("Invalid arena save.");
        if(data.setup.kit==null||data.setup.gates==null||data.setup.gates.size()>4096||data.changedBlocks.size()>100000)throw new IllegalStateException("Invalid arena templates.");
        for(var e:data.snapshots.entrySet())if(e.getKey()==null||e.getValue()==null||e.getValue().receipt==null||e.getValue().match==null||e.getValue().items==null||e.getValue().items.size()>128||e.getValue().origin==null||e.getValue().effects==null)throw new IllegalStateException("Invalid arena inventory backup.");
        if(data.event!=null){Event e=data.event;if(e.id==null||e.phase==null||e.entries==null||e.queue==null||e.roundWinners==null||e.invitations==null||e.teamSize<1||e.teamSize>2)throw new IllegalStateException("Invalid tournament.");Set<UUID> members=new HashSet<>();for(var entry:e.entries.values()){if(entry==null||entry.captain==null||entry.players==null||entry.players.size()!=e.teamSize||!entry.players.contains(entry.captain)||entry.banners==null)throw new IllegalStateException("Invalid tournament team.");for(UUID id:entry.players)if(id==null||!members.add(id))throw new IllegalStateException("Duplicate tournament player.");}}
        if(data.match!=null&&(data.event==null||data.match.id==null||data.match.phase==null||data.match.pair==null||data.match.alive==null))throw new IllegalStateException("Invalid arena match.");
    }
    private ArenaRules(){}
}
