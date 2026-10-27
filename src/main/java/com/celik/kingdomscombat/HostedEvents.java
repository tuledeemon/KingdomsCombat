package com.celik.kingdomscombat;

import com.celik.kingdoms.Ui;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.action.*;
import java.util.List;
import java.util.Optional;

/** One server-wide event announcement; gameplay rules remain up to its host. */
public final class HostedEvents {
    public static final class Event {
        public UUID id=UUID.randomUUID();
        public String name,host;
        public String description="No description has been set.";
        public long startedAt,endsAt;
        public Destination location;
    }
    public record Destination(String dimension,double x,double y,double z,float yaw,float pitch) {
        static Destination at(ServerPlayer p){return new Destination(p.level().dimension().identifier().toString(),p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot());}
        void validate(){if(Identifier.tryParse(dimension)==null||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(x)>30000000||Math.abs(z)>30000000)throw new IllegalStateException("Invalid event location.");}
    }
    public static final class State {
        public int schemaVersion=1;
        public Event event;
        public Destination location;
        public String description="No description has been set.";
    }
    private final CombatService service;
    private final Path file;
    private final State state;
    public final EventChests chests;
    private ServerBossEvent bar;
    private record Travel(UUID eventId,Destination location,long expiresAt){}
    private final Map<UUID,Travel> pending=new HashMap<>();
    HostedEvents(CombatService service,Path file){
        this.service=service;this.file=file;
        state=CombatStore.read(file,State.class,new State());
        validate(state);
        if(state.event!=null&&!active()){state.event=null;save();}
        chests=new EventChests(service,file.resolveSibling("event-chests.json"));
    }
    public static void validate(State state){
        if(state==null||state.schemaVersion!=1)throw new IllegalStateException("Invalid hosted event state.");
        var e=state.event;
        if(e!=null&&(e.id==null||e.name==null||e.name.isBlank()||e.name.length()>64||e.host==null||e.startedAt<0||e.endsAt<=e.startedAt))throw new IllegalStateException("Invalid hosted event.");
        if(state.location!=null)state.location.validate();if(e!=null&&e.location!=null)e.location.validate();
        if(state.description==null||state.description.length()>4096||e!=null&&(e.description==null||e.description.length()>4096))throw new IllegalStateException("Invalid event description.");
    }
    public boolean active(){return state.event!=null&&state.event.endsAt>service.now();}
    public String name(){return active()?state.event.name:"";}
    public UUID id(){return active()?state.event.id:null;}
    public long remainingMillis(){return state.event==null?0:Math.max(0,state.event.endsAt-service.now());}
    public ServerBossEvent bossBar(){return bar;}
    public String description(){return active()?name()+" • "+Ui.clock(remainingMillis())+" remaining.":"No hosted event is active. Use /eventhost \"Event name\" 10m.";}
    private void require(CommandSourceStack source){
        if(!CombatService.allowed(source,service.config.eventHostPermission,service.config.adminLevel))throw new IllegalArgumentException("Event host permission required.");
    }
    public void start(CommandSourceStack source,String name,String time){
        require(source);
        chests.validateStart();
        name=name.trim();
        if(name.isBlank()||name.length()>64||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Use an event name of 1–64 characters.");
        int duration=HostedEventRules.seconds(time,service.config.eventMaxSeconds);
        if(active())throw new IllegalArgumentException("An event is already running. Stop it before starting another.");
        if(state.event!=null)finish(false);
        var event=new Event();event.name=name;event.host=source.getDisplayName().getString();event.startedAt=service.now();event.endsAt=event.startedAt+duration*1000L;
        if(state.location==null&&source.getPlayer()!=null)state.location=Destination.at(source.getPlayer());
        event.location=state.location;
        event.description=state.description;
        chests.start(event.id);state.event=event;save();updateBar();announce(event,service.config.eventStartMessage,"Event started",service.config.tagSound);
    }
    public void stop(CommandSourceStack source){require(source);if(state.event==null)throw new IllegalArgumentException("No hosted event is active.");finish(true);}
    public void tick(){
        chests.tick();
        pending.entrySet().removeIf(entry->{if(entry.getValue().expiresAt()>service.now())return false;var player=service.server.getPlayerList().getPlayer(entry.getKey());if(player!=null)player.sendSystemMessage(CombatService.text("Event teleport timed out. Try again.",service.config.errorColor));return true;});
        if(state.event==null)return;if(!active())finish(false);else updateBar();
    }
    public void join(ServerPlayer player){chests.join(player);if(active()){updateBar();if(player.connection!=null)bar.addPlayer(player);}}
    void claimed(){if(state.event!=null)finish(false);}
    public void leave(ServerPlayer player){pending.remove(player.getUUID());if(bar!=null)bar.removePlayer(player);}
    public void hide(){pending.clear();if(bar!=null){bar.removeAllPlayers();bar=null;}}
    public void setLocation(CommandSourceStack source)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        require(source);state.location=Destination.at(source.getPlayerOrException());if(state.event!=null)state.event.location=state.location;save();pending.clear();
    }
    public void setDescription(CommandSourceStack source,String description){require(source);description=description.trim();if(description.isBlank()||description.length()>service.config.eventDescriptionMaxLength)throw new IllegalArgumentException("Use a description of 1–"+service.config.eventDescriptionMaxLength+" characters.");state.description=description;if(state.event!=null)state.event.description=description;save();}
    public void menu(ServerPlayer player){
        if(service.arenas.active()){service.arenas.menu(player);return;}
        if(!active())throw new IllegalArgumentException("No hosted event is active.");
        var event=state.event;var color=service.config.eventColor;
        List<net.minecraft.server.dialog.body.DialogBody> body=new java.util.ArrayList<>(List.of(new PlainMessage(CombatService.text(event.description,"gray"),300),new PlainMessage(CombatService.text("Duration: "+Ui.clock(event.endsAt-event.startedAt)+" • Remaining: "+Ui.clock(remainingMillis()),color),300),new PlainMessage(CombatService.text("Host: "+event.host,color),300)));
        String chestInfo=chests.information();if(!chestInfo.isEmpty())body.add(new PlainMessage(CombatService.text(chestInfo,color),300));
        var common=new CommonDialogData(CombatService.text(event.name,color),Optional.empty(),true,false,DialogAction.CLOSE,body,List.of());
        var teleport=new ActionButton(new CommonButtonData(CombatService.text("Teleport to event",color),160),Optional.of(new StaticAction(new net.minecraft.network.chat.ClickEvent.RunCommand("/event teleport "+event.id))));
        var close=new ActionButton(new CommonButtonData(CombatService.text("Close","gray"),100),Optional.empty());
        player.closeContainer();player.openDialog(Holder.direct(new MultiActionDialog(common,List.of(teleport),Optional.of(close),1)));
    }
    public void travel(ServerPlayer player,UUID eventId){if(service.arenas.active()&&service.arenas.data.event.id.equals(eventId)){service.arenas.travel(player,eventId);return;}if(!active()||!state.event.id.equals(eventId))throw new IllegalArgumentException("This event is no longer active. Open /event again.");travel(player);}
    public void travel(ServerPlayer player){
        if(!active())throw new IllegalArgumentException("No hosted event is active.");
        if(!service.allowTeleport(player,true))return;
        var destination=state.event.location;
        if(destination==null)throw new IllegalArgumentException("The host has not set an event location.");
        if(pending.containsKey(player.getUUID()))throw new IllegalArgumentException("Your event teleport is already loading.");
        var level=service.server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(destination.dimension())));
        if(level==null||!level.getWorldBorder().isWithinBounds(destination.x(),destination.z()))throw new IllegalArgumentException("The event location is unavailable or outside the world border.");
        var request=new Travel(state.event.id,destination,service.now()+service.config.eventTeleportTimeoutSeconds*1000L);pending.put(player.getUUID(),request);
        int chunkX=((int)Math.floor(destination.x()))>>4,chunkZ=((int)Math.floor(destination.z()))>>4;
        if(level.getChunkSource().getChunkNow(chunkX,chunkZ)!=null){completeTravel(player,request,level,true);return;}
        player.sendSystemMessage(CombatService.text("Loading event location…",service.config.eventColor));
        // The vanilla API waits when invoked on the server thread. Its off-thread
        // path schedules chunk work on the proper executor and returns a future.
        var chunks=level.getChunkSource();
        try{CompletableFuture.supplyAsync(()->chunks.getChunkFuture(chunkX,chunkZ,ChunkStatus.FULL,true)).thenCompose(future->future).whenComplete((result,error)->service.server.execute(()->completeTravel(player,request,level,error==null&&result!=null&&result.isSuccess())));}
        catch(RuntimeException error){pending.remove(player.getUUID(),request);throw new IllegalArgumentException("The event location could not be loaded.",error);}
    }
    private void completeTravel(ServerPlayer player,Travel request,ServerLevel level,boolean loaded){
        if(pending.get(player.getUUID())!=request)return;
        pending.remove(player.getUUID());
        if(service.stopping||service.server.getPlayerList().getPlayer(player.getUUID())!=player||!player.isAlive())return;
        if(!active()||!state.event.id.equals(request.eventId())||!request.location().equals(state.event.location)||service.now()>=request.expiresAt()){player.sendSystemMessage(CombatService.text("The event teleport is no longer available.",service.config.errorColor));return;}
        // Tags and area restrictions may have changed while the destination loaded.
        if(!service.allowTeleport(player,true))return;
        var d=request.location();
        if(!loaded||!level.getWorldBorder().isWithinBounds(d.x(),d.z())){player.sendSystemMessage(CombatService.text("The event location could not be loaded.",service.config.errorColor));return;}
        if(player.teleportTo(level,d.x(),d.y(),d.z(),Set.of(),d.yaw(),d.pitch(),false))player.sendSystemMessage(CombatService.text("Joined "+state.event.name+".",service.config.eventColor));
    }
    private void save(){validate(state);CombatStore.write(file,state);}
    private String format(String template,Event event){return template.replace("{name}",event.name).replace("{host}",event.host).replace("{time}",Ui.clock(Math.max(0,event.endsAt-service.now())));}
    private void updateBar(){
        var event=state.event;
        var title=CombatService.text(format(service.config.eventBarTemplate,event),service.config.eventColor);
        var color=BossEvent.BossBarColor.valueOf(service.config.eventBossBarColor.toUpperCase(Locale.ROOT));
        if(bar==null)bar=new ServerBossEvent(event.id,title,color,BossEvent.BossBarOverlay.PROGRESS);
        bar.setName(title);bar.setColor(color);bar.setProgress((float)Math.clamp(remainingMillis()/(double)(event.endsAt-event.startedAt),0,1));
        for(var p:service.server.getPlayerList().getPlayers())if(p.connection!=null)bar.addPlayer(p);
    }
    private void finish(boolean cancelled){
        var event=state.event;state.event=null;try{save();}catch(RuntimeException error){state.event=event;throw error;}hide();
        announce(event,cancelled?service.config.eventStopMessage:service.config.eventEndMessage,cancelled?"Event stopped":"Event ended",service.config.endSound);
    }
    private void announce(Event event,String message,String title,String sound){
        var config=service.config;
        if(config.eventChat)service.server.getPlayerList().broadcastSystemMessage(CombatService.text("[event] "+format(message,event),config.eventColor),false);
        for(var p:service.server.getPlayerList().getPlayers())if(p.connection!=null){
            if(config.eventTitles){p.connection.send(new ClientboundSetTitlesAnimationPacket(5,50,15));p.connection.send(new ClientboundSetTitleTextPacket(CombatService.text(title,config.eventColor)));p.connection.send(new ClientboundSetSubtitleTextPacket(CombatService.text(event.name,config.eventColor)));}
            if(config.eventSounds)service.sound(p,sound);
        }
    }
}
