package com.celik.kingdomscombat;

import com.celik.kingdoms.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.nio.file.Path;
import java.util.*;
import static com.celik.kingdomscombat.EventAreaRules.*;

/** Permanent, independent cuboids. This does not add kingdoms or additional tournament arenas. */
public final class EventAreas {
    record Selection(String dimension,BlockPos first,BlockPos second){}
    final CombatService service;final Path file;Data data;private Index index;
    private final Map<UUID,Selection> selections=new HashMap<>();
    EventAreas(CombatService service){this.service=service;file=service.stateFile.resolveSibling("event-areas.json");data=CombatStore.read(file,Data.class,new Data());EventAreaRules.validate(data);index=new Index(data.areas.values());}
    private Data copy(){return CombatStore.JSON.fromJson(CombatStore.JSON.toJson(data),Data.class);}
    private void store(Data next){EventAreaRules.validate(next);CombatStore.write(file,next);data=next;index=new Index(next.areas.values());}
    void require(ServerPlayer p){ArenaCommands.require(p.createCommandSourceStack(),true);}
    boolean admin(UUID actor){ServerPlayer p=actor==null?null:service.server.getPlayerList().getPlayer(actor);return p!=null&&service.arenas.admin(p);}
    public Area at(Level level,BlockPos pos){return !service.config.eventAreasEnabled?null:index.at(level.dimension().identifier().toString(),pos.getX(),pos.getY(),pos.getZ());}
    public boolean overlapsChunk(Level level,int x,int z){return service.config.eventAreasEnabled&&index.chunk(level.dimension().identifier().toString(),x,z);}
    public boolean arena(Level level,BlockPos pos){return service.arenas!=null&&service.arenas.inside(level,pos);}
    public boolean allows(Level level,BlockPos pos,Flag flag){if(arena(level,pos))return true;Area area=at(level,pos);return area==null||area.allows(flag);}
    public boolean permission(Level level,BlockPos pos,UUID actor,Rules.Permission permission){
        if(arena(level,pos))return true;Area a=at(level,pos);if(a==null||admin(actor))return true;
        return switch(permission){case BREAK,ENTITIES->a.allows(Flag.BREAK);case PLACE->a.allows(Flag.PLACE);case INTERACT->a.allows(Flag.INTERACT);case CONTAINERS->a.allows(Flag.CONTAINERS);case PVP->a.allows(Flag.PVP);default->true;};
    }
    public boolean interact(ServerPlayer player,BlockPos pos){var state=player.level().getBlockState(pos);return permission(player.level(),pos,player.getUUID(),state.getMenuProvider(player.level(),pos)!=null?Rules.Permission.CONTAINERS:Rules.Permission.INTERACT);}
    public boolean edge(Level level,BlockPos from,BlockPos to,Flag flag){
        if(arena(level,from)||arena(level,to))return true;
        Area a=at(level,from),b=at(level,to);if(a==null&&b==null)return true;
        return a!=null&&b!=null&&a.id.equals(b.id)&&a.allows(flag);
    }
    public boolean mutation(Level level,BlockPos pos,BlockState next){
        if(arena(level,pos))return true;BlockState old=level.getBlockState(pos);if(old.equals(next))return true;
        Area area=at(level,pos);var c=Protection.current();
        if(c==null||c.level()!=level)return area==null||old.getBlock()==next.getBlock();
        if(next.is(BlockTags.FIRE)&&!allows(level,pos,Flag.FIRE))return false;
        return switch(c.cause()){
            case PLAYER->permission(level,pos,c.actor(),next.isAir()?Rules.Permission.BREAK:old.getBlock()==next.getBlock()?Rules.Permission.INTERACT:Rules.Permission.PLACE);
            case EXPLOSION->allows(level,pos,Flag.EXPLOSIONS);
            case FLUID->edge(level,c.origin(),pos,Flag.FLUIDS);
            case FIRE->allows(level,c.origin(),Flag.FIRE)&&allows(level,pos,Flag.FIRE);
            case PISTON->edge(level,c.origin(),pos,Flag.PISTONS);
            case ENTITY->allows(level,pos,Flag.MOB_GRIEFING);
            case AUTOMATION->!old.getFluidState().isEmpty()||!next.getFluidState().isEmpty()?edge(level,c.origin(),pos,Flag.FLUIDS):area==null||old.getBlock()==next.getBlock();
            case BLOCK->!old.getFluidState().isEmpty()||!next.getFluidState().isEmpty()?edge(level,c.origin(),pos,Flag.FLUIDS):true;
        };
    }
    public boolean damage(Entity target,DamageSource source){
        if(arena(target.level(),target.blockPosition()))return true;
        UUID actor=Protection.owner(source.getEntity());if(actor==null)actor=Protection.owner(source.getDirectEntity());
        if(target instanceof ServerPlayer){
            if(actor==null||actor.equals(target.getUUID()))return true;
            if(!allows(target.level(),target.blockPosition(),Flag.PVP))return false;
            ServerPlayer attacker=service.server.getPlayerList().getPlayer(actor);return attacker==null||allows(attacker.level(),attacker.blockPosition(),Flag.PVP);
        }
        if((!(target instanceof LivingEntity)||target instanceof net.minecraft.world.entity.decoration.ArmorStand)&&actor!=null)return permission(target.level(),target.blockPosition(),actor,Rules.Permission.ENTITIES);
        return true;
    }
    public boolean admit(Entity entity){
        var c=Protection.current();if(c==null||c.cause()!=Protection.Cause.PLAYER||arena(entity.level(),entity.blockPosition()))return true;
        if(entity instanceof Mob||entity instanceof net.minecraft.world.entity.vehicle.VehicleEntity||entity instanceof net.minecraft.world.entity.decoration.HangingEntity||entity instanceof net.minecraft.world.entity.decoration.ArmorStand)return permission(entity.level(),entity.blockPosition(),c.actor(),Rules.Permission.PLACE);
        return true;
    }
    public void corner(ServerPlayer p,int number,BlockPos pos){
        require(p);if(pos.getY()<p.level().getMinY()||pos.getY()>=p.level().getMaxY()||Math.abs((long)pos.getX())>30000000||Math.abs((long)pos.getZ())>30000000)throw new IllegalArgumentException("Choose a corner inside world bounds.");
        String dimension=p.level().dimension().identifier().toString();Selection old=selections.get(p.getUUID());if(old!=null&&!old.dimension.equals(dimension))old=null;
        Selection next=new Selection(dimension,number==1?pos.immutable():old==null?null:old.first,number==2?pos.immutable():old==null?null:old.second);selections.put(p.getUUID(),next);
        p.sendSystemMessage(CombatService.text("Corner "+number+": "+pos.toShortString()+" • "+dimension,"aqua"));
    }
    public void claim(ServerPlayer p,String suppliedName){
        require(p);Selection s=selections.get(p.getUUID());if(s==null||s.first==null||s.second==null)throw new IllegalArgumentException("Select both corners first.");
        String name=suppliedName==null?"Event area "+UUID.randomUUID().toString().substring(0,8):suppliedName.trim();
        if(name.isBlank()||name.length()>64||name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Use an area name of 1–64 characters.");
        var r=new ArenaRules.Region();r.dimension=s.dimension;r.minX=Math.min(s.first.getX(),s.second.getX());r.maxX=Math.max(s.first.getX(),s.second.getX());r.minY=Math.min(s.first.getY(),s.second.getY());r.maxY=Math.max(s.first.getY(),s.second.getY());r.minZ=Math.min(s.first.getZ(),s.second.getZ());r.maxZ=Math.max(s.first.getZ(),s.second.getZ());
        if(data.areas.values().stream().anyMatch(a->EventAreaRules.intersects(a.region,r)))throw new IllegalArgumentException("This selection overlaps another event area. Each place has one independent flag menu.");
        for(var kingdom:KingdomsMod.service.state.kingdoms.values())for(String claim:kingdom.claims){String[] part=claim.split("\\|");if(part.length==3&&part[0].equals(r.dimension)){int x=Integer.parseInt(part[1]),z=Integer.parseInt(part[2]);if(x>=(r.minX>>4)&&x<=(r.maxX>>4)&&z>=(r.minZ>>4)&&z<=(r.maxZ>>4))throw new IllegalArgumentException("Event areas must not overlap kingdom claims.");}}
        Data next=copy();Area area=new Area();area.name=name;area.region=r;next.areas.put(area.id,area);store(next);EventAreaUi.settings(this,p,area.id);
    }
    public void flag(ServerPlayer p,UUID id,Flag flag){require(p);Data next=copy();Area a=next.areas.get(id);if(a==null)throw new IllegalArgumentException("This event area no longer exists.");a.flags.put(flag,!a.allows(flag));store(next);}
    public void unclaim(ServerPlayer p,UUID id){require(p);Data next=copy();if(next.areas.remove(id)==null)throw new IllegalArgumentException("This event area no longer exists.");store(next);}
    public void menu(ServerPlayer p){require(p);Area area=at(p.level(),p.blockPosition());if(area!=null)EventAreaUi.settings(this,p,area.id);else EventAreaUi.selection(this,p);}
    Selection selection(UUID player){return selections.get(player);}
    public void leave(UUID player){selections.remove(player);}
}
