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
            a.getInventory().setItem(0,new ItemStack(Items.DIAMOND_SWORD));var kitShield=new ItemStack(Items.SHIELD);kitShield.setDamageValue(17);kitShield.set(DataComponents.CUSTOM_NAME,Component.literal("Original kit shield"));var enchants=service.server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(kitShield,e->{e.set(enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING),3);e.set(enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.MENDING),1);});a.setItemSlot(EquipmentSlot.OFFHAND,kitShield.copy());a.getInventory().setItem(7,kitShield.copy());a.getInventory().setItem(8,new ItemStack(Items.COBBLESTONE,16));arena.kit(a);
            for(ServerPlayer p:players){p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Items.BANNER.blue()));p.getInventory().setItem(9,new ItemStack(Items.DIAMOND,7));p.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));p.setHealth(16);p.experienceLevel=12;p.totalExperience=50;p.experienceProgress=.25f;p.addEffect(new MobEffectInstance(MobEffects.SPEED,1200,1));}
            a.setPos(x,161,z);arena.corner(a,1);a.setPos(x+15,164,z+15);arena.corner(a,2);arena.claim();a.setPos(x+2.5,161,z+2.5);arena.spawn(a,1);b.setPos(x+12.5,161,z+12.5);arena.spawn(b,2);
            Gate gate=new Gate();gate.x=x+8;gate.y=161;gate.z=z+8;gate.state=net.minecraft.world.level.block.state.BlockState.CODEC.encodeStart(JsonOps.INSTANCE,Blocks.IRON_BARS.defaultBlockState()).getOrThrow().toString();arena.data.setup.gates.add(gate);arena.resetTerrain();
            check.accept(arena.overlapsChunk(level,x>>4,z>>4),"arena claim reserves its intersecting wilderness chunks");
            boolean claimRejected=false;try{KingdomsMod.service.create(a,"ArenaShouldNotClaim");}catch(IllegalArgumentException expected){claimRejected=expected.getMessage().contains("arena");}check.accept(claimRejected,"kingdom creation cannot overlap an event arena");
            arena.open("Integration tournament",1);b.getInventory().setItem(0,new ItemStack(Items.STONE));for(ServerPlayer p:players)arena.apply(p);check.accept(arena.data.event.entries.get(b.getUUID()).banners.get(b.getUUID()).isEmpty(),"solo applications without a held banner are accepted");arena.bracket(a.getUUID(),b.getUUID());arena.bracket(c.getUUID(),d.getUUID());arena.start();prepareLoaded(arena);
            check.accept(arena.data.event.entries.size()==4&&!arena.data.event.entries.containsKey(late.getUUID()),"five applications retain the first four and exclude the most recent");
            check.accept(arena.data.match.phase==MatchPhase.COUNTDOWN&&arena.reserved(a.getUUID())&&!arena.reserved(c.getUUID()),"only current fighters receive kits and inventory backups");
            check.accept(!arena.build(c,level,new BlockPos(x+4,161,z+4))&&!arena.fighter(a.getUUID()),"registered waiting players cannot build and countdown fighters cannot build early");
            check.accept(a.getInventory().getItem(0).is(Items.DIAMOND_SWORD)&&a.getOffhandItem().is(Items.SHIELD)&&a.getOffhandItem().get(DataComponents.BASE_COLOR)==DyeColor.BLUE,"saved kit is applied and banner color appears on its shield");
            check.accept(ItemStack.isSameItemSameComponents(b.getOffhandItem(),kitShield),"no-banner applicants retain the unmodified admin kit shield");
            check.accept(a.getInventory().getItem(7).get(DataComponents.BASE_COLOR)==DyeColor.BLUE&&a.getOffhandItem().getDamageValue()==17&&a.getOffhandItem().get(DataComponents.CUSTOM_NAME).equals(kitShield.get(DataComponents.CUSTOM_NAME))&&a.getOffhandItem().get(DataComponents.ENCHANTMENTS).equals(kitShield.get(DataComponents.ENCHANTMENTS)),"banner decoration preserves shield slot, damage, custom name, Mending and Unbreaking");
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
            check.accept(a.gameMode.destroyBlock(changed)&&level.getBlockState(changed).isAir(),"current fighter can break an ordinary block through native destroyBlock");
            try(var scope=Protection.scope(level,changed,a.getUUID(),a,Protection.Cause.PLAYER)){level.setBlock(changed,Blocks.TORCH.defaultBlockState(),3);}
            a.gameMode.handleBlockBreakAction(changed,net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,net.minecraft.core.Direction.UP,level.getMaxY(),0);
            check.accept(level.getBlockState(changed).isAir(),"current fighter can break an instant block through the actual mining action path");
            BlockPos floor=new BlockPos(x+4,160,z+4);check.accept(a.gameMode.destroyBlock(floor)&&level.getBlockState(floor).isAir(),"standing-height corner selections include a floor that current fighters can mine");
            BlockPos water=new BlockPos(x+6,162,z+6),lava=new BlockPos(x+10,162,z+10);
            try(var scope=Protection.scope(level,water,a.getUUID(),a,Protection.Cause.PLAYER)){level.setBlock(water,Blocks.WATER.defaultBlockState(),3);level.setBlock(lava,Blocks.LAVA.defaultBlockState(),3);}
            try(var scope=Protection.scope(level,water,null,null,Protection.Cause.FLUID)){level.getFluidState(water).tick(level,water,level.getBlockState(water));}
            try(var scope=Protection.scope(level,lava,null,null,Protection.Cause.FLUID)){level.getFluidState(lava).tick(level,lava,level.getBlockState(lava));}
            check.accept(!level.getFluidState(water.below()).isEmpty()&&!level.getFluidState(lava.below()).isEmpty(),"native water and lava ticks spread inside the arena");
            BlockPos edge=new BlockPos(arena.data.setup.region.maxX,162,z+6);check.accept(!Protection.fluid(level,edge,edge.east())&&!Protection.fluid(level,edge.east(),edge),"arena liquids cannot flow out or be poured in across its boundary");
            check.accept(!Protection.explosion(level,changed,changed,a.getUUID()),"arena prevents explosion block drops and terrain changes before the native callback");
            b.invulnerableTime=0;b.hurtServer(level,a.damageSources().playerAttack(a),1000);
            check.accept(b.isAlive()&&!arena.reserved(b.getUUID())&&arena.data.match==null,"native lethal damage eliminates the loser without a death or item drops");
            check.accept(a.getInventory().getItem(9).is(Items.DIAMOND)&&a.getInventory().getItem(9).getCount()==7&&a.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS)&&a.getHealth()==16&&a.experienceLevel==12&&a.getEffect(MobEffects.SPEED).getAmplifier()==1,"match completion restores original inventory, armor, health, XP and effects");
            check.accept(level.getFluidState(water).isEmpty()&&level.getFluidState(water.below()).isEmpty()&&level.getFluidState(lava).isEmpty()&&level.getFluidState(lava.below()).isEmpty()&&level.getBlockState(floor).is(Blocks.STONE),"reset removes source and flowed liquids and restores the mined floor");
            kitArrow.baseTick();check.accept(kitArrow.isRemoved(),"temporary kit projectiles expire after their match even outside the cleanup radius");
            check.accept(level.getBlockState(changed).isAir()&&level.getBlockState(new BlockPos(gate.x,gate.y,gate.z)).is(Blocks.IRON_BARS),"arena terrain and gates restore between matches");
            check.accept(arena.rating(a.getUUID()).elo==1016&&arena.rating(b.getUUID()).elo==984&&arena.rating(a.getUUID()).wins==1,"one real match updates the shared individual Elo exactly once");
            arena.data.snapshots.put(a.getUUID(),snapshot);snapshot.restoring=true;arena.save();a.getInventory().setItem(9,new ItemStack(Items.EMERALD,5));arena.restore(a);
            check.accept(a.getInventory().getItem(9).is(Items.EMERALD)&&arena.data.snapshots.isEmpty(),"durable player receipt prevents replaying an already restored inventory");
            List<String> originalKit=new ArrayList<>(arena.data.setup.kit);arena.data.setup.kit.set(7,"");arena.data.setup.kit.set(40,arena.encode(new ItemStack(Items.TORCH)));arena.save();
            arena.next();prepareLoaded(arena);check.accept(c.getOffhandItem().is(Items.TORCH)&&java.util.stream.IntStream.range(0,c.getInventory().getContainerSize()).noneMatch(i->c.getInventory().getItem(i).is(Items.SHIELD)),"a banner never creates a shield or replaces the offhand in a shield-free kit");time[0]+=cfg.arenaCountdownSeconds*1000L;arena.tick();
            c.connection.onDisconnect(new DisconnectionDetails(Component.literal("Arena forfeit fixture")));
            check.accept(c.getHealth()>0&&c.getInventory().getItem(9).is(Items.DIAMOND)&&arena.rating(d.getUUID()).wins==1&&!service.ledger.data.logoutPenalties.containsKey(c.getUUID()),"native disconnect forfeits immediately, restores original items and never triggers ordinary combat-log drops");
            arena.data.setup.kit=new ArrayList<>(originalKit);arena.data.setup.kit.set(0,arena.encode(kitShield));arena.data.setup.kit.set(7,"");arena.data.setup.kit.set(40,arena.encode(new ItemStack(Items.TORCH)));arena.save();
            arena.next();prepareLoaded(arena);check.accept(a.getMainHandItem().is(Items.SHIELD)&&a.getMainHandItem().get(DataComponents.BASE_COLOR)==DyeColor.BLUE&&a.getMainHandItem().get(DataComponents.ENCHANTMENTS).equals(kitShield.get(DataComponents.ENCHANTMENTS))&&a.getOffhandItem().is(Items.TORCH),"a main-hand kit shield is bannered in place without replacing another offhand item");check.accept(arena.reserved(a.getUUID())&&arena.reserved(d.getUUID()),"final round contains only semifinal winners");
            var release=ArenaService.class.getDeclaredMethod("releaseTickets");release.setAccessible(true);release.invoke(arena);
            int paddedMin=arena.data.setup.region.minY,paddedMax=arena.data.setup.region.maxY;arena.data.setup.regionPadding=0;arena.data.setup.region.minY++;arena.data.setup.region.maxY-=2;arena.save();
            replacement=new CombatService(service.server,cfg,service.configFile,service.stateFile,()->time[0]);KingdomsCombat.service=replacement;replacement.arenas.join(a);replacement.arenas.join(d);
            check.accept(replacement.arenas.data.setup.region.minY==paddedMin&&replacement.arenas.data.setup.region.maxY==paddedMax,"legacy arena selections automatically gain floor and headroom padding on load");
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
            ownGear(doubles,a,b,time,check);
            doubles.data=saved;doubles.save();
        }finally{cfg.arenaAutoNext=auto;KingdomsCombat.service=service;arena.data=saved;arena.save();VoicePolicy.reset();for(ServerPlayer p:players)CombatIntegration.remove(service.server,p);}
    }
    private static void ownGear(ArenaService arena,ServerPlayer a,ServerPlayer b,long[] time,BiConsumer<Boolean,String> check)throws Exception{
        arena.data.setup.kit.clear();arena.setGear(true);
        ItemStack sword=new ItemStack(Items.DIAMOND_SWORD);sword.setDamageValue(17);sword.set(DataComponents.CUSTOM_NAME,Component.literal("Personal sword"));a.getInventory().setItem(0,sword);a.getInventory().setItem(8,new ItemStack(Items.COOKED_BEEF,12));b.getInventory().setItem(0,new ItemStack(Items.BOW));
        KingdomsMod.service.artifacts.give(a,"thunder_axe");int protectedSlot=java.util.stream.IntStream.range(0,a.getInventory().getContainerSize()).filter(i->Artifacts.marked(a.getInventory().getItem(i))).findFirst().orElseThrow();ItemStack protectedItem=a.getInventory().getItem(protectedSlot).copy();KingdomsMod.service.artifacts.give(a,"guardian_angel");int guardianSlot=java.util.stream.IntStream.range(0,9).filter(i->a.getInventory().getItem(i).is(Items.HEART_OF_THE_SEA)).findFirst().orElseThrow();
        a.removeAllEffects();a.addEffect(new MobEffectInstance(MobEffects.POISON,1000,1));a.setHealth(3);a.getFoodData().setFoodLevel(5);a.getFoodData().setSaturation(0);a.getFoodData().addExhaustion(8);
        arena.open("Own gear integration",1);arena.add(List.of(a));arena.add(List.of(b));arena.start();prepareLoaded(arena);
        check.accept(arena.data.event.useOwnGear&&arena.data.setup.kit.isEmpty()&&a.getMainHandItem().is(Items.DIAMOND_SWORD)&&a.getMainHandItem().getDamageValue()==17&&b.getMainHandItem().is(Items.BOW),"own-gear rounds use each fighter's actual loadout without requiring an admin kit");
        check.accept(Artifacts.marked(a.getInventory().getItem(protectedSlot))&&KingdomsMod.service.artifacts.usable(a.getInventory().getItem(protectedSlot))!=null&&!arena.data.snapshots.get(a.getUUID()).artifacts.isEmpty(),"own-gear fighters can use authenticated artifacts while their record state is durably backed up");
        boolean storageBlocked=false;try{arena.service.events.chests.open(a);}catch(IllegalArgumentException expected){storageBlocked=expected.getMessage().contains("arena");}check.accept(storageBlocked,"arena escrow cannot be exported through the event chest command/menu");
        boolean locked=false;try{arena.setGear(false);}catch(IllegalArgumentException expected){locked=true;}check.accept(locked&&arena.data.event.useOwnGear,"gear mode is locked once a tournament is running");
        check.accept(a.getActiveEffects().isEmpty()&&a.getHealth()==a.getMaxHealth()&&a.getFoodData().getFoodLevel()==20&&a.getFoodData().getSaturationLevel()==20,"preparation removes effects and fills health, food and saturation");
        a.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,1000,1));a.setHealth(2);a.getFoodData().setFoodLevel(4);a.getFoodData().setSaturation(0);a.getFoodData().addExhaustion(8);time[0]+=arena.service.config.arenaCountdownSeconds*1000L;arena.tick();a.getFoodData().tick(a);
        check.accept(arena.fighter(a.getUUID())&&a.getActiveEffects().isEmpty()&&a.getHealth()==a.getMaxHealth()&&a.getFoodData().getFoodLevel()==20&&a.getFoodData().getSaturationLevel()==20,"gate opening resets countdown effects, health, hunger and exhaustion immediately before combat");
        check.accept(CombatStore.read(arena.file,Data.class,null).event.useOwnGear,"own-gear mode is saved with the tournament");
        a.getInventory().setSelectedSlot(guardianSlot);var guardian=KingdomsMod.service.artifacts.usable(a.getMainHandItem());int charges=guardian.uses;KingdomsMod.service.artifacts.use(a,net.minecraft.world.InteractionHand.MAIN_HAND);check.accept(guardian.uses==charges-1,"personal Guardian Angel can activate and spend a charge during an own-gear match");a.getInventory().setSelectedSlot(0);KingdomsMod.service.artifacts.destroyed(a.getInventory().getItem(protectedSlot));a.getInventory().setItem(protectedSlot,ItemStack.EMPTY);
        a.getMainHandItem().setDamageValue(100);a.getInventory().getItem(8).shrink(5);arena.eliminate(b,"fixture defeat");
        check.accept(ItemStack.isSameItemSameComponents(a.getMainHandItem(),sword)&&a.getMainHandItem().getDamageValue()==17&&a.getInventory().getItem(8).getCount()==12&&a.getHealth()==3,"round completion restores personal item components, original wear, consumed supplies and prior health");
        check.accept(com.google.gson.JsonParser.parseString(arena.encode(a.getInventory().getItem(protectedSlot))).equals(com.google.gson.JsonParser.parseString(arena.encode(protectedItem)))&&KingdomsMod.service.artifacts.usable(a.getInventory().getItem(protectedSlot))!=null,"artifacts broken and retired during a match return authenticated with their saved abilities intact");
        check.accept(KingdomsMod.service.artifacts.usable(a.getInventory().getItem(guardianSlot)).uses==charges,"Guardian Angel charges restore from the saved artifact record after the round");
    }
    private static void prepareLoaded(ArenaService arena)throws Exception{if(arena.data.match!=null&&arena.data.match.phase==MatchPhase.PREPARING){var prepare=ArenaService.class.getDeclaredMethod("prepare",Match.class);prepare.setAccessible(true);prepare.invoke(arena,arena.data.match);}}
    private ArenaIntegration(){}
}
