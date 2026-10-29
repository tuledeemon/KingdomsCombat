package com.celik.kingdomscombat;

import com.celik.kingdoms.Ui;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import java.util.*;

final class EventAreaUi {
    static void selection(EventAreas s,ServerPlayer p){s.require(p);Ui.open(p,"Event area selection",m->{
        var chosen=s.selection(p.getUUID());
        m.button(11,Items.WOODEN_AXE,"First corner",List.of("Select your current block position, including Y.",chosen==null||chosen.first()==null?"Not selected":chosen.first().toShortString()),()->{s.corner(p,1,p.blockPosition());selection(s,p);});
        m.button(15,Items.WOODEN_AXE,"Second corner",List.of("Select the opposite corner, including Y.",chosen==null||chosen.second()==null?"Not selected":chosen.second().toShortString()),()->{s.corner(p,2,p.blockPosition());selection(s,p);});
        m.button(22,Items.GRASS_BLOCK,"Claim selection",List.of("Exact cuboid between both selected blocks.","Include the floor and ceiling you want protected.","Permanent • separate flags for each area"),()->s.claim(p,null));
        m.button(31,Items.MAP,"Saved event areas",List.of(s.data.areas.size()+" permanent areas","Open any area's independent settings"),()->list(s,p,0));
        m.button(49,Items.OAK_DOOR,"Close",List.of(),p::closeContainer);
    },false);}
    static void settings(EventAreas s,ServerPlayer p,UUID id){
        s.require(p);var a=s.data.areas.get(id);if(a==null)throw new IllegalArgumentException("This event area no longer exists.");
        Ui.open(p,"Event area • "+a.name,m->{
            var r=a.region;m.button(4,Items.MAP,a.name,List.of(r.dimension,"From: "+r.minX+", "+r.minY+", "+r.minZ,"To: "+r.maxX+", "+r.maxY+", "+r.maxZ,"Permanent • these flags affect only this area"),null);
            Item[] icons={Items.DIAMOND_PICKAXE,Items.BRICKS,Items.IRON_SWORD,Items.LEVER,Items.CHEST,Items.TNT,Items.FLINT_AND_STEEL,Items.WATER_BUCKET,Items.PISTON,Items.CREEPER_HEAD};
            for(var f:EventAreaRules.Flag.values()){int n=f.ordinal(),slot=10+n/5*9+n%5;boolean enabled=a.allows(f);m.button(slot,icons[n],f.title+": "+(enabled?"Allowed":"Blocked"),List.of(f.description,"Click to "+(enabled?"block":"allow")),()->{s.flag(p,id,f);settings(s,p,id);});}
            m.button(31,Items.BARRIER,"Unclaim this area",List.of("Remove only "+a.name,"No blocks or other areas are deleted."),()->confirmUnclaim(s,p,id));
            m.button(45,Items.WOODEN_AXE,"New area selection",List.of("Select corners for another area"),()->selection(s,p));
            m.button(49,Items.MAP,"All event areas",List.of(),()->list(s,p,0));m.button(53,Items.OAK_DOOR,"Close",List.of(),p::closeContainer);
        },false);
    }
    private static void confirmUnclaim(EventAreas s,ServerPlayer p,UUID id){s.require(p);var area=s.data.areas.get(id);if(area==null)throw new IllegalArgumentException("This event area no longer exists.");Ui.open(p,"Unclaim "+area.name,m->{m.button(13,Items.PAPER,"Remove "+area.name,List.of("Only this area's protection and flags are removed."),null);m.button(29,Items.EMERALD_BLOCK,"Confirm unclaim",List.of(),()->{s.unclaim(p,id);selection(s,p);});m.button(33,Items.REDSTONE_BLOCK,"Cancel",List.of(),()->settings(s,p,id));},false);}
    static void list(EventAreas s,ServerPlayer p,int requested){
        s.require(p);var all=new ArrayList<>(s.data.areas.values());all.sort(Comparator.comparing(a->a.name.toLowerCase(Locale.ROOT)));int pages=Math.max(1,(all.size()+20)/21),page=Math.max(0,Math.min(requested,pages-1));
        Ui.open(p,"Event areas • "+(page+1)+" / "+pages,m->{
            for(int i=page*21;i<Math.min(all.size(),(page+1)*21);i++){var a=all.get(i);int n=i-page*21;m.button(10+n/7*9+n%7,Items.MAP,a.name,List.of(a.region.dimension,"Click to configure only this area"),()->settings(s,p,a.id));}
            if(all.isEmpty())m.button(22,Items.PAPER,"No event areas",List.of("Select two corners to create one"),null);
            if(page>0)m.button(45,Items.ARROW,"Previous",List.of(),()->list(s,p,page-1));m.button(49,Items.WOODEN_AXE,"Create area",List.of(),()->selection(s,p));if(page+1<pages)m.button(53,Items.ARROW,"Next",List.of(),()->list(s,p,page+1));
        },false);
    }
    private EventAreaUi(){}
}
