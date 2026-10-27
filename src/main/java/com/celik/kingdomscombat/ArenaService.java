package com.celik.kingdomscombat;

import com.celik.kingdoms.*;
import com.celik.kingdoms.mixin.AirDropPlayerSaveInvoker;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static com.celik.kingdomscombat.ArenaRules.*;

/** One arena, one current match. Durable inventory backups precede every kit swap. */
public final class ArenaService {
    private static final TicketType TICKET=new TicketType(0,TicketType.FLAG_LOADING|TicketType.FLAG_SIMULATION|TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    private static final TicketType RETURN_TICKET=new TicketType(0,TicketType.FLAG_LOADING|TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    private record ReturnSite(ServerLevel level,ChunkPos pos){}
    private final Map<ReturnSite,Integer> returnTickets=new HashMap<>();
    final CombatService service;
    final Path file;
    Data data;
    private boolean internal,loading,failed;
    private int ticks,lastCountdown=-1;
    private final Set<UUID> restoring=new HashSet<>();
    private final Set<ChunkPos> tickets=new HashSet<>();
    private ServerLevel ticketLevel;
    private final ServerBossEvent bar=new ServerBossEvent(UUID.randomUUID(),CombatService.text("Tournament","gold"),BossEvent.BossBarColor.YELLOW,BossEvent.BossBarOverlay.PROGRESS);
    public ArenaService(CombatService service){
        this.service=service;file=service.stateFile.resolveSibling("arena.json");data=CombatStore.read(file,Data.class,new Data());ArenaRules.validate(data);
        // A restart voids only the interrupted match, not its bracket or any original inventory.
        if(data.match!=null){data.match=null;if(data.event!=null)data.event.paused=true;}
        for(Snapshot snapshot:data.snapshots.values())snapshot.restoring=true;
        save();
    }
    void save(){ArenaRules.validate(data);try{CombatStore.write(file,data);failed=false;}catch(RuntimeException e){failed=true;throw e;}}
    boolean active(){return data.event!=null&&(data.event.phase==Phase.APPLICATIONS||data.event.phase==Phase.RUNNING);}
    public boolean reserved(UUID player){return data.snapshots.containsKey(player);}
    public boolean current(UUID player){return data.match!=null&&data.match.phase!=MatchPhase.PREPARING&&data.match.alive.contains(player)&&reserved(player)&&!data.snapshots.get(player).restoring;}
    public boolean fighter(UUID player){return !failed&&data.match!=null&&data.match.phase==MatchPhase.FIGHTING&&data.match.alive.contains(player)&&reserved(player)&&!data.snapshots.get(player).restoring;}
    public boolean internalMove(){return internal;}
    public boolean inside(Level level,BlockPos pos){return inside(level,pos.getX(),pos.getY(),pos.getZ());}
    public boolean inside(Level level,double x,double y,double z){return service.config.arenaEnabled&&data.setup.region!=null&&data.setup.region.contains(level.dimension().identifier().toString(),x,y,z);}
    public boolean overlapsChunk(Level level,int x,int z){Region r=data.setup.region;return service.config.arenaEnabled&&r!=null&&r.dimension.equals(level.dimension().identifier().toString())&&x>=(r.minX>>4)&&x<=(r.maxX>>4)&&z>=(r.minZ>>4)&&z<=(r.maxZ>>4);}
    public boolean admin(ServerPlayer p){return service.staffMode(p)||CombatService.allowed(p.createCommandSourceStack(),service.config.adminPermission,service.config.adminLevel);}
    public boolean build(ServerPlayer p,Level level,BlockPos pos){return !inside(level,pos)||!reserved(p.getUUID())&&admin(p)||service.config.arenaBuildAllowed&&fighter(p.getUUID())&&inside(p.level(),p.blockPosition());}
    public boolean canInteract(ServerPlayer p,BlockPos pos){if(reserved(p.getUUID()))return fighter(p.getUUID())&&inside(p.level(),pos)&&p.level().getBlockEntity(pos)==null;return !inside(p.level(),pos)||admin(p);}
    public Boolean pvp(Entity target,DamageSource source){
        if(!(target instanceof ServerPlayer victim))return null;
        UUID owner=Protection.owner(source.getEntity());if(owner==null)owner=Protection.owner(source.getDirectEntity());
        ServerPlayer attacker=owner==null?null:service.server.getPlayerList().getPlayer(owner);
        boolean affected=inside(victim.level(),victim.blockPosition())||reserved(victim.getUUID())||attacker!=null&&(inside(attacker.level(),attacker.blockPosition())||reserved(attacker.getUUID()));
        if(!affected)return null;
        if(attacker==null)return reserved(victim.getUUID())?fighter(victim.getUUID()):true;
        if(fighter(victim.getUUID())&&fighter(attacker.getUUID())&&inside(victim.level(),victim.blockPosition())&&inside(attacker.level(),attacker.blockPosition()))return attacker==victim||!Objects.equals(ArenaRules.side(data.event,victim.getUUID()),ArenaRules.side(data.event,attacker.getUUID()));
        // Staff may moderate spectators, but never damage an official fighter or affect Elo.
        return !reserved(victim.getUUID())&&!reserved(attacker.getUUID())&&admin(victim)&&admin(attacker);
    }
    public boolean mutation(Level level,BlockPos pos,BlockState next){
        var context=Protection.current();
        if(internal)return true;
        if(!inside(level,pos))return context==null||context.actor()==null||!reserved(context.actor());
        if(context==null||context.cause()!=Protection.Cause.PLAYER||!(context.entity() instanceof ServerPlayer actor)||!build(actor,level,pos))return level.getBlockState(pos).equals(next);
        if(!reserved(actor.getUUID()))return true;
        if(level.getBlockEntity(pos)!=null||next.hasBlockEntity())return false;
        String key=pos.getX()+","+pos.getY()+","+pos.getZ();
        if(!data.changedBlocks.containsKey(key)){
            if(data.changedBlocks.size()>=service.config.arenaMaxChangedBlocks)return false;
            data.changedBlocks.put(key,BlockState.CODEC.encodeStart(JsonOps.INSTANCE,level.getBlockState(pos)).getOrThrow().toString());
            try{save();}catch(RuntimeException e){return false;}
        }
        return true;
    }
    public boolean admit(Entity entity){
        var context=Protection.current();boolean kit=context!=null&&context.actor()!=null&&reserved(context.actor());
        if(!kit&&entity instanceof net.minecraft.world.entity.projectile.Projectile projectile&&projectile.getOwner()!=null)kit=reserved(projectile.getOwner().getUUID());
        if(!kit)return true;
        if(!(entity instanceof net.minecraft.world.entity.projectile.Projectile)&&!(entity instanceof net.minecraft.world.entity.item.ItemEntity)&&!(entity instanceof net.minecraft.world.entity.AreaEffectCloud)&&!(entity instanceof net.minecraft.world.entity.ExperienceOrb))return false;
        if(entity instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow)arrow.pickup=net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.DISALLOWED;
        entity.addTag("kingdomscombat_arena");if(data.match!=null)entity.addTag("kingdomscombat_match_"+data.match.id);return true;
    }
    public boolean entityActive(Entity entity){return data.match!=null&&entity.entityTags().contains("kingdomscombat_match_"+data.match.id);}
    private void cleanupEntities(){if(data.setup.region==null)return;Region r=data.setup.region;ServerLevel level=level(r.dimension);for(Entity entity:level.getEntities((Entity)null,new AABB(r.minX-16,r.minY-16,r.minZ-16,r.maxX+17,r.maxY+17,r.maxZ+17),e->e.entityTags().contains("kingdomscombat_arena")))entity.discard();}
    private void idle(){if(active()||!data.snapshots.isEmpty()||!data.changedBlocks.isEmpty())throw new IllegalArgumentException("Stop the tournament and finish inventory/terrain recovery before editing the arena.");}
    static Point point(ServerPlayer p){return new Point(p.level().dimension().identifier().toString(),p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot());}
    private ServerLevel level(String dimension){ServerLevel level=service.server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(dimension)));if(level==null)throw new IllegalArgumentException("Arena dimension is unavailable.");return level;}
    public void corner(ServerPlayer p,int number){idle();if(number==1)data.setup.corner1=point(p);else data.setup.corner2=point(p);save();}
    public void claim(){
        idle();Point a=data.setup.corner1,b=data.setup.corner2;require(a!=null&&b!=null&&a.dimension.equals(b.dimension),"Select two corners in the same dimension.");
        Region region=new Region();region.dimension=a.dimension;region.minX=(int)Math.floor(Math.min(a.x,b.x));region.maxX=(int)Math.floor(Math.max(a.x,b.x));region.minY=(int)Math.floor(Math.min(a.y,b.y));region.maxY=(int)Math.floor(Math.max(a.y,b.y));region.minZ=(int)Math.floor(Math.min(a.z,b.z));region.maxZ=(int)Math.floor(Math.max(a.z,b.z));
        require(region.volume()<=service.config.arenaMaxRegionVolume,"Arena exceeds the configured block-volume cap.");
        require((long)((region.maxX>>4)-(region.minX>>4)+1)*((region.maxZ>>4)-(region.minZ>>4)+1)<=service.config.arenaMaxRegionChunks,"Arena exceeds the configured chunk cap.");
        require(region.minY>=level(a.dimension).getMinY()&&region.maxY<level(a.dimension).getMaxY(),"Arena corners are outside build height.");
        wilderness(region);data.setup.region=region;data.setup.gates.clear();save();
    }
    private void wilderness(Region region){
        var kingdoms=KingdomsMod.service;if(kingdoms==null)throw new IllegalArgumentException("Kingdoms is not ready.");ServerLevel level=level(region.dimension);
        for(int x=region.minX>>4;x<=region.maxX>>4;x++)for(int z=region.minZ>>4;z<=region.maxZ>>4;z++)require(kingdoms.land(level,new BlockPos(x*16,region.minY,z*16))==null,"Arena must be in wilderness, outside kingdom claims.");
    }
    public void unclaim(){idle();data.setup.region=null;data.setup.gates.clear();save();}
    public void spawn(ServerPlayer p,int side){idle();require(inside(p.level(),p.blockPosition()),"Stand inside the claimed arena.");Point pos=point(p);if(side==1)data.setup.spawn1=pos;else data.setup.spawn2=pos;save();}
    public void kit(ServerPlayer p){idle();List<String> kit=new ArrayList<>();boolean any=false;for(int i=0;i<p.getInventory().getContainerSize();i++){ItemStack stack=p.getInventory().getItem(i);require(!Artifacts.marked(stack)&&!KingdomCores.isToken(stack)&&!Artifacts.fixerMarked(stack),"Unique tracked items cannot be copied into an arena kit.");require(!stack.has(DataComponents.CONTAINER)&&!stack.has(DataComponents.BUNDLE_CONTENTS),"Remove storage items from the kit template.");kit.add(encode(stack));any|=!stack.isEmpty();}require(any,"Equip the kit in your inventory and armor slots first.");data.setup.kit=kit;save();}
    public void gate(ServerPlayer p,boolean add){
        idle();HitResult hit=p.pick(6,0,false);require(hit instanceof BlockHitResult&&hit.getType()==HitResult.Type.BLOCK,"Look at a gate block within six blocks.");BlockPos pos=((BlockHitResult)hit).getBlockPos();require(inside(p.level(),pos),"Gate blocks must be inside the arena.");
        if(!add){data.setup.gates.removeIf(g->g.x==pos.getX()&&g.y==pos.getY()&&g.z==pos.getZ());save();return;}
        BlockState state=p.level().getBlockState(pos);require(!state.isAir()&&state.getDestroySpeed(p.level(),pos)>=0&&!state.hasBlockEntity()&&state.getFluidState().isEmpty(),"Choose a breakable solid gate block without inventory or fluid.");
        require(data.setup.gates.size()<service.config.arenaMaxGates,"Gate block limit reached.");data.setup.gates.removeIf(g->g.x==pos.getX()&&g.y==pos.getY()&&g.z==pos.getZ());Gate gate=new Gate();gate.x=pos.getX();gate.y=pos.getY();gate.z=pos.getZ();gate.state=BlockState.CODEC.encodeStart(JsonOps.INSTANCE,state).getOrThrow().toString();data.setup.gates.add(gate);save();
    }
    void readySetup(){
        require(service.config.arenaEnabled,"Arena events are disabled.");require(data.setup.region!=null&&data.setup.spawn1!=null&&data.setup.spawn2!=null&&!data.setup.kit.isEmpty(),"Claim the arena, set both spawns and save a kit first.");
        Region r=data.setup.region;wilderness(r);for(Point p:List.of(data.setup.spawn1,data.setup.spawn2))require(r.contains(p.dimension,p.x,p.y,p.z),"Both spawns must be inside the arena.");
        require(Math.hypot(data.setup.spawn1.x-data.setup.spawn2.x,data.setup.spawn1.z-data.setup.spawn2.z)>=2,"Separate the two spawning points by at least two blocks.");
        require(data.snapshots.isEmpty(),"Some original inventories still need recovery.");
    }
    public void open(String name,int teamSize){
        require(!active(),"Stop the current tournament first.");readySetup();require(name!=null&&!name.isBlank()&&name.length()<=64,"Choose an event name of 1–64 characters.");require(teamSize==1||teamSize==2,"Choose 1v1 or 2v2.");
        Event event=new Event();event.name=name;event.teamSize=teamSize;data.event=event;save();announce(name+" • "+teamSize+"v"+teamSize+" applications are open. Use /tournament. Banners are optional for shield customization.");
    }
    private String banner(ServerPlayer p){ItemStack held=p.getMainHandItem();return held.getItem() instanceof BannerItem?encode(held.copyWithCount(1)):"";}
    private void available(ServerPlayer p){require(p.isAlive()&&!p.isSpectator()&&!p.isCreative()&&!service.staffMode(p)&&!service.staff.frozen(p.getUUID()),"Join alive in survival, with staff mode off and no staff review active.");require(p.containerMenu.getCarried().isEmpty(),"Put the item on your cursor into your inventory first.");require(!service.status(p).active(),"Finish your combat, war or AirDrop restriction first.");require(!reserved(p.getUUID()),"Your previous arena inventory must finish restoring first.");require(!KingdomsMod.service.artifacts.crown.locked(p),"Remove bound event equipment before entering an arena.");}
    public void apply(ServerPlayer p){Event e=applications();require(e.teamSize==1,"For 2v2, invite your teammate; they must accept.");available(p);require(ArenaRules.side(e,p.getUUID())==null,"You are already registered.");require(e.entries.size()<service.config.arenaMaxTeams,"The application list is full.");Entry entry=new Entry();entry.captain=p.getUUID();entry.players.add(p.getUUID());entry.banners.put(p.getUUID(),banner(p));e.entries.put(entry.captain,entry);save();tell(p,entry.banners.get(p.getUUID()).isEmpty()?"Application accepted • standard kit.":"Application accepted • banner saved for your shield.");}
    public void invite(ServerPlayer captain,ServerPlayer teammate){Event e=applications();require(e.teamSize==2,"Team invitations are for 2v2 events.");available(captain);available(teammate);require(captain!=teammate&&ArenaRules.side(e,captain.getUUID())==null&&ArenaRules.side(e,teammate.getUUID())==null,"Both players must be unregistered and different.");e.invitations.put(teammate.getUUID(),captain.getUUID());save();tell(teammate,service.staff.name(captain.getUUID())+" invited you to a tournament team. Accept in /tournament; a banner is optional.");}
    public void accept(ServerPlayer teammate){Event e=applications();UUID captainId=e.invitations.get(teammate.getUUID());require(captainId!=null,"No team invitation is waiting.");ServerPlayer captain=online(captainId);available(captain);available(teammate);require(e.entries.size()<service.config.arenaMaxTeams,"The application list is full.");require(ArenaRules.side(e,captainId)==null&&ArenaRules.side(e,teammate.getUUID())==null,"One of you already joined a team.");Entry entry=new Entry();entry.captain=captainId;entry.players.add(captainId);entry.players.add(teammate.getUUID());entry.banners.put(captainId,banner(captain));entry.banners.put(teammate.getUUID(),banner(teammate));e.entries.put(captainId,entry);e.invitations.values().removeIf(captainId::equals);e.invitations.remove(teammate.getUUID());save();for(UUID id:entry.players)tell(online(id),"Team application accepted. Held banners customize shields; otherwise the kit is unchanged.");}
    public void add(List<ServerPlayer> players){Event e=applications();require(players.size()==e.teamSize,"Choose exactly "+e.teamSize+" players for this team.");require(e.entries.size()<service.config.arenaMaxTeams,"The application list is full.");Entry entry=new Entry();entry.captain=players.getFirst().getUUID();for(ServerPlayer p:players){available(p);require(!entry.players.contains(p.getUUID())&&ArenaRules.side(e,p.getUUID())==null,"Players must be distinct and unregistered.");entry.players.add(p.getUUID());entry.banners.put(p.getUUID(),banner(p));}e.entries.put(entry.captain,entry);save();}
    Event applications(){require(active()&&data.event.phase==Phase.APPLICATIONS,"Tournament applications are closed.");return data.event;}
    public void withdraw(ServerPlayer p){if(reserved(p.getUUID())){eliminate(p,"forfeit");return;}Event e=applications();UUID side=ArenaRules.side(e,p.getUUID());require(side!=null,"You are not registered.");e.entries.remove(side);e.queue.removeIf(pair->side.equals(pair.a)||side.equals(pair.b));save();tell(p,"Application removed.");}
    public void remove(UUID player){Event e=applications();UUID side=ArenaRules.side(e,player);require(side!=null,"That player is not registered.");e.entries.remove(side);e.queue.removeIf(pair->side.equals(pair.a)||side.equals(pair.b));save();}
    public void bracket(UUID a,UUID b){Event e=applications();require(!a.equals(b)&&e.entries.containsKey(a)&&e.entries.containsKey(b),"Choose two registered team captains.");require(e.queue.stream().noneMatch(p->a.equals(p.a)||a.equals(p.b)||b.equals(p.a)||b.equals(p.b)),"A team is already in a manual pairing.");e.queue.add(new Pair(a,b));save();}
    public void start(){Event e=applications();readySetup();require(e.entries.size()>=2,"At least two complete entries are required.");List<Entry> excluded=ArenaRules.trimToPowerOfTwo(e);ArenaRules.seed(e,new Random());e.phase=Phase.RUNNING;e.invitations.clear();e.paused=false;e.nextAt=service.now();save();for(Entry entry:excluded)for(UUID id:entry.players){ServerPlayer p=service.server.getPlayerList().getPlayer(id);if(p!=null)tell(p,"Your late entry was excluded so the bracket has a power-of-two number of teams.");}announce(e.name+" • applications closed • "+e.entries.size()+" entries. Latest excess entries were excluded.");next();}
    public void duel(ServerPlayer a,ServerPlayer b){require(a!=b,"Choose two different fighters.");available(a);available(b);open("1v1 • "+a.getGameProfile().name()+" vs "+b.getGameProfile().name(),1);add(List.of(a));add(List.of(b));start();}
    private List<UUID> participants(Match match){List<UUID> result=new ArrayList<>();for(UUID captain:List.of(match.pair.a,match.pair.b))result.addAll(data.event.entries.get(captain).players);return result;}
    private void finishRound(){Event e=data.event;if(e.roundWinners.size()==1){e.champion=e.roundWinners.getFirst();e.phase=Phase.FINISHED;save();announce(e.name+" • winner: "+team(e.champion));return;}List<UUID> winners=new ArrayList<>(e.roundWinners);e.roundWinners.clear();for(int i=0;i<winners.size();i+=2)e.queue.add(new Pair(winners.get(i),winners.get(i+1)));e.round++;save();announce(e.name+" • round "+e.round);}
    public void next(){
        require(active()&&data.event.phase==Phase.RUNNING,"No running tournament.");require(data.match==null&&!loading,"A match is already running/loading.");require(data.snapshots.isEmpty(),"Wait for original inventories to finish restoring.");
        if(data.event.queue.isEmpty()){finishRound();if(!active())return;}
        Pair pair=data.event.queue.getFirst();for(UUID captain:List.of(pair.a,pair.b))for(UUID id:data.event.entries.get(captain).players)available(online(id));
        wilderness(data.setup.region);Match match=new Match();match.pair=pair;for(UUID captain:List.of(pair.a,pair.b))match.alive.addAll(data.event.entries.get(captain).players);data.match=match;data.event.paused=false;save();loading=true;
        loadArena().orTimeout(service.config.eventTeleportTimeoutSeconds,java.util.concurrent.TimeUnit.SECONDS).whenComplete((v,error)->service.server.execute(()->{if(data.match!=match||service.stopping)return;loading=false;try{if(error!=null)throw new IllegalStateException("Arena chunks did not load.",error);resetTerrain();prepare(match);}catch(Exception failure){abortMatch("Match setup failed: "+failure.getMessage());}}));
    }
    private CompletableFuture<Void> loadArena(){
        Region r=data.setup.region;ServerLevel level=level(r.dimension);ticketLevel=level;List<CompletableFuture<?>> futures=new ArrayList<>();
        for(int x=r.minX>>4;x<=r.maxX>>4;x++)for(int z=r.minZ>>4;z<=r.maxZ>>4;z++){ChunkPos pos=new ChunkPos(x,z);if(tickets.add(pos))futures.add(level.getChunkSource().addTicketAndLoadWithRadius(TICKET,pos,2));}
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }
    private void releaseTickets(){if(ticketLevel!=null)for(ChunkPos pos:tickets)ticketLevel.getChunkSource().removeTicketWithRadius(TICKET,pos,2);tickets.clear();ticketLevel=null;}
    private void prepare(Match match){
        loading=false;
        List<UUID> participants=participants(match);for(UUID id:participants)available(online(id));
        for(UUID id:participants){ServerPlayer p=online(id);p.closeContainer();Snapshot snapshot=snapshot(p);snapshot.match=match.id;data.snapshots.put(id,snapshot);}
        save(); // Every original stack is durable before any player receives a kit.
        for(UUID id:participants){ServerPlayer p=online(id);p.stopRiding();p.stopUsingItem();clear(p);for(int slot=0;slot<data.setup.kit.size();slot++)p.getInventory().setItem(slot,decode(data.setup.kit.get(slot)));Entry entry=data.event.entries.get(ArenaRules.side(data.event,id));String banner=entry.banners.getOrDefault(id,"");if(!banner.isEmpty())shield(p,decode(banner));p.removeAllEffects();p.clearFire();p.setAbsorptionAmount(0);p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(20);p.getFoodData().setSaturation(20);p.setGameMode(GameType.SURVIVAL);service.ledger.clear(id);
            Point spawn=match.pair.a.equals(entry.captain)?data.setup.spawn1:data.setup.spawn2;Point destination=new Point(spawn.dimension,spawn.x,spawn.y,spawn.z,spawn.yaw,spawn.pitch);if(data.event.teamSize==2&&entry.players.indexOf(id)==1){double angle=Math.toRadians(spawn.yaw);destination.x+=Math.cos(angle)*1.2;destination.z+=Math.sin(angle)*1.2;}require(data.setup.region.contains(destination.dimension,destination.x,destination.y,destination.z),"Team spawn offset is outside the arena.");safeSpawn(destination);require(moveNow(p,destination),"A fighter could not be moved to the arena.");savePlayer(p);p.inventoryMenu.broadcastFullState();}
        service.save();match.phase=MatchPhase.COUNTDOWN;match.startsAt=service.now()+service.config.arenaCountdownSeconds*1000L;match.endsAt=match.startsAt+service.config.arenaMaxFightSeconds*1000L;lastCountdown=-1;save();announce(data.event.name+" • "+team(match.pair.a)+" vs "+team(match.pair.b)+" • get ready.");
    }
    private void safeSpawn(Point point){ServerLevel l=level(point.dimension);BlockPos foot=BlockPos.containing(point.x,point.y,point.z);require(l.getBlockState(foot).getCollisionShape(l,foot).isEmpty()&&l.getBlockState(foot.above()).getCollisionShape(l,foot.above()).isEmpty()&&!l.getBlockState(foot.below()).getCollisionShape(l,foot.below()).isEmpty()&&l.getFluidState(foot).isEmpty(),"Spawn points need two clear blocks and solid ground.");require(!KingdomsMod.service.airDrops.protectedAt(l,foot)&&!KingdomsBridge.enteringAirDrop(online(data.match.alive.iterator().next()),l,point.x,point.y,point.z,service.now(),service.config),"Keep the arena away from active AirDrops.");}
    private Snapshot snapshot(ServerPlayer p){Snapshot s=new Snapshot();s.origin=point(p);for(int i=0;i<p.getInventory().getContainerSize();i++)s.items.add(encode(p.getInventory().getItem(i)));var ops=service.server.registryAccess().createSerializationContext(JsonOps.INSTANCE);for(var effect:p.getActiveEffects())s.effects.add(MobEffectInstance.CODEC.encodeStart(ops,effect).getOrThrow().toString());var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,p.registryAccess());p.getFoodData().addAdditionalSaveData(output);s.food=output.buildResult().toString();s.health=p.getHealth();s.absorption=p.getAbsorptionAmount();s.fire=p.getRemainingFireTicks();s.air=p.getAirSupply();s.mode=p.gameMode.getGameModeForPlayer().getName();s.xpLevel=p.experienceLevel;s.xpTotal=p.totalExperience;s.xpProgress=p.experienceProgress;s.selected=p.getInventory().getSelectedSlot();return s;}
    private void clear(ServerPlayer p){p.stopUsingItem();p.closeContainer();p.containerMenu.setCarried(ItemStack.EMPTY);for(int i=0;i<p.getInventory().getContainerSize();i++)p.getInventory().setItem(i,ItemStack.EMPTY);}
    private void shield(ServerPlayer p,ItemStack banner){ItemStack shield=p.getOffhandItem().is(Items.SHIELD)?p.getOffhandItem().copy():new ItemStack(Items.SHIELD);shield.set(DataComponents.BASE_COLOR,((BannerItem)banner.getItem()).getColor());var patterns=banner.get(DataComponents.BANNER_PATTERNS);if(patterns!=null)shield.set(DataComponents.BANNER_PATTERNS,patterns);p.setItemSlot(EquipmentSlot.OFFHAND,shield);}
    private void savePlayer(ServerPlayer p){((AirDropPlayerSaveInvoker)service.server.getPlayerList()).kingdoms$saveAirDropPlayer(p);}
    private boolean moveNow(ServerPlayer p,Point dest){boolean was=internal;internal=true;try{p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;return p.teleportTo(level(dest.dimension),dest.x,dest.y,dest.z,Set.of(),dest.yaw,dest.pitch,false);}finally{internal=was;}}
    public void restore(ServerPlayer p){
        Snapshot snapshot=data.snapshots.get(p.getUUID());if(snapshot==null||!restoring.add(p.getUUID()))return;snapshot.restoring=true;try{save();}catch(RuntimeException e){restoring.remove(p.getUUID());throw e;}
        Point origin=snapshot.origin;ServerLevel level=level(origin.dimension);int x=((int)Math.floor(origin.x))>>4,z=((int)Math.floor(origin.z))>>4;
        Runnable apply=()->{try{if(service.server.getPlayerList().getPlayer(p.getUUID())!=p||data.snapshots.get(p.getUUID())!=snapshot)return;if(!snapshot.receipt.equals(((PenaltyReceipt)p).combat$arenaReceipt())){require(moveNow(p,origin),"Return teleport was blocked.");clear(p);for(int i=0;i<snapshot.items.size();i++)p.getInventory().setItem(i,decode(snapshot.items.get(i)));p.removeAllEffects();var ops=service.server.registryAccess().createSerializationContext(JsonOps.INSTANCE);for(String effect:snapshot.effects)p.addEffect(MobEffectInstance.CODEC.parse(ops,JsonParser.parseString(effect)).getOrThrow());p.getFoodData().readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING,p.registryAccess(),TagParser.parseCompoundFully(snapshot.food)));p.setGameMode(GameType.byName(snapshot.mode));p.setRemainingFireTicks(snapshot.fire);p.setAirSupply(snapshot.air);p.setAbsorptionAmount(snapshot.absorption);p.setHealth(Math.max(1,Math.min(p.getMaxHealth(),snapshot.health)));p.experienceLevel=snapshot.xpLevel;p.totalExperience=snapshot.xpTotal;p.experienceProgress=snapshot.xpProgress;p.getInventory().setSelectedSlot(snapshot.selected);p.connection.send(new ClientboundSetExperiencePacket(p.experienceProgress,p.totalExperience,p.experienceLevel));((PenaltyReceipt)p).combat$arenaReceipt(snapshot.receipt);}
            savePlayer(p);var saved=net.minecraft.nbt.NbtIo.readCompressed(service.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getStringUUID()+".dat"),net.minecraft.nbt.NbtAccounter.unlimitedHeap());require(saved.getStringOr("kingdomscombat_arena_receipt","").equals(snapshot.receipt.toString()),"Native player save has not confirmed the inventory restore.");
            data.snapshots.remove(p.getUUID());try{save();}catch(RuntimeException e){data.snapshots.put(p.getUUID(),snapshot);throw e;}p.inventoryMenu.broadcastFullState();tell(p,"Original inventory restored.");}catch(Exception e){KingdomsCombat.LOG.error("Arena inventory recovery retained for {}",p.getUUID(),e);}finally{restoring.remove(p.getUUID());}};
        if(level.getChunkSource().getChunkNow(x,z)!=null){apply.run();return;}
        ReturnSite site=new ReturnSite(level,new ChunkPos(x,z));returnTickets.merge(site,1,Integer::sum);
        level.getChunkSource().addTicketAndLoadWithRadius(RETURN_TICKET,site.pos,2).orTimeout(service.config.eventTeleportTimeoutSeconds,java.util.concurrent.TimeUnit.SECONDS).whenComplete((chunk,error)->service.server.execute(()->{try{if(error==null&&level.getChunkSource().getChunkNow(x,z)!=null)apply.run();else{restoring.remove(p.getUUID());KingdomsCombat.LOG.error("Arena return chunks unavailable for {}; inventory backup retained",p.getUUID(),error);}}finally{Integer count=returnTickets.get(site);if(count!=null&&count>1)returnTickets.put(site,count-1);else{returnTickets.remove(site);level.getChunkSource().removeTicketWithRadius(RETURN_TICKET,site.pos,2);}}}));
    }
    public boolean defeat(ServerPlayer p,DamageSource source){if(!reserved(p.getUUID()))return false;p.setHealth(1);p.clearFire();p.invulnerableTime=20;eliminate(p,"eliminated");return true;}
    public void eliminate(ServerPlayer p,String reason){Match match=data.match;if(match==null||!match.alive.remove(p.getUUID()))return;Snapshot snapshot=data.snapshots.get(p.getUUID());if(snapshot!=null)snapshot.restoring=true;save();tell(p,"Arena • "+reason);UUID side=ArenaRules.side(data.event,p.getUUID());boolean teamAlive=data.event.entries.get(side).players.stream().anyMatch(match.alive::contains);if(!teamAlive)finish(match.pair.a.equals(side)?match.pair.b:match.pair.a);else restore(p);}
    public void finish(UUID winner){
        Match match=data.match;require(match!=null&&(winner.equals(match.pair.a)||winner.equals(match.pair.b)),"Choose a side in the current match.");
        List<UUID> participants=participants(match);UUID loser=winner.equals(match.pair.a)?match.pair.b:match.pair.a;Entry winners=data.event.entries.get(winner),losers=data.event.entries.get(loser);int delta=ArenaRules.eloDelta(average(winners),average(losers),service.config.arenaEloK,service.config.arenaEloScale);
        for(UUID id:winners.players){Rating r=rating(id);r.elo+=delta;r.wins++;}for(UUID id:losers.players){Rating r=rating(id);r.elo=Math.max(0,r.elo-delta);r.losses++;}
        data.event.queue.removeFirst();data.event.roundWinners.add(winner);data.event.nextAt=service.now()+service.config.arenaBetweenMatchesSeconds*1000L;data.match=null;for(UUID id:participants){Snapshot s=data.snapshots.get(id);if(s!=null)s.restoring=true;service.ledger.clear(id);}save();service.save();announce(team(winner)+" wins • +"+delta+" Elo • "+team(loser)+" −"+delta+" Elo");
        resetTerrain();cleanupEntities();releaseTickets();for(UUID id:participants){var p=service.server.getPlayerList().getPlayer(id);if(p!=null){title(p,winners.players.contains(id)?"Victory":"Defeat","Elo "+(winners.players.contains(id)?"+":"−")+delta);restore(p);}}if(data.event.queue.isEmpty())finishRound();
    }
    private double average(Entry entry){return entry.players.stream().mapToInt(id->rating(id).elo).average().orElse(service.config.arenaEloStart);}
    public Rating rating(UUID id){return data.ratings.computeIfAbsent(id,k->new Rating(service.config.arenaEloStart));}
    public void abortMatch(String reason){if(data.match==null)return;loading=false;List<UUID> participants=participants(data.match);data.match=null;data.event.paused=true;for(UUID id:participants){Snapshot s=data.snapshots.get(id);if(s!=null)s.restoring=true;service.ledger.clear(id);}save();resetTerrain();cleanupEntities();releaseTickets();for(UUID id:participants){ServerPlayer p=service.server.getPlayerList().getPlayer(id);if(p!=null)restore(p);}announce(reason+" • match paused without an Elo result. Host can use tournament next to replay.");}
    public void cancel(){require(active(),"No active tournament.");if(data.match!=null)abortMatch("Event cancelled");data.event.phase=Phase.CANCELLED;save();bar.removeAllPlayers();announce(data.event.name+" was stopped.");}
    public boolean disconnect(ServerPlayer p){boolean reserved=reserved(p.getUUID());if(reserved&&!service.stopping){if(data.match!=null&&data.match.phase!=MatchPhase.PREPARING){UUID side=ArenaRules.side(data.event,p.getUUID());finish(side.equals(data.match.pair.a)?data.match.pair.b:data.match.pair.a);}else if(data.match!=null)abortMatch("A fighter disconnected during setup");}return reserved;}
    public void join(ServerPlayer p){if(reserved(p.getUUID()))restore(p);}
    public void stop(){if(data.match!=null)abortMatch("Server restart");for(ServerPlayer p:service.server.getPlayerList().getPlayers())if(reserved(p.getUUID()))restore(p);bar.removeAllPlayers();releaseTickets();for(ReturnSite site:new ArrayList<>(returnTickets.keySet()))site.level.getChunkSource().removeTicketWithRadius(RETURN_TICKET,site.pos,2);returnTickets.clear();save();}
    void resetTerrain(){
        if(data.setup.region==null)return;ServerLevel level=level(data.setup.region.dimension);boolean previous=internal;internal=true;
        try{Iterator<Map.Entry<String,String>> iterator=data.changedBlocks.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();String[] xyz=entry.getKey().split(",");BlockPos pos=new BlockPos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]));if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)continue;require(level.setBlock(pos,state(entry.getValue()),3)||level.getBlockState(pos).equals(state(entry.getValue())),"An arena block could not be restored.");iterator.remove();}
            for(Gate gate:data.setup.gates){BlockPos pos=new BlockPos(gate.x,gate.y,gate.z);if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null)level.setBlock(pos,state(gate.state),3);}
            save();
        }finally{internal=previous;}
    }
    private void gatesOpen(){boolean previous=internal;internal=true;try{ServerLevel level=level(data.setup.region.dimension);for(Gate g:data.setup.gates){BlockPos p=new BlockPos(g.x,g.y,g.z);String key=g.x+","+g.y+","+g.z;data.changedBlocks.putIfAbsent(key,g.state);}save();for(Gate g:data.setup.gates)level.setBlock(new BlockPos(g.x,g.y,g.z),Blocks.AIR.defaultBlockState(),3);}finally{internal=previous;}}
    public void tick(){
        ticks++;if(service.stopping)return;
        if(failed){if(ticks%20==0)save();return;}
        if(ticks%20==0){for(ServerPlayer p:service.server.getPlayerList().getPlayers()){Snapshot snapshot=data.snapshots.get(p.getUUID());if(snapshot!=null&&snapshot.restoring)restore(p);}if(data.match==null&&!data.changedBlocks.isEmpty())resetTerrain();}
        Match match=data.match;
        if(match!=null){
            for(UUID id:new ArrayList<>(match.alive)){ServerPlayer p=service.server.getPlayerList().getPlayer(id);if(p==null){if(match.phase!=MatchPhase.PREPARING){UUID side=ArenaRules.side(data.event,id);finish(side.equals(match.pair.a)?match.pair.b:match.pair.a);}break;}if(!inside(p.level(),p.blockPosition())&&match.phase==MatchPhase.FIGHTING){eliminate(p,"left the arena");break;}if(match.phase==MatchPhase.COUNTDOWN){p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;}}
            if(data.match!=match)return;
            if(match.phase==MatchPhase.COUNTDOWN){int seconds=(int)Math.max(0,(match.startsAt-service.now()+999)/1000);if(seconds!=lastCountdown&&(seconds<=5||seconds==10||seconds==service.config.arenaCountdownSeconds)){lastCountdown=seconds;for(UUID id:match.alive){ServerPlayer p=online(id);title(p,seconds==0?"Fight!":Integer.toString(seconds),team(match.pair.a)+" vs "+team(match.pair.b));service.sound(p,seconds==0?"minecraft:entity.ender_dragon.growl":"minecraft:block.note_block.pling");}}if(service.now()>=match.startsAt){gatesOpen();match.phase=MatchPhase.FIGHTING;save();announce("Fight started • "+team(match.pair.a)+" vs "+team(match.pair.b));}}
            if(match.phase==MatchPhase.FIGHTING&&service.now()>=match.endsAt)abortMatch("Fight time limit reached");
        }else if(ticks%20==0&&active()&&data.event.phase==Phase.RUNNING&&!data.event.paused&&service.config.arenaAutoNext&&data.snapshots.isEmpty()&&service.now()>=data.event.nextAt){try{next();}catch(IllegalArgumentException e){data.event.paused=true;save();announce("Tournament paused • "+e.getMessage()+" Host can use tournament next when ready.");}}
        if(ticks%20==0)hud();
    }
    private void hud(){if(!active()){bar.removeAllPlayers();return;}String status=data.event.phase==Phase.APPLICATIONS?"Applications • "+data.event.entries.size()+" teams":data.match==null?"Waiting for next match":data.match.phase==MatchPhase.COUNTDOWN?"Starts in "+Math.max(0,(data.match.startsAt-service.now()+999)/1000)+"s":team(data.match.pair.a)+" vs "+team(data.match.pair.b);bar.setName(CombatService.text(data.event.name+" • "+status,"gold"));bar.setProgress(data.match!=null&&data.match.phase==MatchPhase.FIGHTING?(float)Math.max(0,Math.min(1,(data.match.endsAt-service.now())/(service.config.arenaMaxFightSeconds*1000d))):1);Set<ServerPlayer> online=new HashSet<>(service.server.getPlayerList().getPlayers());for(ServerPlayer old:new ArrayList<>(bar.getPlayers()))if(!online.contains(old))bar.removePlayer(old);for(ServerPlayer p:online)if(!bar.getPlayers().contains(p))bar.addPlayer(p);}
    public boolean allowTeleport(ServerPlayer p,ServerLevel target,double x,double y,double z){if(internal)return true;if(reserved(p.getUUID()))return fighter(p.getUUID())&&inside(p.level(),p.blockPosition())&&inside(target,x,y,z);return !inside(target,x,y,z)||admin(p)||!active();}
    public boolean allowCommand(ServerPlayer p,String command){return !reserved(p.getUUID())||CommandRules.matches(command,service.config.arenaAllowedCommands);}
    public boolean freezeMovement(ServerPlayer p){return reserved(p.getUUID())&&(data.snapshots.get(p.getUUID()).restoring||data.match==null||data.match.phase!=MatchPhase.FIGHTING);}
    public void menu(ServerPlayer p){ArenaUi.player(this,p);}
    String team(UUID captain){Entry e=data.event==null?null:data.event.entries.get(captain);return e==null?"None":String.join(" + ",e.players.stream().map(service.staff::name).toList());}
    String encode(ItemStack stack){return stack.isEmpty()?"":AirDropLoot.encode(service.server,stack);}
    ItemStack decode(String json){return json.isEmpty()?ItemStack.EMPTY:AirDropLoot.decode(service.server,json);}
    private BlockState state(String json){return BlockState.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(json)).getOrThrow();}
    private ServerPlayer online(UUID id){ServerPlayer p=service.server.getPlayerList().getPlayer(id);require(p!=null,"A selected fighter is offline: "+service.staff.name(id));return p;}
    static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
    void announce(String message){if(!service.config.eventChat)return;service.server.getPlayerList().broadcastSystemMessage(CombatService.text("[arena] "+message,"gold"),false);}
    static void tell(ServerPlayer p,String message){p.sendSystemMessage(CombatService.text(message,"aqua"));}
    private void title(ServerPlayer p,String title,String subtitle){if(!service.config.eventTitles)return;p.connection.send(new ClientboundSetTitlesAnimationPacket(0,20,5));p.connection.send(new ClientboundSetTitleTextPacket(CombatService.text(title,"gold")));p.connection.send(new ClientboundSetSubtitleTextPacket(CombatService.text(subtitle,"gray")));}
}
