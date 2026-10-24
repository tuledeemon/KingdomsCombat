package com.celik.kingdomscombat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KingdomsCombat implements ModInitializer {
    public static final Logger LOG=LoggerFactory.getLogger("kingdoms-combat");
    public static CombatService service;
    public static CombatConfig startupConfig;
    @Override public void onInitialize(){
        var configPath=FabricLoader.getInstance().getConfigDir().resolve("kingdoms-combat/config.json");startupConfig=CombatStore.read(configPath,CombatConfig.class,new CombatConfig());startupConfig.validate();CombatStore.write(configPath,startupConfig);
        var ready=Identifier.parse("kingdomscombat:ready");ServerLifecycleEvents.SERVER_STARTED.addPhaseOrdering(Identifier.parse("kingdoms:ready"),ready);
        ServerLifecycleEvents.SERVER_STARTED.register(ready,server->{service=new CombatService(server,startupConfig,configPath,server.getWorldPath(LevelResource.ROOT).resolve("kingdoms-combat/state.json"));});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{if(service!=null)service.stop();});ServerLifecycleEvents.SERVER_STOPPED.register(server->service=null);
        ServerTickEvents.END_SERVER_TICK.register(server->{if(service!=null)service.tick();});
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity,source,base,taken,blocked)->{if(entity instanceof ServerPlayer p&&service!=null)service.damage(p,source,base,taken,blocked);});
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{if(entity instanceof ServerPlayer p&&service!=null)service.death(p,source);});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{if(service!=null)service.disconnect(handler.player);});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{if(service!=null)service.join(handler.player);});
        ServerPlayerEvents.COPY_FROM.register((oldPlayer,newPlayer,alive)->((PenaltyReceipt)newPlayer).combat$penaltyReceipt(((PenaltyReceipt)oldPlayer).combat$penaltyReceipt()));
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->CombatCommands.register(dispatcher));
        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message,sender,parameters)->{if(service!=null&&service.staff.muted(sender.getUUID())){sender.sendSystemMessage(CombatService.text("You are muted.","red"));return false;}return true;});
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,entity)->!(player instanceof ServerPlayer p&&service!=null&&service.staff.frozen(p.getUUID())));
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player,world,hand)->player instanceof ServerPlayer p&&service!=null&&service.staff.frozen(p.getUUID())?net.minecraft.world.InteractionResult.FAIL:net.minecraft.world.InteractionResult.PASS);
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,world,hand,hit)->player instanceof ServerPlayer p&&service!=null&&service.staff.frozen(p.getUUID())?net.minecraft.world.InteractionResult.FAIL:net.minecraft.world.InteractionResult.PASS);
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->service==null||!(entity instanceof ServerPlayer victim&&service.staff.frozen(victim.getUUID()))&&!(source.getEntity() instanceof ServerPlayer attacker&&service.staff.frozen(attacker.getUUID())));
        CombatPlaceholders.register();
        if(FabricLoader.getInstance().isModLoaded("huskhomes"))HuskHomesHook.register();
        LOG.info("Kingdoms Combat: server-only Fabric 26.2; optional HuskHomes guard {}",FabricLoader.getInstance().isModLoaded("huskhomes"));
    }
}
