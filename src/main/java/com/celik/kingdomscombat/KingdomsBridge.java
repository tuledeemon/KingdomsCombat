package com.celik.kingdomscombat;
import com.celik.kingdoms.*;
import com.celik.kingdomscombat.mixin.AirDropsAccessor;
import net.minecraft.server.level.ServerPlayer;
public final class KingdomsBridge {
    private KingdomsBridge(){}
    public static boolean war(ServerPlayer player,long now,CombatConfig c){
        var k=KingdomsMod.service;if(k==null||!c.warRestrictions)return false;
        var own=k.own(player.getUUID());
        if(own!=null&&Rules.war(k.state,own.id,now)!=null)return true;
        var territory=c.restrictWarTerritoryVisitors?k.land(player.level(),player.blockPosition()):null;
        return territory!=null&&Rules.war(k.state,territory.id,now)!=null;
    }
    public static boolean airDrop(ServerPlayer player,long now,CombatConfig c){
        var k=KingdomsMod.service;if(k==null||!c.airDropRestrictions)return false;
        for(var d:((AirDropsAccessor)(Object)k.airDrops).combat$data().drops)
            if(d.active()&&now<d.expiresAt&&TagLedger.near(KingdomService.dimension(player.level()),player.getX(),player.getY(),player.getZ(),d.dimension,d.x+.5,d.y,d.z+.5,c))return true;
        return false;
    }
}
