package com.celik.kingdomscombat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import java.util.*;
import java.util.function.BiConsumer;

/** Exercise vanilla player removal, physical ground entities and the player file, not just an event invoker. */
final class LogoutDropIntegration {
    static void run(MinecraftServer server,CombatService s,long[] time,BiConsumer<Boolean,String> check)throws Exception{
        var level=server.overworld();var player=CombatIntegration.player(server,level,"LogoutDrop",11040.5,165,12000.5);level.getChunk(player.blockPosition());
        // Synthetic players are not registered in ChunkMap. Supply the tracking visibility
        // that an actual nearby player gives this chunk before inspecting physical drops.
        var managerField=net.minecraft.server.level.ServerLevel.class.getDeclaredField("entityManager");managerField.setAccessible(true);
        @SuppressWarnings("unchecked") var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<net.minecraft.world.entity.Entity>)managerField.get(level);
        var chunkPosition=new net.minecraft.world.level.ChunkPos(player.blockPosition().getX()>>4,player.blockPosition().getZ()>>4);manager.updateChunkStatus(chunkPosition,net.minecraft.world.level.entity.Visibility.TICKING);
        UUID marker=UUID.randomUUID();
        var reconfig=CombatIntegration.player(server,level,"LogoutConfig",11040.5,165,12000.5);
        reconfig.getInventory().setItem(9,new ItemStack(Items.DIAMOND,1));s.ledger.manual(reconfig.getUUID(),time[0],20);
        server.getPlayerList().remove(reconfig);
        check.accept(reconfig.getHealth()==20&&!reconfig.getInventory().isEmpty()&&!s.ledger.data.logoutPenalties.containsKey(reconfig.getUUID()),"player removal for protocol reconfiguration does not count as logout");
        try{
            player.getInventory().clearContent();player.getInventory().setItem(9,marked(Items.DIAMOND,3,marker));player.setItemSlot(EquipmentSlot.HEAD,marked(Items.DIAMOND_HELMET,1,marker));player.setItemSlot(EquipmentSlot.OFFHAND,marked(Items.SHIELD,1,marker));
            player.containerMenu.setCarried(marked(Items.GOLD_INGOT,2,marker));
            s.ledger.manual(player.getUUID(),time[0],20);s.save();
            player.connection.onDisconnect(new DisconnectionDetails(Component.literal("Combat logout integration fixture")));
            int dropped=drops(player,marker);
            check.accept(!player.isAlive()&&dropped==7,"native disconnect creates all inventory, armor, offhand and cursor drops before any reconnect: alive="+player.isAlive()+", dropped="+dropped+", empty="+player.getInventory().isEmpty()+", keep="+level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY)+", nearby="+level.getEntitiesOfClass(ItemEntity.class,new AABB(player.blockPosition()).inflate(8)).stream().map(e->e.getItem().toString()).toList());
            var file=server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getStringUUID()+".dat");var saved=NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap());
            check.accept(saved.getFloatOr("Health",20)==0&&saved.getStringOr("kingdomscombat_logout_receipt","").equals(s.ledger.data.logoutPenalties.get(player.getUUID()).toString()),"vanilla logout saves death and its penalty receipt before player removal");
            check.accept(player.getInventory().isEmpty()&&server.getPlayerList().getPlayer(player.getUUID())==null,"player inventory is empty and the disconnected player is removed after dropping");
            var restored=new ServerPlayer(server,level,player.getGameProfile(),net.minecraft.server.level.ClientInformation.createDefault());
            restored.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),saved));
            var connection=new CombatIntegration.RecordingConnection();new net.minecraft.server.network.ServerGamePacketListenerImpl(server,connection,restored,net.minecraft.server.network.CommonListenerCookie.createInitial(restored.getGameProfile(),false));s.join(restored);
            check.accept(drops(player,marker)==7&&restored.getInventory().isEmpty()&&!s.ledger.data.logoutPenalties.containsKey(player.getUUID()),"rejoining acknowledges the already-saved death without spawning another copy of the logout drops");
        }finally{CombatIntegration.remove(server,player);level.getEntitiesOfClass(ItemEntity.class,new AABB(player.blockPosition()).inflate(8)).stream().filter(e->hasMarker(e,marker)).forEach(ItemEntity::discard);manager.updateChunkStatus(chunkPosition,net.minecraft.world.level.entity.Visibility.HIDDEN);}
    }
    static ItemStack marked(Item item,int count,UUID marker){ItemStack stack=new ItemStack(item,count);CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.putString("logout_drop_fixture",marker.toString()));return stack;}
    static boolean hasMarker(ItemEntity entity,UUID marker){return entity.getItem().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getStringOr("logout_drop_fixture","").equals(marker.toString());}
    static int drops(ServerPlayer player,UUID marker){return player.level().getEntitiesOfClass(ItemEntity.class,new AABB(player.blockPosition()).inflate(8)).stream().filter(e->hasMarker(e,marker)).mapToInt(e->e.getItem().getCount()).sum();}
    private LogoutDropIntegration(){}
}

