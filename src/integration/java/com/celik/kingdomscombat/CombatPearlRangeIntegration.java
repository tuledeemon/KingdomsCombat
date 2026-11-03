package com.celik.kingdomscombat;

import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import java.util.function.BiConsumer;

final class CombatPearlRangeIntegration {
    static void run(CombatService service,long[] time,BiConsumer<Boolean,String> check)throws Exception{
        ServerLevel level=service.server.overworld();var p=CombatIntegration.player(service.server,level,"PearlRange",21000,180,21000);
        // Vanilla pearls refuse to teleport a disconnected owner; use an online recording
        // connection here rather than the ordinary packet-only fixture connection.
        var connection=new OnlineConnection();var handler=new net.minecraft.server.network.ServerGamePacketListenerImpl(service.server,connection,p,net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false));
        var timeout=handler.getClass().getDeclaredField("clientLoadedTimeoutTimer");timeout.setAccessible(true);timeout.setInt(handler,0);
        var waiting=handler.getClass().getDeclaredField("waitingForRespawn");waiting.setAccessible(true);waiting.setBoolean(handler,false);
        Vec3 origin=p.position();long clock=time[0];boolean enabled=service.config.enabled;
        try{
            service.ledger.manual(p.getUUID(),time[0],20);float health=p.getHealth();
            var far=pearl(level,p,origin.add(301,0,0));impact(far);
            check.accept(far.isRemoved()&&p.position().equals(origin)&&p.getHealth()==health,"native combat pearl beyond 300 is discarded before teleport or pearl damage");
            var exact=pearl(level,p,origin.add(300,0,0));level.getChunkAt(BlockPos.containing(exact.position()));impact(exact);
            check.accept(p.position().equals(origin.add(300,0,0))&&exact.isRemoved(),"native combat pearl at exactly 300 blocks still teleports");p.setPos(origin);p.setHealth(20);p.invulnerableTime=0;
            var vertical=pearl(level,p,origin.add(0,301,0));impact(vertical);check.accept(p.position().equals(origin),"vertical combat pearl displacement is included in the distance cap");
            var diagonal=pearl(level,p,origin.add(213,0,213));impact(diagonal);check.accept(p.position().equals(origin),"diagonal pearl cannot bypass cap by keeping each axis below 300");
            var oldFar=pearl(level,p,origin.add(301,0,0));oldFar.setPos(origin.add(1,0,0));impact(oldFar);
            check.accept(p.position().equals(origin),"range guard checks vanilla oldPosition destination rather than closer projectile/hit coordinates");
            service.ledger.clear(p.getUUID());var beforeCombat=pearl(level,p,origin.add(400,0,0));service.ledger.manual(p.getUUID(),time[0],20);impact(beforeCombat);
            check.accept(p.position().equals(origin),"pearl prepared before tagging is checked against combat state at impact");
            var afterExpiry=pearl(level,p,origin.add(400,0,0));time[0]+=21000;level.getChunkAt(BlockPos.containing(afterExpiry.position()));impact(afterExpiry);
            check.accept(p.position().equals(origin.add(400,0,0)),"expired combat tag no longer limits a pearl at impact");p.setPos(origin);p.setHealth(20);p.invulnerableTime=0;
            service.ledger.clear(p.getUUID());service.ledger.zone(p.getUUID(),TagLedger.Reason.WAR,time[0],20);var warOnly=pearl(level,p,origin.add(400,0,0));impact(warOnly);
            check.accept(p.position().equals(origin.add(400,0,0)),"war-only teleport lock does not activate the PvP pearl range cap");p.setPos(origin);p.setHealth(20);p.invulnerableTime=0;
            service.ledger.clear(p.getUUID());service.ledger.zone(p.getUUID(),TagLedger.Reason.AIRDROP,time[0],20);check.accept(service.allowCombatPearl(p,level,origin.add(400,0,0)),"AirDrop-only lock does not activate PvP pearl range cap");
            service.ledger.manual(p.getUUID(),time[0],20);check.accept(!service.allowCombatPearl(p,service.server.getLevel(net.minecraft.world.level.Level.NETHER),origin),"cross-dimension pearl travel is rejected while PvP tagged");
            service.config.enabled=false;check.accept(service.allowCombatPearl(p,level,origin.add(400,0,0)),"disabling combat addon disables the new pearl range rule");service.config.enabled=true;
        }finally{service.config.enabled=enabled;service.ledger.clear(p.getUUID());time[0]=clock;CombatIntegration.remove(service.server,p);}
    }
    private static ThrownEnderpearl pearl(ServerLevel level,ServerPlayer player,Vec3 destination){var pearl=new ThrownEnderpearl(level,player,new ItemStack(Items.ENDER_PEARL));pearl.setPos(destination);pearl.setOldPosAndRot();return pearl;}
    private static void impact(ThrownEnderpearl pearl)throws Exception{var hit=ThrownEnderpearl.class.getDeclaredMethod("onHit",HitResult.class);hit.setAccessible(true);hit.invoke(pearl,new BlockHitResult(pearl.position(),Direction.UP,pearl.blockPosition(),false));}
    private static final class OnlineConnection extends net.minecraft.network.Connection {
        OnlineConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}
        @Override public boolean isConnected(){return true;}
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet,io.netty.channel.ChannelFutureListener listener,boolean flush){}
    }
    private CombatPearlRangeIntegration(){}
}
