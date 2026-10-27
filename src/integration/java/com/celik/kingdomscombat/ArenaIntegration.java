package com.celik.kingdomscombat;

import com.celik.kingdoms.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import java.util.function.BiConsumer;
import static com.celik.kingdomscombat.ArenaRules.*;

final class ArenaIntegration {
    static void run(CombatService service,long[] time,BiConsumer<Boolean,String> check)throws Exception{
        ArenaService arena=service.arenas;var level=service.server.overworld();var cfg=service.config;boolean auto=cfg.arenaAutoNext;cfg.arenaAutoNext=false;
        var saved=CombatStore.JSON.fromJson(CombatStore.JSON.toJson(arena.data),Data.class);arena.data=new Data();arena.save();
        List<ServerPlayer> players=new ArrayList<>();CombatService replacement=null;
        try{
            int x=14000,z=14000;level.getChunk(x>>4,z>>4);
            for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++){level.setBlock(new BlockPos(x+dx,160,z+dz),Blocks.STONE.defaultBlockState(),3);for(int y=161;y<165;y++)level.setBlock(new BlockPos(x+dx,y,z+dz),Blocks.AIR.defaultBlockState(),3);}
            ServerPlayer a=CombatIntegration.player(service.server,level,"ArenaAlpha",x+2.5,161,z+2.5),b=CombatIntegration.player(service.server,level,"ArenaBeta",x+12.5,161,z+12.5),c=CombatIntegration.player(service.server,level,"ArenaGamma",x+3.5,161,z+3.5),d=CombatIntegration.player(service.server,level,"ArenaDelta",x+11.5,161,z+11.5),late=CombatIntegration.player(service.server,level,"ArenaLate",x+5.5,161,z+5.5);players.addAll(List.of(a,b,c,d,late));
            a.getInventory().setItem(0,new ItemStack(Items.DIAMOND_SWORD));a.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.SHIELD));a.getInventory().setItem(8,new ItemStack(Items.COBBLESTONE,16));arena.kit(a);
            for(ServerPlayer p:players){p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Items.BANNER.blue()));p.getInventory().setItem(9,new ItemStack(Items.DIAMOND,7));p.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));p.setHealth(16);p.experienceLevel=12;p.totalExperience=50;p.experienceProgress=.25f;p.addEffect(new MobEffectInstance(MobEffects.SPEED,1200,1));}
            a.setPos(x,160,z);arena.corner(a,1);a.setPos(x+15,164,z+15);arena.corner(a,2);arena.claim();a.setPos(x+2.5,161,z+2.5);arena.spawn(a,1);b.setPos(x+12.5,161,z+12.5);arena.spawn(b,2);
            Gate gate=new Gate();gate.x=x+8;gate.y=161;gate.z=z+8;gate.state=net.minecraft.world.level.block.state.BlockState.CODEC.encodeStart(JsonOps.INSTANCE,Blocks.IRON_BARS.defaultBlockState()).getOrThrow().toString();arena.data.setup.gates.add(gate);arena.resetTerrain();
            check.accept(arena.overlapsChunk(level,x>>4,z>>4),"arena claim reserves its intersecting wilderness chunks");
            boolean claimRejected=false;try{KingdomsMod.service.create(a,"ArenaShouldNotClaim");}catch(IllegalArgumentException expected){claimRejected=expected.getMessage().contains("arena");}check.accept(claimRejected,"kingdom creation cannot overlap an event arena");
            arena.open("Integration tournament",1);b.getInventory().setItem(0,new ItemStack(Items.STONE));for(ServerPlayer p:players)arena.apply(p);check.accept(arena.data.event.entries.get(b.getUUID()).banners.get(b.getUUID()).isEmpty(),"solo applications without a held banner are accepted");arena.bracket(a.getUUID(),b.getUUID());arena.bracket(c.getUUID(),d.getUUID());arena.start();prepareLoaded(arena);
            check.accept(arena.data.event.entries.size()==4&&!arena.data.event.entries.containsKey(late.getUUID()),"five applications retain the first four and exclude the most recent");
            check.accept(arena.data.match.phase==MatchPhase.COUNTDOWN&&arena.reserved(a.getUUID())&&!arena.reserved(c.getUUID()),"only current fighters receive kits and inventory backups");
            check.accept(!arena.build(c,level,new BlockPos(x+4,161,z+4))&&!arena.fighter(a.getUUID()),"registered waiting players cannot build and countdown fighters cannot build early");
            check.accept(a.getInventory().getItem(0).is(Items.DIAMOND_SWORD)&&a.getOffhandItem().is(Items.SHIELD)&&a.getOffhandItem().get(DataComponents.BASE_COLOR)==DyeColor.BLUE,"saved kit is applied and banner color appears on its shield");
            check.accept(ItemStack.isSameItemSameComponents(b.getOffhandItem(),new ItemStack(Items.SHIELD)),"no-banner applicants retain the unmodified admin kit shield");
            check.accept(!Protection.damage(b,a.damageSources().playerAttack(a)),"countdown protection blocks PvP");
            check.accept(!service.allowTeleport(a,false)&&!service.allowDestination(c,level,x+8,161,z+8,Set.of()),"fighters cannot teleport away and waiting players cannot teleport into the arena");
            check.accept(a.drop(new ItemStack(Items.DIAMOND_SWORD),false,false)==null,"current fighters cannot drop kit items through the native drop method");
            check.accept(level.getBlockState(new BlockPos(gate.x,gate.y,gate.z)).is(Blocks.IRON_BARS),"saved gate stays closed during countdown");
            var snapshot=arena.data.snapshots.get(a.getUUID());check.accept(CombatStore.read(arena.file,Data.class,null).snapshots.get(a.getUUID()).items.get(9).contains("diamond"),"original item components are saved on disk before kit use");
            VoicePolicy.refresh(service);check.accept(!VoicePolicy.muted(a.getUUID())&&VoicePolicy.muted(c.getUUID()),"voice allows current match players while waiting applicants inside the arena are restricted");
            time[0]+=cfg.arenaCountdownSeconds*1000L;arena.tick();
            check.accept(arena.fighter(a.getUUID())&&arena.fighter(b.getUUID())&&!arena.fighter(c.getUUID())&&level.getBlockState(new BlockPos(gate.x,gate.y,gate.z)).isAir(),"fight start opens gates and enables only its current opponents");
            check.accept(Protection.damage(b,a.damageSources().playerAttack(a))&&!Protection.damage(b,c.damageSources().playerAttack(c)),"native Kingdoms PvP decision admits current opponents and refuses registered outsiders");
            var kitArrow=new net.minecraft.world.entity.projectile.arrow.Arrow(net.minecraft.world.entity.EntityTypes.ARROW,level);kitArrow.setOwner(a);check.accept(arena.admit(kitArrow)&&kitArrow.pickup==net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.DISALLOWED,"kit arrows cannot be collected or exported after leaving the arena");
            BlockPos changed=new BlockPos(x+4,161,z+4);try(var scope=Protection.scope(level,changed,c.getUUID(),c,Protection.Cause.PLAYER)){check.accept(!level.setBlock(changed,Blocks.COBBLESTONE.defaultBlockState(),3),"native block placement by waiting applicant is refused");}
            try(var scope=Protection.scope(level,changed,a.getUUID(),a,Protection.Cause.PLAYER)){check.accept(level.setBlock(changed,Blocks.COBBLESTONE.defaultBlockState(),3),"current fighter can place blocks within the arena");}
            check.accept(!Protection.explosion(level,changed,changed,a.getUUID()),"arena prevents explosion block drops and terrain changes before the native callback");
            b.invulnerableTime=0;b.hurtServer(level,a.damageSources().playerAttack(a),1000);
            check.accept(b.isAlive()&&!arena.reserved(b.getUUID())&&arena.data.match==null,"native lethal damage eliminates the loser without a death or item drops");
            check.accept(a.getInventory().getItem(9).is(Items.DIAMOND)&&a.getInventory().getItem(9).getCount()==7&&a.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS)&&a.getHealth()==16&&a.experienceLevel==12&&a.getEffect(MobEffects.SPEED).getAmplifier()==1,"match completion restores original inventory, armor, health, XP and effects");
            kitArrow.baseTick();check.accept(kitArrow.isRemoved(),"temporary kit projectiles expire after their match even outside the cleanup radius");
            check.accept(level.getBlockState(changed).isAir()&&level.getBlockState(new BlockPos(gate.x,gate.y,gate.z)).is(Blocks.IRON_BARS),"arena terrain and gates restore between matches");
            check.accept(arena.rating(a.getUUID()).elo==1016&&arena.rating(b.getUUID()).elo==984&&arena.rating(a.getUUID()).wins==1,"one real match updates the shared individual Elo exactly once");
            arena.data.snapshots.put(a.getUUID(),snapshot);snapshot.restoring=true;arena.save();a.getInventory().setItem(9,new ItemStack(Items.EMERALD,5));arena.restore(a);
            check.accept(a.getInventory().getItem(9).is(Items.EMERALD)&&arena.data.snapshots.isEmpty(),"durable player receipt prevents replaying an already restored inventory");
            arena.next();prepareLoaded(arena);time[0]+=cfg.arenaCountdownSeconds*1000L;arena.tick();
            c.connection.onDisconnect(new DisconnectionDetails(Component.literal("Arena forfeit fixture")));
            check.accept(c.getHealth()>0&&c.getInventory().getItem(9).is(Items.DIAMOND)&&arena.rating(d.getUUID()).wins==1&&!service.ledger.data.logoutPenalties.containsKey(c.getUUID()),"native disconnect forfeits immediately, restores original items and never triggers ordinary combat-log drops");
            arena.next();prepareLoaded(arena);check.accept(arena.reserved(a.getUUID())&&arena.reserved(d.getUUID()),"final round contains only semifinal winners");
            var release=ArenaService.class.getDeclaredMethod("releaseTickets");release.setAccessible(true);release.invoke(arena);
            replacement=new CombatService(service.server,cfg,service.configFile,service.stateFile,()->time[0]);KingdomsCombat.service=replacement;replacement.arenas.join(a);replacement.arenas.join(d);
            check.accept(replacement.arenas.data.match==null&&replacement.arenas.data.event.paused&&replacement.arenas.data.snapshots.isEmpty()&&a.getInventory().getItem(9).is(Items.EMERALD),"restart voids the interrupted match and restores original items without losing the bracket");
            check.accept(replacement.arenas.rating(a.getUUID()).wins==1&&replacement.arenas.rating(d.getUUID()).wins==1,"restart recovery does not grant duplicate Elo results");
            replacement.arenas.cancel();
            ArenaService doubles=replacement.arenas;doubles.open("Doubles integration",2);d.getInventory().setItem(0,new ItemStack(Items.STONE));late.getInventory().setItem(0,new ItemStack(Items.EMERALD));doubles.invite(a,b);doubles.accept(b);doubles.invite(d,late);doubles.accept(late);doubles.bracket(a.getUUID(),d.getUUID());
            check.accept(doubles.data.event.entries.size()==2&&doubles.data.event.entries.get(a.getUUID()).players.equals(List.of(a.getUUID(),b.getUUID())),"2v2 captain invitations require teammate acceptance and register complete ordered teams");
            check.accept(doubles.data.event.entries.get(d.getUUID()).banners.values().stream().allMatch(String::isEmpty),"2v2 captain and teammate can invite and accept without banners");
            doubles.start();prepareLoaded(doubles);time[0]+=cfg.arenaCountdownSeconds*1000L;doubles.tick();
            check.accept(!Protection.damage(b,a.damageSources().playerAttack(a))&&Protection.damage(d,a.damageSources().playerAttack(a)),"2v2 current teammates cannot hurt one another while opponents can fight");
            int oldA=doubles.rating(a.getUUID()).elo,oldB=doubles.rating(b.getUUID()).elo;
            doubles.eliminate(a,"fixture defeat");check.accept(!doubles.reserved(a.getUUID())&&doubles.fighter(b.getUUID())&&doubles.data.match!=null,"one defeated teammate immediately gets original items while the surviving teammate keeps fighting");
            doubles.eliminate(d,"fixture defeat");doubles.eliminate(late,"fixture defeat");
            check.accept(doubles.data.event.phase==Phase.FINISHED&&doubles.data.event.champion.equals(a.getUUID())&&doubles.rating(a.getUUID()).elo>oldA&&doubles.rating(b.getUUID()).elo>oldB,"doubles team victory finishes the tournament and updates the same individual Elo ladder");
            check.accept(doubles.data.snapshots.isEmpty()&&b.getInventory().getItem(9).is(Items.DIAMOND),"doubles completion leaves no kit inventory or pending restoration");
            doubles.data=saved;doubles.save();
        }finally{cfg.arenaAutoNext=auto;KingdomsCombat.service=service;arena.data=saved;arena.save();VoicePolicy.reset();for(ServerPlayer p:players)CombatIntegration.remove(service.server,p);}
    }
    private static void prepareLoaded(ArenaService arena)throws Exception{if(arena.data.match!=null&&arena.data.match.phase==MatchPhase.PREPARING){var prepare=ArenaService.class.getDeclaredMethod("prepare",Match.class);prepare.setAccessible(true);prepare.invoke(arena,arena.data.match);}}
    private ArenaIntegration(){}
}
