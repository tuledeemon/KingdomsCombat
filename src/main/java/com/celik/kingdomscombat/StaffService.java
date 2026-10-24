package com.celik.kingdomscombat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.server.players.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class StaffService {
    public static final class Point {public String dimension;public double x,y,z;public float yaw,pitch;Point(){}Point(ServerPlayer p){dimension=p.level().dimension().identifier().toString();x=p.getX();y=p.getY();z=p.getZ();yaw=p.getYRot();pitch=p.getXRot();}}
    public static final class Mute {public long until;public String reason,actor;}
    public static final class Note {public long time;public String actor,action,reason;}
    public static final class State {
        public Map<UUID,String> names=new HashMap<>();public Map<UUID,Point> frozen=new HashMap<>();
        public Map<UUID,Mute> mutes=new HashMap<>();public Map<UUID,List<Note>> notes=new HashMap<>();
    }
    private final CombatService service;private final Path file,auditFile;
    final State state;
    private final Map<UUID,UUID> travel=new HashMap<>();private final Map<UUID,Long> travelDeadline=new HashMap<>();
    private final ThreadLocal<Boolean> moving=ThreadLocal.withInitial(()->false);
    private int ticks;
    StaffService(CombatService s){service=s;file=s.stateFile.resolveSibling("moderation.json");auditFile=s.stateFile.resolveSibling("staff-audit.jsonl");state=CombatStore.read(file,State.class,new State());validate();}
    private void validate(){if(state.names==null||state.frozen==null||state.mutes==null||state.notes==null)throw new IllegalStateException("Invalid moderation state.");for(Point p:state.frozen.values())validate(p);}
    private static void validate(Point p){if(p==null||Identifier.tryParse(p.dimension)==null||!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z)||!Float.isFinite(p.yaw)||!Float.isFinite(p.pitch))throw new IllegalStateException("Invalid saved staff location.");}
    void save(){CombatStore.write(file,state);}
    public boolean frozen(UUID id){return state.frozen.containsKey(id);}
    public Point anchor(UUID id){return state.frozen.get(id);}
    public boolean muted(UUID id){var m=state.mutes.get(id);return m!=null&&m.until>service.now();}
    public boolean internalMove(){return moving.get();}
    public void require(ServerPlayer actor,String tool){
        if(!service.staffMode(actor))throw new IllegalArgumentException("Enable staff mode first with /staffmode.");
        boolean allowed=me.lucko.fabric.api.permissions.v0.Permissions.check(actor.createCommandSourceStack(),service.config.staffToolPermissionPrefix+tool,true);
        if(!allowed)throw new IllegalArgumentException("You do not have permission to use this tool.");
    }
    public void require(CommandSourceStack source,String tool){if(source.getPlayer()!=null)require(source.getPlayer(),tool);}
    public UUID target(String name){var online=service.server.getPlayerList().getPlayerByName(name);if(online!=null)return online.getUUID();try{return UUID.fromString(name);}catch(IllegalArgumentException ignored){}return state.names.entrySet().stream().filter(e->e.getValue().equalsIgnoreCase(name)).map(Map.Entry::getKey).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown player. Use a known name or UUID."));}
    public ServerPlayer online(UUID id){var p=service.server.getPlayerList().getPlayer(id);if(p==null)throw new IllegalArgumentException("That player is offline.");return p;}
    public String name(UUID id){var p=service.server.getPlayerList().getPlayer(id);return p==null?state.names.getOrDefault(id,id.toString()):p.getGameProfile().name();}
    public String reason(String value){String result=value.strip();if(result.isBlank()||result.length()>service.config.staffReasonMaxLength||result.chars().anyMatch(c->Character.isISOControl(c)))throw new IllegalArgumentException("Enter a reason of 1–"+service.config.staffReasonMaxLength+" characters.");return result;}
    private void targetAllowed(CommandSourceStack actor,UUID id){
        if(actor.getPlayer()==null)return;
        if(actor.getPlayer().getUUID().equals(id))throw new IllegalArgumentException("You cannot moderate yourself.");
        var p=service.server.getPlayerList().getPlayer(id);
        var user=net.luckperms.api.LuckPermsProvider.get().getUserManager().getUser(id);
        if(p==null&&user==null)throw new IllegalArgumentException("Offline permissions are not loaded. Apply new punishments while the player is online, or use the server console.");
        boolean immune=p!=null?(service.staffMode(p)||StaffPermissions.raw(()->me.lucko.fabric.api.permissions.v0.Permissions.check(p,service.config.staffToolPermissionPrefix+"immune",false))):StaffPermissions.raw(()->user.getCachedData().getPermissionData().checkPermission(service.config.staffToolPermissionPrefix+"immune").asBoolean());
        if(immune)throw new IllegalArgumentException("That player is protected from moderation.");
    }
    private String actor(CommandSourceStack source){return source.getPlayer()==null?"console":source.getPlayer().getGameProfile().name();}
    private void audit(CommandSourceStack actor,UUID id,String action,String reason){
        var note=new Note();note.time=service.now();note.actor=actor(actor);note.action=action;note.reason=reason;
        var notes=state.notes.computeIfAbsent(id,k->new ArrayList<>());notes.add(note);while(notes.size()>service.config.staffNotesLimit)notes.removeFirst();save();
        try{Files.createDirectories(auditFile.getParent());var row=new com.google.gson.JsonObject();row.addProperty("time",Instant.ofEpochMilli(note.time).toString());row.addProperty("actor",note.actor);row.addProperty("target",id.toString());row.addProperty("action",action);row.addProperty("reason",reason);Files.writeString(auditFile,row+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(java.io.IOException e){throw new IllegalStateException("Could not append the moderation audit log.",e);}
        notifyStaff(note.actor+" • "+action+" • "+name(id)+" • "+reason);
    }
    public List<Note> notes(UUID id){return List.copyOf(state.notes.getOrDefault(id,List.of()));}
    public void note(CommandSourceStack actor,UUID id,String text){require(actor,"notes");audit(actor,id,"note",reason(text));}
    public void warn(CommandSourceStack actor,UUID id,String text){require(actor,"warn");targetAllowed(actor,id);String why=reason(text);var target=online(id);audit(actor,id,"warn",why);target.sendSystemMessage(CombatService.text("Warning • "+why,"gold"));}
    public void freeze(CommandSourceStack actor,UUID id,boolean enabled){
        require(actor,"freeze");if(enabled)targetAllowed(actor,id);
        if(enabled){var p=online(id);if(frozen(id))return;p.stopRiding();p.stopUsingItem();p.closeContainer();state.frozen.put(id,new Point(p));service.ledger.clear(id);service.save();p.setDeltaMovement(Vec3.ZERO);p.sendSystemMessage(CombatService.text("You are frozen for a staff review. You can still use chat. Logging out is recorded.","red"));}
        else{state.frozen.remove(id);travel.remove(id);travelDeadline.remove(id);}
        audit(actor,id,enabled?"freeze":"unfreeze",enabled?"Staff review":"Review ended");
        var p=service.server.getPlayerList().getPlayer(id);if(p!=null&&!enabled)p.sendSystemMessage(CombatService.text("You have been unfrozen.","aqua"));
    }
    public void mute(CommandSourceStack actor,UUID id,long seconds,String why){require(actor,"mute");targetAllowed(actor,id);String reason=reason(why);if(seconds<1&&seconds!=-1)throw new IllegalArgumentException("Invalid mute duration.");Mute m=new Mute();m.until=seconds==-1?Long.MAX_VALUE:Math.addExact(service.now(),Math.multiplyExact(seconds,1000));m.actor=actor(actor);m.reason=reason;state.mutes.put(id,m);audit(actor,id,"mute",reason+(seconds==-1?" (permanent)":" ("+seconds+"s)"));var p=service.server.getPlayerList().getPlayer(id);if(p!=null)p.sendSystemMessage(CombatService.text("You have been muted • "+reason,"red"));}
    public void unmute(CommandSourceStack actor,UUID id){require(actor,"mute");state.mutes.remove(id);audit(actor,id,"unmute","Mute removed");}
    public void kick(CommandSourceStack actor,UUID id,String why){require(actor,"kick");targetAllowed(actor,id);String reason=reason(why);var target=online(id);audit(actor,id,"kick",reason);target.connection.disconnect(CombatService.text("Kicked • "+reason,"red"));}
    public void ban(CommandSourceStack actor,UUID id,long seconds,String why){require(actor,"ban");targetAllowed(actor,id);String reason=reason(why);if(seconds<1&&seconds!=-1)throw new IllegalArgumentException("Invalid ban duration.");Date until=seconds==-1?null:new Date(Math.addExact(service.now(),Math.multiplyExact(seconds,1000)));audit(actor,id,"ban",reason+(seconds==-1?" (permanent)":" ("+seconds+"s)"));service.server.getPlayerList().getBans().add(new UserBanListEntry(new NameAndId(id,name(id)),new Date(service.now()),actor(actor),until,reason));var p=service.server.getPlayerList().getPlayer(id);if(p!=null)p.connection.disconnect(CombatService.text("Banned • "+reason,"red"));}
    public void unban(CommandSourceStack actor,UUID id){require(actor,"ban");service.server.getPlayerList().getBans().remove(new NameAndId(id,name(id)));audit(actor,id,"unban","Ban removed");}
    public void staffChat(ServerPlayer actor,String text){require(actor,"chat");String message=reason(text);notifyStaff(actor.getGameProfile().name()+" » "+message);}
    public void notifyStaff(String text){for(var p:service.server.getPlayerList().getPlayers())if(service.staffMode(p))p.sendSystemMessage(CombatService.text("[staff] "+text,"aqua"));}
    public void enter(ServerPlayer p){audit(p.createCommandSourceStack(),p.getUUID(),"staffmode on","Session started");}
    public void leave(ServerPlayer p){
        p.closeContainer();normalizeMode(p);
        audit(p.createCommandSourceStack(),p.getUUID(),"staffmode off","Session ended");
        service.permissions.refreshCommands(p);
    }
    private void normalizeMode(ServerPlayer p){
        if(p.isCreative()||p.isSpectator())p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().flying=false;p.getAbilities().mayfly=false;p.getAbilities().invulnerable=false;p.onUpdateAbilities();
    }
    private void move(ServerPlayer p,Point point,boolean needStaff,Runnable done){
        validate(point);var key=ResourceKey.create(Registries.DIMENSION,Identifier.parse(point.dimension));ServerLevel level=service.server.getLevel(key);if(level==null)throw new IllegalArgumentException("The saved dimension is not available.");
        UUID request=UUID.randomUUID();travel.put(p.getUUID(),request);travelDeadline.put(p.getUUID(),service.now()+service.config.staffTeleportTimeoutSeconds*1000L);
        Runnable finish=()->{if(!request.equals(travel.get(p.getUUID()))||service.server.getPlayerList().getPlayer(p.getUUID())!=p||state.frozen.get(p.getUUID())!=point||needStaff&&!service.staffMode(p))return;travel.remove(p.getUUID());travelDeadline.remove(p.getUUID());moving.set(true);try{if(p.teleportTo(level,point.x,point.y,point.z,Set.of(),point.yaw,point.pitch,false)){if(frozen(p.getUUID())){state.frozen.put(p.getUUID(),new Point(p));save();}done.run();}}finally{moving.set(false);}};
        int x=((int)Math.floor(point.x))>>4,z=((int)Math.floor(point.z))>>4;
        if(level.getChunkSource().getChunkNow(x,z)!=null){finish.run();return;}
        CompletableFuture.supplyAsync(()->level.getChunkSource().getChunkFuture(x,z,ChunkStatus.FULL,true)).thenCompose(f->f).whenComplete((result,error)->service.server.execute(()->{if(error==null&&result!=null&&result.isSuccess())finish.run();else if(travel.remove(p.getUUID(),request)){travelDeadline.remove(p.getUUID());p.sendSystemMessage(CombatService.text("Your frozen position could not be loaded. Contact staff for help.","red"));}}));
    }
    public void join(ServerPlayer p){
        state.names.put(p.getUUID(),p.getGameProfile().name());
        if(StaffPermissions.restricted(p))normalizeMode(p);
        if(frozen(p.getUUID())){move(p,anchor(p.getUUID()),false,()->{});p.sendSystemMessage(CombatService.text("Your staff review is still active. Please contact staff in chat.","red"));}
        save();
    }
    public void disconnect(ServerPlayer p){
        if(frozen(p.getUUID()))audit(service.server.createCommandSourceStack(),p.getUUID(),"frozen logout","Player disconnected during a staff review");
        if(service.staffMode(p))normalizeMode(p);
        travel.remove(p.getUUID());travelDeadline.remove(p.getUUID());
    }
    public void tick(){
        ticks++;
        for(var p:service.server.getPlayerList().getPlayers())if(frozen(p.getUUID())){p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;}
        if(ticks%20!=0)return;
        for(UUID id:new ArrayList<>(travelDeadline.keySet()))if(travelDeadline.getOrDefault(id,0L)<=service.now()){travel.remove(id);travelDeadline.remove(id);var p=service.server.getPlayerList().getPlayer(id);if(p!=null)p.sendSystemMessage(CombatService.text("Frozen position recovery timed out. Contact staff for help.","red"));}
        for(var p:service.server.getPlayerList().getPlayers()){
            if(service.staffFlag(p.getUUID())&&!service.staffMode(p))service.toggleStaffMode(p);
            if(service.config.staffActionBar&&service.staffMode(p))p.connection.send(new ClientboundSetActionBarTextPacket(CombatService.text("staff mode • active","aqua")));
        }
    }
    public void stop(){for(var p:service.server.getPlayerList().getPlayers())if(service.staffMode(p))normalizeMode(p);save();}
}
