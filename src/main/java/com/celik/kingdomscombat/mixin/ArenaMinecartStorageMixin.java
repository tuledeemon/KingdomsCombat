package com.celik.kingdomscombat.mixin;

import com.celik.kingdomscombat.ArenaService;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Placed storage carts are combat props, not routes out of inventory escrow. */
@Mixin(AbstractMinecartContainer.class)
public abstract class ArenaMinecartStorageMixin implements Container {
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return !ArenaService.temporaryMinecart((Entity)(Object)this);}
    @Override public boolean canTakeItem(Container destination,int slot,ItemStack stack){return !ArenaService.temporaryMinecart((Entity)(Object)this);}
    @Inject(method="interact",at=@At("HEAD"),cancellable=true)
    private void interact(Player player,InteractionHand hand,Vec3 hit,CallbackInfoReturnable<InteractionResult> cir){if(ArenaService.temporaryMinecart((Entity)(Object)this))cir.setReturnValue(InteractionResult.FAIL);}
    @Inject(method="createMenu(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;",at=@At("HEAD"),cancellable=true)
    private void menu(int id,Inventory inventory,Player player,CallbackInfoReturnable<AbstractContainerMenu> cir){if(ArenaService.temporaryMinecart((Entity)(Object)this))cir.setReturnValue(null);}
    @Inject(method="remove",at=@At("HEAD"))
    private void remove(Entity.RemovalReason reason,CallbackInfo ci){if(ArenaService.temporaryMinecart((Entity)(Object)this)){var cart=(AbstractMinecartContainer)(Object)this;cart.setContainerLootTable(null);cart.clearItemStacks();}}
    @Inject(method="destroy",at=@At("HEAD"))
    private void destroy(net.minecraft.server.level.ServerLevel level,net.minecraft.world.damagesource.DamageSource source,CallbackInfo ci){if(ArenaService.temporaryMinecart((Entity)(Object)this)){var cart=(AbstractMinecartContainer)(Object)this;cart.setContainerLootTable(null);cart.clearItemStacks();}}
}
