package com.celik.kingdomscombat;

import com.celik.kingdoms.*;
import com.celik.kingdomscombat.mixin.AirDropsAccessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.server.players.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.nbt.*;
import net.minecraft.util.ProblemReporter;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import eu.pb4.placeholders.api.*;
import java.util.*;
import java.nio.file.*;
import static net.minecraft.commands.Commands.*;

public final class CombatIntegration implements ModInitializer {
    private boolean ran;
    private int checks;
    private static final Map<UUID,RecordingConnection> connections=new HashMap<>();
    private static String lastDamage="none";
    private void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
    public void onInitialize(){if(!Boolean.getBoolean("kingdomscombat.integration"))return;net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((e,d,base,taken,blocked)->lastDamage="base="+base+", taken="+taken+", blocked="+blocked+", source="+d.getEntity());ServerTickEvents.END_SERVER_TICK.register(server->{if(ran||KingdomsCombat.service==null)return;ran=true;try{run(server);ArenaAsyncIntegration.start(server,this::check,error->finish(server,error));}catch(Throwable e){finish(server,e);}});}
    private void finish(MinecraftServer server,Throwable error){try{if(error!=null)throw error;Files.writeString(Path.of("result.txt"),"PASS "+checks+" checks\n");KingdomsCombat.LOG.info("COMBAT INTEGRATION PASS {}",checks);}catch(Throwable failure){KingdomsCombat.LOG.error("COMBAT INTEGRATION FAIL",failure);try{Files.writeString(Path.of("result.txt"),"FAIL "+failure+"\n");}catch(Exception ignored){}}finally{server.halt(false);}}
    private void run(MinecraftServer server)throws Exception{
        CombatService original=KingdomsCombat.service;var kingdoms=KingdomsMod.service;var savedKingdoms=kingdoms.store.copy(kingdoms.state);var drops=((AirDropsAccessor)(Object)kingdoms.airDrops).combat$data();var savedDrops=new ArrayList<>(drops.drops);
        long[] time={System.currentTimeMillis()};CombatConfig cfg=new CombatConfig();cfg.chatNotifications=false;cfg.titles=false;cfg.sounds=false;cfg.staffDefaultPermissionsWhenOff=false;
        var stateFile=server.getWorldPath(LevelResource.ROOT).resolve("test-combat-state.json");CombatStore.write(stateFile,new TagLedger.Data());CombatStore.write(stateFile.resolveSibling("event.json"),new HostedEvents.State());CombatService s=new CombatService(server,cfg,server.getWorldPath(LevelResource.ROOT).resolve("test-combat-config.json"),stateFile,()->time[0]);KingdomsCombat.service=s;
        ServerLevel level=server.overworld();level.getChunk(11000>>4,12000>>4);var a=player(server,level,"CombatAlpha",11000,160,12000);var b=player(server,level,"CombatBeta",11000,160,12003);var c=player(server,level,"CombatGamma",11000,160,12006);
        try{
            PersistenceIntegration.run(server,this::check);
            StaffIntegration.run(server,s,time,this::check);
            LogoutDropIntegration.run(server,s,time,this::check);
            check((Object)kingdoms.airDrops instanceof AirDropsAccessor,"addon reads live AirDrop data without changing Kingdoms source");
            check((Object)a instanceof PenaltyReceipt,"logout receipt mixin is installed");
            check(!s.status(a).active()&&s.allowTeleport(a,false),"untagged player can teleport");
            cfg.titles=true;cfg.sounds=true;cfg.chatNotifications=true;damage(a,b,level);check(s.status(a).combatMillis()>0&&s.status(b).combatMillis()>0,"real PvP damage tags attacker and victim: "+lastDamage+" health="+b.getHealth()+" loaded="+b.connection.hasClientLoaded()+" alive="+a.isAlive()+" creative="+a.isCreative()+" bypass="+s.bypass(a)+" pvp="+level.isPvpAllowed());
            check(connections.get(a.getUUID()).packets.stream().anyMatch(p->p instanceof ClientboundSetTitleTextPacket)&&connections.get(a.getUUID()).packets.stream().anyMatch(p->p instanceof ClientboundSoundPacket),"tag start sends native title and private sound packets");cfg.titles=false;cfg.sounds=false;cfg.chatNotifications=false;
            check(!s.allowTeleport(a,false),"combat blocks teleport checks");
            time[0]+=10000;var opponentBefore=s.status(b).combatMillis();a.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.ENDER_PEARL));
            check(a.gameMode.useItem(a,level,a.getMainHandItem(),InteractionHand.MAIN_HAND).consumesAction(),"real ender pearl throw succeeds");check(s.status(a).seconds()==20&&s.status(b).combatMillis()==opponentBefore,"pearl throw refreshes only the user");
            time[0]+=3000;a.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WIND_CHARGE));a.gameMode.useItem(a,level,a.getMainHandItem(),InteractionHand.MAIN_HAND);check(s.status(a).seconds()==20&&s.status(b).seconds()==7,"actual wind charge use refreshes only the user");
            time[0]+=3000;ItemStack spear=new ItemStack(Items.DIAMOND_SPEAR);var enchant=server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);EnchantmentHelper.updateEnchantments(spear,e->e.set(enchant.getOrThrow(Enchantments.LUNGE),1));a.setItemInHand(InteractionHand.MAIN_HAND,spear);a.getFoodData().setFoodLevel(20);a.setOnGround(true);a.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);a.postPiercingAttack();check(a.getDeltaMovement().lengthSqr()>0&&s.status(a).seconds()==20&&s.status(b).seconds()==4,"actual Lunge impulse refreshes only the wielder");
            s.ledger.clear(a.getUUID());s.ledger.clear(b.getUUID());a.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WIND_CHARGE));a.gameMode.useItem(a,level,a.getMainHandItem(),InteractionHand.MAIN_HAND);check(!s.status(a).active(),"movement use in safety does not start a fresh combat tag");
            a.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_SWORD));b.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));b.setYRot(180);b.setYHeadRot(180);b.startUsingItem(InteractionHand.OFF_HAND);var useTicks=LivingEntity.class.getDeclaredField("useItemRemaining");useTicks.setAccessible(true);useTicks.setInt(b,Items.SHIELD.getDefaultInstance().getUseDuration(b)-20);
            damage(a,b,level);check(b.getHealth()==20&&s.status(a).combatMillis()>0&&s.status(b).combatMillis()>0,"real fully blocked shield hit still tags both players by default");s.ledger.clear(a.getUUID());s.ledger.clear(b.getUUID());cfg.tagBlockedHits=false;damage(a,b,level);check(!s.status(a).active()&&!s.status(b).active(),"shield-hit tagging can be disabled");cfg.tagBlockedHits=true;b.stopUsingItem();b.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            var arrow=new net.minecraft.world.entity.projectile.arrow.Arrow(EntityTypes.ARROW,level);arrow.setOwner(a);b.setHealth(20);b.invulnerableTime=0;b.hurtServer(level,b.damageSources().arrow(arrow,a),3);check(s.status(a).combatMillis()>0&&s.status(b).combatMillis()>0,"player-owned projectile damage tags both players");s.ledger.clear(a.getUUID());s.ledger.clear(b.getUUID());
            damage(a,b,level);damage(a,c,level);b.kill(level);check(s.status(a).opponents().equals(Set.of(c.getUUID())),"killing one opponent preserves the other fight");c.kill(level);check(s.status(a).combatMillis()==0,"killing the last opponent ends combat");b.setHealth(20);c.setHealth(20);
            var parsed=server.getCommands().getDispatcher();int[] teleports={0};
            var home=parsed.register(literal("combatfixturehome").executes(ctx->{teleports[0]++;return 1;}));parsed.register(literal("combatfixturealias").redirect(home));parsed.register(literal("test:combatfixturealias").redirect(home));
            parsed.register(literal("combatfixtureouter").redirect(parsed.getRoot()));
            cfg.blockedCommands.add("combatfixturehome");s.ledger.manual(a.getUUID(),time[0],20);
            for(String command:List.of("combatfixturehome","combatfixturealias","test:combatfixturealias","combatfixtureouter combatfixturehome")){var result=parsed.parse(command,a.createCommandSourceStack());check(s.blockedCommand(result,command),"resolved redirect is blocked: "+command);server.getCommands().performCommand(result,command);}
            check(teleports[0]==0,"actual command execution stops roots and redirected aliases");
            cfg.blockedCommands.remove("combatfixturehome");server.getCommands().performPrefixedCommand(a.createCommandSourceStack(),"combatfixturehome");check(teleports[0]==1,"removing a command rule takes effect immediately");cfg.blockedCommands.add("combatfixturehome");
            s.ledger.clear(a.getUUID());server.getCommands().performPrefixedCommand(a.createCommandSourceStack(),"combatfixturehome");check(teleports[0]==2,"same command works without a restriction");
            var first=kingdom(a,"Alpha Kingdom");var second=kingdom(b,"Beta Kingdom");var next=kingdoms.store.copy(kingdoms.state);next.kingdoms.put(first.id,first);next.kingdoms.put(second.id,second);var war=new Rules.War();war.first=first.id;war.second=second.id;war.declaredAt=time[0];war.startedAt=time[0]+180000;war.endsAt=time[0]+1080000;next.wars.add(war);kingdoms.commit(next);
            check(!s.status(a).active()&&s.allowTeleport(a,false),"war preparation keeps teleport commands available");time[0]=war.startedAt;check(s.status(a).warMillis()==20000&&!s.allowTeleport(a,false),"active war starts and refreshes a twenty-second lock");
            time[0]+=5000;check(s.status(a).warMillis()==20000,"ongoing war continuously refreshes the lock");
            time[0]=war.endsAt-1000;s.status(a);time[0]=war.endsAt;check(s.status(a).warMillis()==19000,"war end leaves only the previously refreshed countdown");time[0]=war.endsAt+20000;check(s.allowTeleport(a,false),"war lock eventually expires");
            drops.drops.clear();var drop=new AirDropRules.Drop();drop.dimension="minecraft:overworld";drop.x=11000;drop.y=160;drop.z=12000;drop.createdAt=time[0];drop.landsAt=time[0]+600000;drop.expiresAt=time[0]+2400000;drops.drops.add(drop);
            AirDropArrivalIntegration.run(s,a,drop,this::check);
            a.setPos(11100.5,900,12000.5);check(s.status(a).airDropMillis()==20000,"100-block incoming drop radius includes high-altitude players");a.setPos(11100.501,160,12000.5);time[0]+=5000;check(s.status(a).airDropMillis()==15000,"leaving the exact radius starts the countdown");
            a.setPos(11000.5,160,12000.5);drop.phase=AirDropRules.Phase.CAPTURABLE;check(s.status(a).airDropMillis()==20000,"landed contested drops still restrict teleports");
            drop.phase=AirDropRules.Phase.CLAIMED;time[0]+=1000;check(!KingdomsBridge.airDrop(a,time[0],cfg)&&s.status(a).airDropMillis()==19000,"any winning side stops refreshing the AirDrop restriction");time[0]+=20000;check(s.allowTeleport(a,false),"claimed drop no longer blocks commands once the tail expires");
            drop.phase=AirDropRules.Phase.CANCELLED;check(!KingdomsBridge.airDrop(a,time[0],cfg),"cancelled drops do not restrict teleports");drop.phase=AirDropRules.Phase.EXPIRED;check(!KingdomsBridge.airDrop(a,time[0],cfg),"expired drops do not restrict teleports");
            cfg.airDropRestrictions=false;drop.phase=AirDropRules.Phase.DESCENDING;check(!s.status(a).active(),"AirDrop restrictions can be disabled");cfg.airDropRestrictions=true;drop.dimension="minecraft:the_nether";check(!KingdomsBridge.airDrop(a,time[0],cfg),"matching coordinates in another dimension do not tag players");drops.drops.clear();
            s.ledger.manual(a.getUUID(),time[0],20);var recording=connections.get(a.getUUID());recording.packets.clear();kingdoms.feedback.tick();check(recording.packets.stream().anyMatch(p->p instanceof ClientboundSetActionBarTextPacket bar&&bar.text().getString().contains(Ui.caps("combat"))&&bar.text().getString().contains(Ui.caps("territory"))),"combat HUD appends to the actual Kingdoms territory action bar");
            cfg.actionBar=false;recording.packets.clear();kingdoms.feedback.tick();check(recording.packets.stream().filter(p->p instanceof ClientboundSetActionBarTextPacket).map(p->((ClientboundSetActionBarTextPacket)p).text().getString()).noneMatch(t->t.contains(Ui.caps("combat"))),"disabling the combat HUD leaves the Kingdoms HUD intact");
            cfg.actionBar=true;cfg.appendToKingdomsActionBar=false;recording.packets.clear();kingdoms.feedback.tick();check(recording.packets.stream().anyMatch(p->p instanceof ClientboundSetActionBarTextPacket bar&&bar.text().getString().contains(Ui.caps("combat"))&&!bar.text().getString().contains(Ui.caps("territory"))),"combat-only action bar mode replaces the territory line only while tagged");cfg.appendToKingdomsActionBar=true;cfg.actionBar=false;
            var context=ServerPlaceholderContext.of(a);String placeholders=Placeholders.parseServerPlaceholder(net.minecraft.resources.Identifier.parse("kingdomscombat:remaining"),"",context).component().getString()+" / "+Placeholders.parseServerPlaceholder(net.minecraft.resources.Identifier.parse("kingdomscombat:tagged"),"",context).component().getString()+" / "+Placeholders.parseServerPlaceholder(net.minecraft.resources.Identifier.parse("kingdomscombat:tag"),"",context).component().getString();check(placeholders.contains("20 / true")&&placeholders.contains(Ui.caps("combat")),"Text Placeholder API returns the real timer and styled component while HUD is disabled");
            CombatMenu.status(a);check(a.containerMenu instanceof Ui.Menu&&a.containerMenu.getSlot(13).getItem().is(Items.CLOCK),"status uses a protected vanilla menu");a.closeContainer();
            server.getPlayerList().op(new NameAndId(a.getUUID(),a.getGameProfile().name()),Optional.of(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER),Optional.empty());CombatMenu.admin(a);check(a.containerMenu instanceof Ui.Menu&&a.containerMenu.getSlot(32).getItem().is(Items.ENDER_PEARL),"admin settings include command rules and dialog inputs");a.closeContainer();
            var reload=s.configFile;CombatStore.write(reload,cfg);s.reload();check(s.config.durationSeconds==20&&!s.config.actionBar,"configuration reload preserves saved options");s.config.operatorBypass=true;check(s.allowTeleport(a,false),"operator bypass is available only when explicitly configured");s.config.operatorBypass=false;
            server.getCommands().performPrefixedCommand(b.createCommandSourceStack(),"staffmode");check(!s.staffMode(b),"ordinary players cannot enable staff mode through the command");
            boolean denied=false;try{s.toggleStaffMode(b);}catch(IllegalArgumentException expected){denied=true;}check(denied,"staff permission is also checked inside the toggle");
            s.ledger.zone(a.getUUID(),TagLedger.Reason.WAR,time[0],20);s.ledger.zone(a.getUUID(),TagLedger.Reason.AIRDROP,time[0],20);server.getCommands().performPrefixedCommand(a.createCommandSourceStack(),"staffmode");
            check(s.staffMode(a)&&s.ledger.status(a.getUUID(),time[0]).combatMillis()==0,"staff command enables mode and clears existing PvP combat");
            check(!s.status(a).active()&&s.allowTeleport(a,false)&&!s.ledger.data.tags.containsKey(a.getUUID()),"staff mode clears every timer and bypasses war and AirDrop teleport restrictions");
            ready(b);s.ledger.clear(a.getUUID());s.ledger.clear(b.getUUID());damage(a,b,level);check(b.getHealth()<20&&s.status(a).combatMillis()==0&&s.status(b).combatMillis()==0,"staff interactions cause normal damage without creating PvP combat tags");
            s.ledger.manual(a.getUUID(),time[0],20);s.ledger.zone(a.getUUID(),TagLedger.Reason.WAR,time[0],20);s.config.killOnWarLogout=true;
            check(s.status(a).combatMillis()==0&&!s.logoutPunished(a)&&!s.component(a).getString().contains(Ui.caps("combat")),"staff status hides every restriction and reports no logout penalty");
            check(Placeholders.parseServerPlaceholder(net.minecraft.resources.Identifier.parse("kingdomscombat:staffmode"),"",ServerPlaceholderContext.of(a)).component().getString().equals("true"),"staff mode is available through the real placeholder API");
            s.disconnect(a);check(a.isAlive()&&!s.staffMode(a)&&!s.ledger.data.logoutPenalties.containsKey(a.getUUID()),"staff logout is safe and resets the session toggle even with optional zone deaths enabled");s.config.killOnWarLogout=false;s.ledger.clear(a.getUUID());
            server.getCommands().performPrefixedCommand(a.createCommandSourceStack(),"staffmode");server.getPlayerList().deop(new NameAndId(a.getUUID(),a.getGameProfile().name()));check(!s.staffMode(a),"revoked permissions immediately remove the staff exemption");
            server.getPlayerList().op(new NameAndId(a.getUUID(),a.getGameProfile().name()),Optional.of(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER),Optional.empty());server.getCommands().performPrefixedCommand(a.createCommandSourceStack(),"staffmode");damage(a,b,level);check(!s.staffMode(a)&&s.status(a).combatMillis()>0&&s.status(b).combatMillis()>0,"toggling staff mode off restores normal PvP tags");
            s.save();var loaded=new TagLedger(CombatStore.read(s.stateFile,TagLedger.Data.class,null));check(loaded.status(a.getUUID(),time[0]).seconds()==20,"real state file preserves the current timer");
            if(FabricLoader.getInstance().isModLoaded("huskhomes"))HuskCompatibility.run(server,a,b,s,this::check);
            if(FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0"))PermissionCompatibility.run(a,b,s,this::check);
            s.config.eventTitles=true;s.config.eventChat=true;s.config.eventSounds=true;s.config.sounds=true;connections.get(b.getUUID()).packets.clear();
            server.getCommands().performPrefixedCommand(b.createCommandSourceStack(),"eventhost \"Blocked event\" 10m");check(!s.events.active(),"ordinary players cannot host events without permission");
            server.getCommands().performPrefixedCommand(a.createCommandSourceStack(),"eventhost \"PvP Tournament\" 10m");
            check(s.events.active()&&s.events.remainingMillis()==600000&&s.events.name().equals("PvP Tournament"),"eventhost starts immediately for the supplied duration");
            var origin=new net.minecraft.world.phys.Vec3(11000,160,12003);b.setPos(origin);ready(b);s.ledger.clear(b.getUUID());
            a.setPos(11000.5,160,12000.5);server.getCommands().performPrefixedCommand(a.createCommandSourceStack(),"eventhost setlocation");
            server.getCommands().performPrefixedCommand(a.createCommandSourceStack(),"eventhost description Fight in teams. Last team standing wins.");server.getCommands().performPrefixedCommand(b.createCommandSourceStack(),"event");check(connections.get(b.getUUID()).packets.stream().anyMatch(packet->packet.getClass().getSimpleName().equals("ClientboundShowDialogPacket")),"event command opens the native description/time/teleport dialog: "+connections.get(b.getUUID()).packets.stream().map(packet->packet instanceof ClientboundSystemChatPacket chat?chat.content().getString():packet.getClass().getSimpleName()).toList());
            var described=CombatStore.read(s.stateFile.resolveSibling("event.json"),HostedEvents.State.class,null);check(described.event.description.equals("Fight in teams. Last team standing wins."),"staff-written description is saved with the current event");
            var staffEventPoint=a.position();a.setPos(origin);s.ledger.zone(a.getUUID(),TagLedger.Reason.WAR,time[0],20);s.ledger.zone(a.getUUID(),TagLedger.Reason.AIRDROP,time[0],20);s.toggleStaffMode(a);server.getCommands().performPrefixedCommand(a.createCommandSourceStack(),"event teleport "+s.events.id());check(a.position().equals(staffEventPoint)&&s.allowTeleport(a,false),"staff mode permits the actual event teleport despite war and AirDrop restrictions");s.toggleStaffMode(a);
            var eventPoint=a.position();s.ledger.manual(b.getUUID(),time[0],20);server.getCommands().performPrefixedCommand(b.createCommandSourceStack(),"event teleport "+s.events.id());check(b.position().equals(origin),"event command cannot bypass a PvP combat tag");
            s.ledger.clear(b.getUUID());s.ledger.zone(b.getUUID(),TagLedger.Reason.WAR,time[0],20);server.getCommands().performPrefixedCommand(b.createCommandSourceStack(),"event teleport "+s.events.id());check(b.position().equals(origin),"event command cannot bypass an active war timer");
            s.ledger.clear(b.getUUID());s.ledger.zone(b.getUUID(),TagLedger.Reason.AIRDROP,time[0],20);server.getCommands().performPrefixedCommand(b.createCommandSourceStack(),"event teleport "+s.events.id());check(b.position().equals(origin),"event command cannot bypass an AirDrop timer");
            s.ledger.clear(b.getUUID());server.getCommands().performPrefixedCommand(b.createCommandSourceStack(),"event teleport "+s.events.id());check(b.position().equals(eventPoint),"safe player uses native teleport to the host-selected event location");
            var eventFile=CombatStore.read(s.stateFile.resolveSibling("event.json"),HostedEvents.State.class,null);check(eventFile.location!=null&&eventFile.event.location.equals(eventFile.location),"selected event location persists with the active event");
            a.setPos(59000.5,160,61000.5);s.events.setLocation(a.createCommandSourceStack());b.setPos(origin);s.ledger.clear(b.getUUID());s.events.travel(b);
            var pendingField=HostedEvents.class.getDeclaredField("pending");pendingField.setAccessible(true);var pending=(Map<?,?>)pendingField.get(s.events);var request=pending.get(b.getUUID());check(request!=null,"unloaded event location is requested without blocking the server thread");
            s.ledger.manual(b.getUUID(),time[0],20);var finishTravel=HostedEvents.class.getDeclaredMethod("completeTravel",ServerPlayer.class,request.getClass(),ServerLevel.class,boolean.class);finishTravel.setAccessible(true);finishTravel.invoke(s.events,b,request,level,true);check(b.position().equals(origin)&&!pending.containsKey(b.getUUID()),"combat beginning during chunk loading cancels the event teleport at completion");
            a.setPos(eventPoint);s.events.setLocation(a.createCommandSourceStack());s.ledger.clear(b.getUUID());

            check(s.events.bossBar().getPlayers().containsAll(List.of(a,b,c))&&s.events.bossBar().getName().getString().contains(Ui.caps("PvP Tournament")),"event boss bar reaches every online player with small-caps text");
            check(connections.get(b.getUUID()).packets.stream().anyMatch(p->p instanceof ClientboundSetTitleTextPacket)&&connections.get(b.getUUID()).packets.stream().anyMatch(p->p instanceof ClientboundSoundPacket),"event start broadcasts native titles and sounds");
            boolean replaced=false;try{s.events.start(a.createCommandSourceStack(),"Replacement","10m");}catch(IllegalArgumentException expected){replaced=true;}check(replaced&&s.events.name().equals("PvP Tournament"),"a running event cannot be overwritten accidentally");
            time[0]+=300000;s.events.tick();check(Math.abs(s.events.bossBar().getProgress()-.5)<.001&&s.events.bossBar().getName().getString().contains("5:00"),"event boss bar counts down the actual event duration");
            var eventViewer=player(server,level,"EventViewer",11000,160,12000);s.join(eventViewer);check(s.events.bossBar().getPlayers().contains(eventViewer),"players joining during the event receive its boss bar");s.events.leave(eventViewer);remove(server,eventViewer);
            var recoveredEvents=new HostedEvents(s,s.stateFile.resolveSibling("event.json"));check(recoveredEvents.active()&&recoveredEvents.remainingMillis()==300000,"event timestamps survive reloading persistent state");
            check(Placeholders.parseServerPlaceholder(net.minecraft.resources.Identifier.parse("kingdomscombat:event_name"),"",ServerPlaceholderContext.of(a)).component().getString().equals(Ui.caps("PvP Tournament")),"hosted event name is available through placeholders");
            time[0]+=300000;s.events.tick();check(!s.events.active()&&s.events.bossBar()==null,"event expiry removes the boss bar");
            check(!new HostedEvents(s,s.stateFile.resolveSibling("event.json")).active(),"expired event is not resurrected after reload");
            s.events.start(a.createCommandSourceStack(),"Stopped event","1m");server.getCommands().performPrefixedCommand(a.createCommandSourceStack(),"eventhost stop");check(!s.events.active()&&s.events.bossBar()==null,"event hosts can stop the active event through the command");
            b.setPos(origin);server.getCommands().performPrefixedCommand(b.createCommandSourceStack(),"event teleport "+eventFile.event.id);check(b.position().equals(origin),"stale event dialog cannot teleport after the event stops");
            s.config.eventTitles=false;s.config.eventChat=false;s.config.eventSounds=false;s.config.sounds=false;
            EventChestIntegration.run(s,a,b,c,this::check);

            s.ledger.clear(a.getUUID());s.ledger.manual(a.getUUID(),time[0],20);s.stopping=true;s.disconnect(a);check(a.isAlive()&&s.ledger.data.logoutPenalties.isEmpty(),"normal server shutdown does not kill combat-tagged players");s.stopping=false;
            a.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);a.getInventory().setItem(9,new ItemStack(Items.DIAMOND,3));s.ledger.manual(a.getUUID(),time[0],20);
            a.connection.onDisconnect(new net.minecraft.network.DisconnectionDetails(Component.literal("Combat logout fixture")));check(!a.isAlive()&&a.getInventory().getItem(9).isEmpty(),"native disconnect kills the tagged player with immediate inventory drops");check(s.ledger.data.logoutPenalties.containsKey(a.getUUID())&&((PenaltyReceipt)a).combat$penaltyReceipt()!=null,"logout death is journaled with a player receipt");
            var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());a.saveWithoutId(output);var nbt=output.buildResult();check(nbt.getStringOr("kingdomscombat_logout_receipt","").equals(s.ledger.data.logoutPenalties.get(a.getUUID()).toString()),"receipt is part of the same vanilla player save as death and inventory");
            var restored=player(server,level,"ReceiptRecovery",11000,160,12000);restored.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),nbt));UUID penalty=s.ledger.data.logoutPenalties.get(a.getUUID());s.ledger.data.logoutPenalties.put(restored.getUUID(),penalty);s.join(restored);check(!s.ledger.data.logoutPenalties.containsKey(restored.getUUID()),"saved receipt prevents applying a logout death twice");remove(server,restored);
            b.setHealth(20);s.ledger.clear(b.getUUID());s.ledger.data.logoutPenalties.put(b.getUUID(),UUID.randomUUID());((PenaltyReceipt)b).combat$penaltyReceipt(null);var loading=ServerGamePacketListenerImpl.class.getDeclaredField("waitingForRespawn");loading.setAccessible(true);loading.setBoolean(b.connection,true);check(!b.connection.hasClientLoaded(),"logout recovery fixture has normal login/respawn immunity");s.join(b);check(!b.isAlive()&&((PenaltyReceipt)b).combat$penaltyReceipt()!=null,"interrupted logout penalty is applied on next join despite loading immunity");
            cfg=s.config;cfg.killOnWarLogout=false;cfg.killOnAirDropLogout=false;c.setHealth(20);s.ledger.clear(c.getUUID());s.ledger.zone(c.getUUID(),TagLedger.Reason.WAR,time[0],20);s.disconnect(c);check(c.isAlive(),"war-only restrictions do not kill on logout by default");
            ArenaIntegration.run(s,time,this::check);
            ServerUtilitiesIntegration.run(s,time,this::check);
            SellIntegration.run(s,time,this::check);
        }finally{drops.drops.clear();drops.drops.addAll(savedDrops);kingdoms.commit(savedKingdoms);remove(server,a);remove(server,b);remove(server,c);KingdomsCombat.service=original;}
    }
    private static void damage(ServerPlayer attacker,ServerPlayer victim,ServerLevel level){victim.setHealth(20);victim.invulnerableTime=0;victim.hurtServer(level,attacker.damageSources().playerAttack(attacker),3);}
    private static void ready(ServerPlayer p)throws Exception{var timeout=ServerGamePacketListenerImpl.class.getDeclaredField("clientLoadedTimeoutTimer");timeout.setAccessible(true);timeout.setInt(p.connection,0);var waiting=ServerGamePacketListenerImpl.class.getDeclaredField("waitingForRespawn");waiting.setAccessible(true);waiting.setBoolean(p.connection,false);}
    private static Rules.Kingdom kingdom(ServerPlayer p,String name){var k=new Rules.Kingdom();k.name=name;k.members.put(p.getUUID(),p.getName().getString());k.mayors.add(p.getUUID());k.claims.add("minecraft:overworld|"+(p.blockPosition().getX()>>4)+"|"+((p.blockPosition().getZ()>>4)+(name.startsWith("Alpha")?100:200)));k.nextUpkeep=System.currentTimeMillis()+86400000;return k;}
    @SuppressWarnings("unchecked") static ServerPlayer player(MinecraftServer server,ServerLevel level,String name,double x,double y,double z)throws Exception{
        var p=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),name),ClientInformation.createDefault());p.setPos(x,y,z);p.setHealth(20);p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        var connection=new RecordingConnection();connections.put(p.getUUID(),connection);var handler=new ServerGamePacketListenerImpl(server,connection,p,CommonListenerCookie.createInitial(p.getGameProfile(),false));
        var timeout=ServerGamePacketListenerImpl.class.getDeclaredField("clientLoadedTimeoutTimer");timeout.setAccessible(true);timeout.setInt(handler,0);var waiting=ServerGamePacketListenerImpl.class.getDeclaredField("waitingForRespawn");waiting.setAccessible(true);waiting.setBoolean(handler,false);
        server.getPlayerList().getPlayers().add(p);var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);((Map<UUID,ServerPlayer>)field.get(server.getPlayerList())).put(p.getUUID(),p);return p;
    }
    @SuppressWarnings("unchecked") static void remove(MinecraftServer server,ServerPlayer p)throws Exception{server.getPlayerList().getPlayers().remove(p);var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);((Map<UUID,ServerPlayer>)field.get(server.getPlayerList())).remove(p.getUUID());}
    static List<net.minecraft.network.protocol.Packet<?>> packets(ServerPlayer p){return connections.get(p.getUUID()).packets;}
    static final class RecordingConnection extends net.minecraft.network.Connection{
        final List<net.minecraft.network.protocol.Packet<?>> packets=new ArrayList<>();
        RecordingConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet,io.netty.channel.ChannelFutureListener listener,boolean flush){packets.add(packet);}
    }
}
