package com.celik.kingdomscombat;

import com.celik.kingdoms.*;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.LightningBolt;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.function.LongSupplier;

public final class CombatService {
    public final MinecraftServer server;
    public final TagLedger ledger;
    public final HostedEvents events;
    public final StaffPermissions permissions;
    public final StaffService staff;
    public CombatConfig config;
    public final Path configFile,stateFile;
    private final LongSupplier clock;
    private final Map<UUID,TagLedger.Status> previous=new HashMap<>();
    private final Map<UUID,Long> lastDenial=new HashMap<>();
    private final Set<UUID> staffModes=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private int ticks;
    private long lastSaveAttempt,lastSaveError;
    private boolean saveFailureReported;
    public boolean stopping,healthy=true;
    private boolean dirty;
    public CombatService(MinecraftServer server,CombatConfig config,Path configFile,Path stateFile){this(server,config,configFile,stateFile,System::currentTimeMillis);}
    CombatService(MinecraftServer server,CombatConfig config,Path configFile,Path stateFile,LongSupplier clock){
        this.server=server;this.config=config;this.configFile=configFile;this.stateFile=stateFile;this.clock=clock;
        ledger=new TagLedger(CombatStore.read(stateFile,TagLedger.Data.class,new TagLedger.Data()));ledger.prune(now());
        if(!trySave())throw new IllegalStateException("Combat state is not writable; refusing to start without durable logout protection: "+stateFile);
        events=new HostedEvents(this,stateFile.resolveSibling("event.json"));
        permissions=new StaffPermissions(this);staff=new StaffService(this);
    }
    public long now(){return clock.getAsLong();}
    public static boolean allowed(CommandSourceStack source,String permission,int level){boolean fallback=admin(source,level);return !permission.isBlank()&&FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0")?PermissionHook.allowed(source,permission,fallback):fallback;}
    boolean staffFlag(UUID id){return staffModes.contains(id);}
    public boolean staffMode(ServerPlayer p){return staffModes.contains(p.getUUID())&&permissions.authorize(p);}
    public boolean toggleStaffMode(ServerPlayer p){
        if(staffModes.remove(p.getUUID())){staff.leave(p);permissions.refreshCommands(p);return false;}
        if(!permissions.authorize(p))throw new IllegalArgumentException("Staff permission required.");
        if(staff.frozen(p.getUUID()))throw new IllegalArgumentException("Your staff review must be ended before enabling staff mode.");
        staffModes.add(p.getUUID());
        try{staff.enter(p);ledger.data.tags.remove(p.getUUID());save();permissions.refreshCommands(p);return true;}
        catch(RuntimeException error){staffModes.remove(p.getUUID());try{staff.leave(p);}catch(RuntimeException restore){error.addSuppressed(restore);}throw error;}
    }
    public static boolean admin(CommandSourceStack source,int level){return source.permissions().hasPermission(switch(level){case 1->Permissions.COMMANDS_MODERATOR;case 3->Permissions.COMMANDS_ADMIN;case 4->Permissions.COMMANDS_OWNER;default->Permissions.COMMANDS_GAMEMASTER;});}
    public boolean bypass(ServerPlayer p){
        if(staffMode(p))return true;
        if(config.operatorBypass&&admin(p.createCommandSourceStack(),config.adminLevel))return true;
        return config.respectBypassPermission&&!config.bypassPermission.isBlank()&&FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0")&&PermissionHook.bypass(p,config.bypassPermission);
    }
    public TagLedger.Status status(ServerPlayer player){
        if(staff.frozen(player.getUUID()))return new TagLedger.Status(0,0,0,Set.of());
        if(!config.enabled||bypass(player))return new TagLedger.Status(0,0,0,Set.of());
        if(server.isSameThread()&&player.isAlive()&&!player.isSpectator())exposure(player);
        return currentStatus(player);
    }
    private TagLedger.Status currentStatus(ServerPlayer p){return staffMode(p)?new TagLedger.Status(0,0,0,Set.of()):ledger.status(p.getUUID(),now());}
    public void exposure(ServerPlayer p){
        if(!config.enabled||bypass(p)||!p.isAlive()||p.isSpectator())return;
        long now=now();var before=ledger.status(p.getUUID(),now);
        if(KingdomsBridge.war(p,now,config)){ledger.zone(p.getUUID(),TagLedger.Reason.WAR,now,config.warLockSeconds);dirty=true;if(before.warMillis()==0)tell(p,config.warMessage);}
        if(KingdomsBridge.airDrop(p,now,config)){ledger.zone(p.getUUID(),TagLedger.Reason.AIRDROP,now,config.airDropLockSeconds);dirty=true;if(before.airDropMillis()==0)tell(p,config.airDropMessage);}
    }
    public void tick(){
        ticks++;if(stopping)return;
        staff.tick();
        if(ticks%20==0)events.tick();
        if(ticks%config.pollTicks==0){
            for(ServerPlayer p:server.getPlayerList().getPlayers()){
                var before=previous.getOrDefault(p.getUUID(),new TagLedger.Status(0,0,0,Set.of()));var after=status(p);previous.put(p.getUUID(),after);
                if(before.combatMillis()>0&&after.combatMillis()==0){tell(p,TagLedger.penalty(after,config)?"Combat ended.":config.safeMessage);sound(p,config.endSound);}
                if(config.actionBar&&before.active()&&!after.active()&&p.connection!=null)p.connection.send(new ClientboundSetActionBarTextPacket(text(config.safeMessage,config.airDropColor)));
            }
            ledger.prune(now());
        }
        if(config.enabled&&config.actionBar&&ticks%config.actionBarIntervalTicks==0){
            var kingdoms=KingdomsMod.service;boolean nativeHud=kingdoms!=null&&(kingdoms.config.territoryActionBar||kingdoms.config.warActionBar);
            if(!nativeHud||!config.appendToKingdomsActionBar)for(ServerPlayer p:server.getPlayerList().getPlayers())if(status(p).active()&&p.connection!=null)p.connection.send(new ClientboundSetActionBarTextPacket(component(p)));
        }
        if(dirty&&now()-lastSaveAttempt>=config.saveIntervalSeconds*1000L)save();
    }
    public void damage(ServerPlayer victim,DamageSource source,float base,float taken,boolean blocked){
        if(!config.enabled||!victim.isAlive()||(taken<=0&&!(blocked&&config.tagBlockedHits&&base>0)))return;
        ServerPlayer attacker=source.getEntity() instanceof ServerPlayer p?p:source.getDirectEntity() instanceof Projectile arrow&&arrow.getOwner() instanceof ServerPlayer p?p:source.getDirectEntity() instanceof LightningBolt bolt?bolt.getCause():null;
        if(attacker==null||attacker==victim||!eligible(victim)||!eligible(attacker)||!Protection.damage(victim,source))return;
        long now=now();var a=ledger.status(attacker.getUUID(),now);var b=ledger.status(victim.getUUID(),now);ledger.hit(attacker.getUUID(),victim.getUUID(),now,config);dirty=true;
        if(a.combatMillis()==0&&ledger.status(attacker.getUUID(),now).combatMillis()>0)started(attacker);
        if(b.combatMillis()==0&&ledger.status(victim.getUUID(),now).combatMillis()>0)started(victim);
        previous.put(attacker.getUUID(),ledger.status(attacker.getUUID(),now));previous.put(victim.getUUID(),ledger.status(victim.getUUID(),now));
        save();
    }
    private boolean eligible(ServerPlayer p){return p.isAlive()&&!bypass(p)&&!staffMode(p)&&(config.tagSpectators||!p.isSpectator())&&(config.tagCreativePlayers||!p.isCreative());}
    private void started(ServerPlayer p){
        tell(p,format(config.taggedMessage,p));if(config.titles&&p.connection!=null){p.connection.send(new ClientboundSetTitlesAnimationPacket(5,30,10));p.connection.send(new ClientboundSetTitleTextPacket(text("Combat",config.combatColor)));p.connection.send(new ClientboundSetSubtitleTextPacket(component(p)));}sound(p,config.tagSound);
    }
    public void movement(ServerPlayer p,String method){
        if(!config.enabled||bypass(p))return;
        boolean enabled=switch(method){case "ender_pearl"->config.resetOnEnderPearl;case "wind_charge"->config.resetOnWindCharge;case "spear_lunge"->config.resetOnSpearLunge;default->false;};
        if(enabled){status(p);if(ledger.refreshSelf(p.getUUID(),now(),config)){dirty=true;save();}}
    }
    public void death(ServerPlayer p,DamageSource source){UUID killer=source.getEntity() instanceof ServerPlayer other?other.getUUID():null;ledger.death(p.getUUID(),killer,config);dirty=true;save();}
    public void disconnect(ServerPlayer p){
        var current=status(p);
        if(config.enabled&&(!stopping||config.punishDuringShutdown)&&p.isAlive()&&!bypass(p)&&!staffMode(p)&&TagLedger.penalty(current,config)){
            UUID pending=ledger.data.logoutPenalties.get(p.getUUID());
            UUID penalty=pending!=null&&!pending.equals(((PenaltyReceipt)p).combat$penaltyReceipt())?pending:UUID.randomUUID();
            ledger.data.logoutPenalties.put(p.getUUID(),penalty);
            if(trySave()){
                ((PenaltyReceipt)p).combat$penaltyReceipt(penalty);logoutDeath(p);
                if(config.broadcastLogoutDeaths)server.getPlayerList().broadcastSystemMessage(message(config.logoutMessage.replace("{player}",p.getName().getString()),config.errorColor),false);
            }
        }
        events.leave(p);staff.disconnect(p);staffModes.remove(p.getUUID());permissions.leave(p);previous.remove(p.getUUID());lastDenial.remove(p.getUUID());dirty=true;save();
    }
    public void join(ServerPlayer p){
        staffModes.remove(p.getUUID());
        permissions.join(p);staff.join(p);permissions.refreshCommands(p);
        UUID penalty=ledger.data.logoutPenalties.get(p.getUUID());
        if(penalty!=null){
            if(!penalty.equals(((PenaltyReceipt)p).combat$penaltyReceipt())){
                if(!trySave()){p.connection.disconnect(text("Combat data could not be saved. Please retry shortly or contact staff.","red"));return;}
                ((PenaltyReceipt)p).combat$penaltyReceipt(penalty);if(p.isAlive())logoutDeath(p);
            }
            // Keep the journal until a subsequent login proves the death receipt reached player data.
            else{ledger.data.logoutPenalties.remove(p.getUUID());save();}
        }
        exposure(p);previous.put(p.getUUID(),status(p));
        events.join(p);
    }
    private void logoutDeath(ServerPlayer p){p.setHealth(0);p.die(p.damageSources().genericKill());}
    public boolean logoutPunished(ServerPlayer p){return !staffMode(p)&&TagLedger.penalty(status(p),config);}
    public boolean allowTeleport(ServerPlayer p,boolean notify){
        if(staff.frozen(p.getUUID())&&!staff.internalMove()){if(notify)p.sendSystemMessage(text("You cannot teleport during a staff review.","red"));return false;}
        if(!config.enabled||bypass(p))return true;
        boolean blocked=!healthy||status(p).active();if(blocked&&notify)denied(p);return !blocked;
    }
    public boolean command(ParseResults<CommandSourceStack> parsed,String raw){
        ServerPlayer actor=parsed.getContext().getSource().getPlayer();
        if(actor!=null&&staff.muted(actor.getUUID())&&matchesCommand(parsed,raw,config.mutedCommands)){actor.sendSystemMessage(text("You are muted.","red"));return false;}
        ServerPlayer p=parsed.getContext().getSource().getPlayer();if(p==null||!config.enabled||bypass(p))return true;
        if(!blockedCommand(parsed,raw))return true;return allowTeleport(p,true);
    }
    public boolean blockedCommand(ParseResults<CommandSourceStack> parsed,String raw){
        return matchesCommand(parsed,raw,config.blockedCommands);
    }
    private boolean matchesCommand(ParseResults<CommandSourceStack> parsed,String raw,List<String> rules){
        if(CommandRules.matches(raw,rules))return true;
        for(CommandContextBuilder<CommandSourceStack> context=parsed.getContext();context!=null;context=context.getChild()){
            StringBuilder path=new StringBuilder();
            for(var parsedNode:context.getNodes()){
                var node=parsedNode.getNode();if(node instanceof com.mojang.brigadier.tree.LiteralCommandNode<?>){if(!path.isEmpty())path.append(' ');path.append(node.getName());if(CommandRules.matches(path.toString(),rules))return true;}
                Set<CommandNode<?>> seen=Collections.newSetFromMap(new IdentityHashMap<>());
                for(CommandNode<?> redirect=node.getRedirect();redirect!=null&&seen.add(redirect);redirect=redirect.getRedirect())if(CommandRules.matches(redirect.getName(),rules))return true;
            }
        }
        return false;
    }
    public void denied(ServerPlayer p){long now=now();if(now-lastDenial.getOrDefault(p.getUUID(),0L)<1000)return;lastDenial.put(p.getUUID(),now);if(p.connection!=null)p.sendSystemMessage(message(format(config.blockedMessage,p),config.errorColor));}
    public String reasons(TagLedger.Status s){return Ui.caps(String.join(" / ",s.reasons().stream().map(Enum::name).toList()));}
    public String format(String pattern,ServerPlayer p){var s=currentStatus(p);return pattern.replace("{seconds}",Integer.toString(s.seconds())).replace("{time}",Ui.clock(s.millis())).replace("{reasons}",reasons(s)).replace("{player}",p.getName().getString()).replace("{opponents}",Integer.toString(s.opponents().size()));}
    public Component component(ServerPlayer p){var s=status(p);if(!s.active())return Component.empty();String color=s.combatMillis()>0?config.combatColor:s.warMillis()>0?config.warColor:config.airDropColor;return text(format(config.actionBarTemplate,p),color);}
    public Component append(Component original,ServerPlayer p){
        if(!config.actionBar||!status(p).active())return original;
        if(!config.appendToKingdomsActionBar)return component(p);
        return original.copy().append(text(config.separator,"dark_gray")).append(component(p));
    }
    public static Component text(String value,String color){ChatFormatting style;try{style=ChatFormatting.valueOf(color.toUpperCase(Locale.ROOT));}catch(Exception e){style=ChatFormatting.RED;}return Component.literal(Ui.caps(value)).withStyle(style).withStyle(s->s.withItalic(false));}
    private Component message(String value,String color){return config.prefix.isBlank()?text(value,color):Component.literal("["+Ui.caps(config.prefix)+"] ").withStyle(ChatFormatting.DARK_GRAY).append(text(value,color));}
    private void tell(ServerPlayer p,String value){if(config.chatNotifications&&p.connection!=null){var status=ledger.status(p.getUUID(),now());p.sendSystemMessage(message(format(value,p),status.combatMillis()>0?config.combatColor:status.warMillis()>0?config.warColor:config.airDropColor));}}
    void sound(ServerPlayer p,String id){if(!config.sounds||p.connection==null)return;var key=Identifier.tryParse(id);if(key!=null)BuiltInRegistries.SOUND_EVENT.get(key).ifPresent(sound->p.connection.send(new ClientboundSoundPacket(sound,SoundSource.UI,p.getX(),p.getY(),p.getZ(),config.soundVolume,1,p.getRandom().nextLong())));}
    public void save(){trySave();}
    private synchronized boolean trySave(){
        lastSaveAttempt=now();
        try{
            TagLedger.validate(ledger.data);CombatStore.write(stateFile,ledger.data);dirty=false;
            if(!healthy)KingdomsCombat.LOG.info("Combat persistence recovered; pending state saved to {}",stateFile);
            healthy=true;saveFailureReported=false;return true;
        }catch(RuntimeException e){
            healthy=false;dirty=true;
            if(!saveFailureReported||now()-lastSaveError>=30000){
                saveFailureReported=true;lastSaveError=now();
                KingdomsCombat.LOG.error("Combat persistence failed; retaining pending state for retry. Restricted teleports remain blocked and unjournaled logout deaths are deferred: {}",stateFile,e);
            }
            return false;
        }
    }
    public void reload(){CombatConfig next=CombatStore.read(configFile,CombatConfig.class,new CombatConfig());next.validate();config=next;CombatStore.write(configFile,next);}
    public void replaceConfig(CombatConfig next){next.validate();CombatStore.write(configFile,next);config=next;}
    public void stop(){stopping=true;staff.stop();events.hide();save();}
}
