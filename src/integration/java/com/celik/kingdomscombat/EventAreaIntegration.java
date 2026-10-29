package com.celik.kingdomscombat;

import com.celik.kingdoms.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.*;
import java.util.*;
import java.util.function.BiConsumer;
import static com.celik.kingdomscombat.EventAreaRules.*;

final class EventAreaIntegration {
    static void run(CombatService s,BiConsumer<Boolean,String> check)throws Exception{
        var areas=s.areas;var level=s.server.overworld();int x=24500,z=24500;level.getChunk(x>>4,z>>4);
        var admin=CombatIntegration.player(s.server,level,"AreaAdmin",x+1.5,161,z+1.5);var guest=CombatIntegration.player(s.server,level,"AreaGuest",x+2.5,161,z+1.5);var rival=CombatIntegration.player(s.server,level,"AreaRival",x+3.5,161,z+1.5);
        var originalIds=new HashSet<>(areas.data.areas.keySet());var savedRegion=s.arenas.data.setup.region;var dispatcher=s.server.getCommands().getDispatcher();
        try{
            s.server.getPlayerList().op(new NameAndId(admin.getUUID(),admin.getGameProfile().name()),Optional.of(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER),Optional.empty());s.toggleStaffMode(admin);
            for(int dx=0;dx<12;dx++)for(int dz=0;dz<4;dz++){level.setBlock(new BlockPos(x+dx,160,z+dz),Blocks.STONE.defaultBlockState(),3);for(int y=161;y<=164;y++)level.setBlock(new BlockPos(x+dx,y,z+dz),Blocks.AIR.defaultBlockState(),3);}
            check.accept(dispatcher.execute("eventhost area pos1 "+x+" 160 "+z,admin.createCommandSourceStack())==1&&dispatcher.execute("eventhost area pos2 "+(x+3)+" 164 "+(z+3),admin.createCommandSourceStack())==1&&dispatcher.execute("eventhost area claim Courtyard",admin.createCommandSourceStack())==1,"admin commands select exact two corners and claim a persistent event cuboid");
            Area first=areas.at(level,admin.blockPosition());check.accept(first!=null&&first.name.equals("Courtyard")&&areas.at(level,new BlockPos(x+4,161,z))==null&&areas.at(level,new BlockPos(x,165,z))==null,"event area protects only the inclusive cuboid, not the rest of its chunk or column");
            areas.corner(admin,1,new BlockPos(x+8,160,z));areas.corner(admin,2,new BlockPos(x+11,164,z+3));areas.claim(admin,"Market square");Area second=areas.at(level,new BlockPos(x+9,161,z+1));
            check.accept(second!=null&&!first.id.equals(second.id)&&(first.region.minX>>4)==(second.region.minX>>4),"two independent event areas can occupy separate blocks of the same chunk");
            check.accept(dispatcher.execute("eventhost area",admin.createCommandSourceStack())==1&&admin.containerMenu instanceof Ui.Menu&&admin.containerMenu.getSlot(4).getItem().getHoverName().getString().contains(Ui.caps("Courtyard")),"standing inside an area opens that area's own flag menu");
            admin.containerMenu.clicked(12,0,ContainerInput.PICKUP,admin);check.accept(!areas.at(level,admin.blockPosition()).allows(Flag.PVP)&&areas.data.areas.get(second.id).allows(Flag.PVP),"clicking a flag changes only the selected event area");
            check.accept(!Protection.damage(guest,rival.damageSources().playerAttack(rival)),"area PvP flag blocks player damage through the native Kingdoms protection hook");areas.flag(admin,first.id,Flag.PVP);check.accept(Protection.damage(guest,rival.damageSources().playerAttack(rival)),"area PvP can be re-enabled independently from build protection");
            BlockPos floor=new BlockPos(x+1,160,z+1),air=floor.above(),outside=new BlockPos(x+4,161,z+1);
            check.accept(!guest.gameMode.destroyBlock(floor)&&level.getBlockState(floor).is(Blocks.STONE),"native block breaking is blocked inside a default event area");
            try(var scope=Protection.scope(level,air,guest.getUUID(),guest,Protection.Cause.PLAYER)){check.accept(!level.setBlock(air,Blocks.COBBLESTONE.defaultBlockState(),3),"native placement cannot bypass the area's placing flag");}
            check.accept(Protection.permitted(level,air,guest.getUUID(),Rules.Permission.INTERACT)&&Protection.container(level,air,guest.getUUID()),"default event-area interactions and containers remain allowed");
            areas.flag(admin,first.id,Flag.PLACE);try(var scope=Protection.scope(level,air,guest.getUUID(),guest,Protection.Cause.PLAYER)){check.accept(level.setBlock(air,Blocks.COBBLESTONE.defaultBlockState(),3),"enabling placement allows ordinary native block placement in that area");}areas.flag(admin,first.id,Flag.PLACE);
            try(var scope=Protection.scope(level,air,admin.getUUID(),admin,Protection.Cause.PLAYER)){check.accept(level.setBlock(air,Blocks.AIR.defaultBlockState(),3),"authorized staff can edit an area without globally enabling building");}
            check.accept(!Protection.explosion(level,air,floor,null)&&Protection.explosion(level,outside,outside,null),"explosion terrain protection is local to the event area and leaves adjacent wilderness unchanged");
            areas.flag(admin,first.id,Flag.EXPLOSIONS);check.accept(Protection.explosion(level,air,floor,null),"each area's explosion block-damage flag can be enabled");areas.flag(admin,first.id,Flag.EXPLOSIONS);
            check.accept(Protection.damage(guest,guest.damageSources().explosion(null,null)),"blocking explosion block damage does not make players immune to unowned explosions");
            check.accept(Protection.fluid(level,air,air.north())&&!Protection.fluid(level,air,outside)&&!Protection.fluid(level,outside,air),"fluids can flow inside an enabled area but cannot cross its boundary in either direction");areas.flag(admin,first.id,Flag.FLUIDS);check.accept(!Protection.fluid(level,air,air.north()),"disabling fluid flow stops internal flow for that area");
            check.accept(!Protection.piston(level,air,air.north()),"piston motion is blocked by the default per-area flag");areas.flag(admin,first.id,Flag.PISTONS);check.accept(Protection.piston(level,air,air.north())&&!Protection.piston(level,outside,air),"enabled piston motion stays inside the selected event area");
            check.accept(!Protection.flag(level,air,Rules.Flag.FIRE)&&!Protection.flag(level,air,Rules.Flag.MOB_GRIEFING),"default fire and mob-griefing flags reach the native protection decisions");
            areas.flag(admin,first.id,Flag.CONTAINERS);check.accept(!Protection.container(level,air,guest.getUUID())&&!Protection.automation(level,air,air.north()),"container flags also prevent automated item extraction inside the area");
            BlockPos button=new BlockPos(x+2,161,z+2);try(var scope=Protection.scope(level,button,admin.getUUID(),admin,Protection.Cause.PLAYER)){level.setBlock(button,Blocks.STONE_BUTTON.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE,net.minecraft.world.level.block.state.properties.AttachFace.FLOOR).setValue(ButtonBlock.POWERED,true),3);}try(var scope=Protection.scope(level,button,null,null,Protection.Cause.BLOCK)){level.getBlockState(button).tick(level,button,net.minecraft.util.RandomSource.create(1));}check.accept(!level.getBlockState(button).getValue(ButtonBlock.POWERED),"normal scheduled button release is not frozen by event-area build protection");
            var loaded=new EventAreas(s);check.accept(loaded.at(level,air)!=null&&!loaded.at(level,air).allows(Flag.CONTAINERS)&&loaded.at(level,new BlockPos(x+9,161,z+1)).allows(Flag.CONTAINERS),"restart loading preserves independent area flags and bounds without an expiration timer");
            boolean overlap=false;try{areas.corner(admin,1,floor);areas.corner(admin,2,air);areas.claim(admin,"Overlap");}catch(IllegalArgumentException expected){overlap=true;}check.accept(overlap,"overlapping cuboids are rejected so the in-place settings menu is unambiguous");
            boolean denied=false;try{areas.menu(guest);}catch(IllegalArgumentException expected){denied=true;}check.accept(denied,"ordinary players cannot configure event-area flags");
            boolean kingdom=false;try{KingdomsMod.service.create(guest,"AreaClaimAttempt");}catch(IllegalArgumentException expected){kingdom=expected.getMessage().contains("event area");}check.accept(kingdom,"kingdom chunk claiming cannot take land that intersects an event cuboid");
            var arenaRegion=new ArenaRules.Region();arenaRegion.dimension=first.region.dimension;arenaRegion.minX=x;arenaRegion.maxX=x+3;arenaRegion.minY=160;arenaRegion.maxY=164;arenaRegion.minZ=z;arenaRegion.maxZ=z+3;s.arenas.data.setup.region=arenaRegion;
            check.accept(areas.permission(level,air,guest.getUUID(),Rules.Permission.PLACE)&&areas.allows(level,air,Flag.EXPLOSIONS),"tournament arena territory defers to existing arena protection instead of inheriting generic area restrictions");s.arenas.data.setup.region=savedRegion;
            areas.menu(admin);admin.containerMenu.clicked(31,0,ContainerInput.PICKUP,admin);admin.containerMenu.clicked(29,0,ContainerInput.PICKUP,admin);check.accept(areas.at(level,air)==null&&areas.data.areas.containsKey(second.id),"GUI unclaim removes only the selected area and leaves every other area's flags intact");
        }finally{s.arenas.data.setup.region=savedRegion;for(UUID id:new ArrayList<>(areas.data.areas.keySet()))if(!originalIds.contains(id))areas.unclaim(admin,id);if(s.staffMode(admin))s.toggleStaffMode(admin);s.server.getPlayerList().deop(new NameAndId(admin.getUUID(),admin.getGameProfile().name()));for(ServerPlayer p:List.of(admin,guest,rival))CombatIntegration.remove(s.server,p);}
    }
    private EventAreaIntegration(){}
}
