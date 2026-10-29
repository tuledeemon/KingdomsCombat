package com.celik.kingdomscombat;

import com.celik.kingdoms.*;
import com.celik.kingdomscombat.mixin.ArenaArtifactsAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.*;
import java.util.*;
import java.util.function.BiConsumer;

/** Bound equipment stays locked to players while server escrow can exchange it safely. */
final class ArenaBoundEquipmentIntegration {
    static void run(ArenaService arena,ServerPlayer a,ServerPlayer b,long[] time,BiConsumer<Boolean,String> check)throws Exception{
        var artifacts=KingdomsMod.service.artifacts;var access=(ArenaArtifactsAccessor)(Object)artifacts;CombatService recovered=null;ArtifactRules.Record record=null;
        try{
            a.closeContainer();artifacts.give(a,"golden_crown");int slot=java.util.stream.IntStream.range(0,a.getInventory().getContainerSize()).filter(i->{var r=access.combat$artifactRecord(a.getInventory().getItem(i));return r!=null&&r.type.equals("golden_crown");}).findFirst().orElseThrow();
            ItemStack equipped=a.getInventory().getItem(slot);a.getInventory().setItem(slot,ItemStack.EMPTY);a.setItemSlot(EquipmentSlot.HEAD,equipped);artifacts.crown.sync(a);record=artifacts.usable(equipped);artifacts.crown.killed(a,UUID.randomUUID());artifacts.crown.killed(a,UUID.randomUUID());a.setHealth(26);
            String original=arena.encode(equipped);Set<UUID> victims=Set.copyOf(access.combat$artifactData().crownLives.get(a.getUUID()).victims);
            check.accept(artifacts.crown.locked(a)&&a.getMaxHealth()==28&&!artifacts.crown.mayReplace(a,ItemStack.EMPTY),"bound equipment begins locked with its persistent progress and derived health");
            b.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));arena.kit(b);arena.open("Equipment kit fixture",1,false);arena.apply(a);arena.apply(b);arena.start();prepare(arena);
            check.accept(a.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)&&a.getMaxHealth()==20&&a.getHealth()==20&&!a.hasEffect(MobEffects.GLOWING),"kit preparation escrows worn bound equipment without carrying its attributes or effects into the admin kit");
            var saved=CombatStore.read(arena.file,ArenaRules.Data.class,null).snapshots.get(a.getUUID());check.accept(saved.boundStateSaved&&saved.boundState!=null&&saved.items.get(39).contains("kingdoms_artifact"),"worn item and its persistent wearer state are durable before the kit swap");
            time[0]+=arena.service.config.arenaCountdownSeconds*1000L;arena.tick();arena.eliminate(a,"fixture defeat");
            check.accept(same(arena,original,a)&&a.getMaxHealth()==28&&a.getHealth()==26&&artifacts.crown.locked(a)&&access.combat$artifactData().crownLives.get(a.getUUID()).victims.equals(victims),"kit defeat restores the authentic worn item, original health, binding and progress");
            arena.open("Equipment own fixture",1,true);arena.apply(a);arena.apply(b);arena.start();prepare(arena);
            check.accept(same(arena,original,a)&&a.getMaxHealth()==28&&a.getHealth()==28&&a.hasEffect(MobEffects.GLOWING),"own-gear preparation keeps worn bound equipment usable with its correct full-health attributes");
            a.getInventory().setItem(39,ItemStack.EMPTY);a.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));a.inventoryMenu.clicked(5,0,net.minecraft.world.inventory.ContainerInput.PICKUP,a);
            check.accept(same(arena,original,a)&&a.inventoryMenu.getCarried().isEmpty()&&!artifacts.crown.mayReplace(a,ItemStack.EMPTY),"inventory, equipment and ordinary UI removal remain locked outside the synchronous escrow exchange");
            time[0]+=arena.service.config.arenaCountdownSeconds*1000L;arena.tick();artifacts.crown.killed(a,UUID.randomUUID());a.invulnerableTime=0;a.hurtServer(a.level(),a.damageSources().playerAttack(b),1000);
            check.accept(a.isAlive()&&same(arena,original,a)&&a.getMaxHealth()==28&&a.getHealth()==26&&access.combat$artifactData().crownLives.get(a.getUUID()).victims.equals(victims),"native lethal arena defeat restores the original bound progress and health without a real death");
            arena.open("Equipment recovery fixture",1,true);arena.apply(a);arena.apply(b);arena.start();prepare(arena);artifacts.crown.killed(a,UUID.randomUUID());
            var release=ArenaService.class.getDeclaredMethod("releaseTickets");release.setAccessible(true);release.invoke(arena);
            recovered=new CombatService(arena.service.server,arena.service.config,arena.service.configFile,arena.service.stateFile,()->time[0]);KingdomsCombat.service=recovered;recovered.arenas.join(a);recovered.arenas.join(b);
            check.accept(recovered.arenas.data.match==null&&recovered.arenas.data.event.paused&&recovered.arenas.data.snapshots.isEmpty()&&same(arena,original,a)&&a.getMaxHealth()==28&&a.getHealth()==26&&access.combat$artifactData().crownLives.get(a.getUUID()).victims.equals(victims),"restart recovery restores bound item state and health while retaining the paused bracket");
            check.accept(!recovered.arenas.swappingInventory(a)&&!artifacts.crown.mayReplace(a,ItemStack.EMPTY),"the privileged exchange scope is cleared after successful recovery");recovered.arenas.cancel();
        }finally{
            if(recovered!=null)recovered.stop();KingdomsCombat.service=arena.service;
            if(record!=null){artifacts.crown.died(a);artifacts.retire(record.id);a.getInventory().setItem(39,ItemStack.EMPTY);artifacts.crown.sync(a);a.removeAllEffects();a.setHealth(20);}
        }
    }
    private static boolean same(ArenaService arena,String expected,ServerPlayer p){return com.google.gson.JsonParser.parseString(expected).equals(com.google.gson.JsonParser.parseString(arena.encode(p.getItemBySlot(EquipmentSlot.HEAD))));}
    private static void prepare(ArenaService arena)throws Exception{if(arena.data.match!=null&&arena.data.match.phase==ArenaRules.MatchPhase.PREPARING){var method=ArenaService.class.getDeclaredMethod("prepare",ArenaRules.Match.class);method.setAccessible(true);method.invoke(arena,arena.data.match);}}
    private ArenaBoundEquipmentIntegration(){}
}
