package com.celik.kingdomscombat.mixin;
import com.celik.kingdomscombat.KingdomsCombat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class) public abstract class StaffNetworkMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",at=@At("HEAD"),cancellable=true)
    private void eventDestination(net.minecraft.world.entity.PositionMoveRotation movement,java.util.Set<net.minecraft.world.entity.Relative> relative,CallbackInfo ci){var s=KingdomsCombat.service;var pos=movement.position();if(s!=null&&!s.allowDestination(player,player.level(),pos.x,pos.y,pos.z,relative))ci.cancel();}
    private boolean frozen(){return player.level().getServer().isSameThread()&&KingdomsCombat.service!=null&&KingdomsCombat.service.staff.frozen(player.getUUID());}
    @Inject(method="handleMovePlayer",at=@At("HEAD"),cancellable=true) private void move(ServerboundMovePlayerPacket packet,CallbackInfo ci){if(!frozen())return;var p=KingdomsCombat.service.staff.anchor(player.getUUID());double dx=packet.getX(player.getX())-p.x,dy=packet.getY(player.getY())-p.y,dz=packet.getZ(player.getZ())-p.z;if(dx*dx+dy*dy+dz*dz>1e-6){player.connection.teleport(p.x,p.y,p.z,packet.getYRot(player.getYRot()),packet.getXRot(player.getXRot()));ci.cancel();}}
    @Inject(method={"handleMoveVehicle","handleContainerClick","handleSetCreativeModeSlot","handleAttack","handleInteract"},at=@At("HEAD"),cancellable=true) private void action(CallbackInfo ci){if(frozen()){player.inventoryMenu.broadcastFullState();ci.cancel();}}
    @Inject(method="handlePlayerAction",at=@At("HEAD"),cancellable=true) private void drop(ServerboundPlayerActionPacket packet,CallbackInfo ci){if(frozen()&&switch(packet.getAction()){case DROP_ITEM,DROP_ALL_ITEMS,SWAP_ITEM_WITH_OFFHAND->true;default->false;}){player.inventoryMenu.broadcastFullState();ci.cancel();}}
}
