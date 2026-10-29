package com.celik.kingdomscombat;

import java.util.*;

public final class EventAreaRules {
    public enum Flag {
        BREAK(false,"Block breaking","Allow players to break blocks and decorations"),
        PLACE(false,"Block placing","Allow players to place blocks and entities"),
        PVP(true,"PvP","Allow player-versus-player damage"),
        INTERACT(true,"Interactions","Allow buttons, doors and entity interactions"),
        CONTAINERS(true,"Containers","Allow chest and other container access"),
        EXPLOSIONS(false,"Explosion block damage","Allow explosions to destroy terrain; entity damage follows PvP"),
        FIRE(false,"Fire","Allow ignition and fire spread"),
        FLUIDS(true,"Fluid flow","Allow flow inside this area; crossing its boundary stays blocked"),
        PISTONS(false,"Pistons","Allow movement inside this area; crossing its boundary stays blocked"),
        MOB_GRIEFING(false,"Mob griefing","Allow mobs to change terrain; does not control spawning");
        public final boolean defaultValue;public final String title,description;
        Flag(boolean value,String title,String description){defaultValue=value;this.title=title;this.description=description;}
    }
    public static final class Area {
        public UUID id=UUID.randomUUID();public String name;public ArenaRules.Region region;
        public Map<Flag,Boolean> flags=new EnumMap<>(Flag.class);
        public Area(){for(Flag f:Flag.values())flags.put(f,f.defaultValue);}
        public boolean allows(Flag f){return flags.getOrDefault(f,f.defaultValue);}
    }
    public static final class Data {public int schemaVersion=1;public Map<UUID,Area> areas=new LinkedHashMap<>();}
    public static boolean intersects(ArenaRules.Region a,ArenaRules.Region b){return a.dimension.equals(b.dimension)&&a.minX<=b.maxX&&a.maxX>=b.minX&&a.minY<=b.maxY&&a.maxY>=b.minY&&a.minZ<=b.maxZ&&a.maxZ>=b.minZ;}
    public static void validate(Data data){
        if(data==null||data.schemaVersion!=1||data.areas==null)throw new IllegalStateException("Invalid event-area data.");
        for(var entry:data.areas.entrySet()){
            Area a=entry.getValue();if(a==null||a.id==null||!a.id.equals(entry.getKey())||a.name==null||a.name.isBlank()||a.name.length()>64||a.name.chars().anyMatch(Character::isISOControl)||a.region==null||a.flags==null||a.flags.values().stream().anyMatch(Objects::isNull))throw new IllegalStateException("Invalid event area.");
            var r=a.region;if(r.dimension==null||r.minX>r.maxX||r.minY>r.maxY||r.minZ>r.maxZ||r.minX< -30000000||r.maxX>30000000||r.minZ< -30000000||r.maxZ>30000000)throw new IllegalStateException("Invalid event-area bounds.");
        }
    }
    /** Immutable interval index: no block scans, per-tick scans or per-chunk expansion. */
    public static final class Index {
        private record Node(Area area,Node left,Node right,int maxX){}
        private final Map<String,Node> dimensions=new HashMap<>();
        public Index(Collection<Area> areas){Map<String,List<Area>> groups=new HashMap<>();for(Area a:areas)groups.computeIfAbsent(a.region.dimension,k->new ArrayList<>()).add(a);groups.forEach((dim,list)->{list.sort(Comparator.comparingInt(a->a.region.minX));dimensions.put(dim,build(list,0,list.size()));});}
        private static Node build(List<Area> areas,int low,int high){if(low>=high)return null;int mid=(low+high)>>>1;Node left=build(areas,low,mid),right=build(areas,mid+1,high);Area area=areas.get(mid);return new Node(area,left,right,Math.max(area.region.maxX,Math.max(left==null?Integer.MIN_VALUE:left.maxX,right==null?Integer.MIN_VALUE:right.maxX)));}
        public Area at(String dimension,int x,int y,int z){return find(dimensions.get(dimension),x,y,z);}
        private static Area find(Node n,int x,int y,int z){if(n==null||n.maxX<x)return null;Area left=find(n.left,x,y,z);if(left!=null)return left;var r=n.area.region;if(r.minX>x)return null;if(r.contains(r.dimension,x,y,z))return n.area;return find(n.right,x,y,z);}
        public boolean chunk(String dimension,int x,int z){return overlap(dimensions.get(dimension),(long)x*16,(long)x*16+15,(long)z*16,(long)z*16+15);}
        private static boolean overlap(Node n,long minX,long maxX,long minZ,long maxZ){if(n==null||n.maxX<minX)return false;if(overlap(n.left,minX,maxX,minZ,maxZ))return true;var r=n.area.region;if(r.minX>maxX)return false;if(r.maxX>=minX&&r.minZ<=maxZ&&r.maxZ>=minZ)return true;return overlap(n.right,minX,maxX,minZ,maxZ);}
    }
    private EventAreaRules(){}
}
