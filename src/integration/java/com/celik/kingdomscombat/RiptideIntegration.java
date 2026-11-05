package com.celik.kingdomscombat;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.function.BiConsumer;

final class RiptideIntegration {
    static void run(CombatService s,long[] time,BiConsumer<Boolean,String> check)throws Exception{
        var level=s.server.overworld();var a=CombatIntegration.player(s.server,level,"RiptideUser",19000,180,19000);var b=CombatIntegration.player(s.server,level,"RiptideRival",19004,180,19000);
        long clock=time[0];int cooldown=s.config.riptideCooldownSeconds;boolean reset=s.config.resetOnRiptide;
        var fluid=Entity.class.getDeclaredMethod("updateFluidInteraction");fluid.setAccessible(true);
        try{
            ItemStack trident=new ItemStack(Items.TRIDENT);var enchant=level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);EnchantmentHelper.updateEnchantments(trident,m->m.set(enchant.getOrThrow(Enchantments.RIPTIDE),3));a.setItemInHand(InteractionHand.MAIN_HAND,trident);a.setOnGround(false);
            level.setBlockAndUpdate(a.blockPosition(),Blocks.WATER.defaultBlockState());level.setBlockAndUpdate(a.blockPosition().above(),Blocks.WATER.defaultBlockState());fluid.invoke(a);
            check.accept(a.isInWaterOrRain()&&s.config.riptideCooldownSeconds==0,"Riptide fixture uses native water detection and cooldown defaults to disabled");
            s.ledger.hit(a.getUUID(),b.getUUID(),time[0],s.config);time[0]+=10000;long other=s.status(b).combatMillis();
            check.accept(launch(a,trident)&&s.status(a).seconds()==20&&s.status(b).combatMillis()==other,"actual Riptide impulse refreshes only its user's existing combat timer");
            s.ledger.clear(a.getUUID());s.ledger.clear(b.getUUID());check.accept(launch(a,trident)&&!s.status(a).active(),"Riptide in safety does not create a new combat tag");
            s.config.riptideCooldownSeconds=5;
            check.accept(launch(a,trident)&&launch(a,trident)&&!s.ledger.data.riptideCooldowns.containsKey(a.getUUID()),"safe Riptide launches neither start nor enforce a configured cooldown");s.config.riptideCooldownSeconds=0;
            s.ledger.hit(a.getUUID(),b.getUUID(),time[0],s.config);time[0]+=4000;s.config.resetOnRiptide=false;long before=s.status(a).combatMillis();launch(a,trident);check.accept(s.status(a).combatMillis()==before,"Riptide tag refresh can be disabled independently");s.config.resetOnRiptide=true;
            s.config.riptideCooldownSeconds=5;check.accept(launch(a,trident),"successful native Riptide launch starts optional cooldown");
            long expiry=s.ledger.data.riptideCooldowns.get(a.getUUID());int damage=trident.getDamageValue();a.setDeltaMovement(Vec3.ZERO);before=s.status(a).combatMillis();
            check.accept(!launch(a,trident)&&a.getDeltaMovement().lengthSqr()==0&&trident.getDamageValue()==damage&&s.status(a).combatMillis()==before,"cooldown blocks release without impulse, durability cost or timer refresh");
            var replacement=trident.copy();a.setItemInHand(InteractionHand.OFF_HAND,replacement);a.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);check.accept(!launch(a,replacement)&&!s.riptideBlocked(b),"Riptide cooldown follows the player across hands and items without affecting other players");a.setItemInHand(InteractionHand.MAIN_HAND,trident);
            var persisted=CombatStore.read(s.stateFile,TagLedger.Data.class,null);check.accept(persisted.riptideCooldowns.get(a.getUUID())==expiry,"Riptide cooldown expiry is saved for reconnect and restart recovery");
            s.ledger.clear(a.getUUID());check.accept(launch(a,trident)&&s.ledger.data.riptideCooldowns.get(a.getUUID())==expiry,"leaving PvP permits Riptide immediately without extending a saved cooldown");
            s.ledger.zone(a.getUUID(),TagLedger.Reason.WAR,time[0],20);check.accept(launch(a,trident)&&s.ledger.data.riptideCooldowns.get(a.getUUID())==expiry,"war-only lock does not enforce or start Riptide cooldown");
            s.ledger.clear(a.getUUID());s.ledger.zone(a.getUUID(),TagLedger.Reason.AIRDROP,time[0],20);check.accept(launch(a,trident)&&s.ledger.data.riptideCooldowns.get(a.getUUID())==expiry,"AirDrop-only lock does not enforce or start Riptide cooldown");
            s.ledger.clear(a.getUUID());s.ledger.hit(a.getUUID(),b.getUUID(),time[0],s.config);check.accept(!launch(a,trident),"remaining cooldown is enforced if PvP resumes before its real-time expiry");
            s.config.riptideCooldownSeconds=0;check.accept(launch(a,trident),"setting cooldown to zero disables an existing cooldown immediately");s.config.riptideCooldownSeconds=5;time[0]=expiry;check.accept(launch(a,trident),"Riptide launches again at the exact real-time cooldown boundary");
            var pos=a.blockPosition();level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos.above(),Blocks.AIR.defaultBlockState());fluid.invoke(a);s.ledger.data.riptideCooldowns.remove(a.getUUID());before=s.status(a).combatMillis();damage=trident.getDamageValue();
            check.accept(!launch(a,trident)&&!s.ledger.data.riptideCooldowns.containsKey(a.getUUID())&&trident.getDamageValue()==damage&&s.status(a).combatMillis()==before,"failed dry Riptide use neither refreshes combat nor starts cooldown");
        }finally{s.config.riptideCooldownSeconds=cooldown;s.config.resetOnRiptide=reset;s.ledger.clear(a.getUUID());s.ledger.clear(b.getUUID());s.ledger.data.riptideCooldowns.remove(a.getUUID());s.save();time[0]=clock;CombatIntegration.remove(s.server,a);CombatIntegration.remove(s.server,b);}
    }
    private static boolean launch(net.minecraft.server.level.ServerPlayer player,ItemStack stack){return ((TridentItem)Items.TRIDENT).releaseUsing(stack,player.level(),player,71980);}
    private RiptideIntegration(){}
}
