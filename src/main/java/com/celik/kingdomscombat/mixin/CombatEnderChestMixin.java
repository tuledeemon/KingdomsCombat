package com.celik.kingdomscombat.mixin;

import com.celik.kingdomscombat.CombatStorage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ServerPlayer.class)
public abstract class CombatEnderChestMixin {
    @Redirect(method="openMenu",at=@At(value="INVOKE",target="Lnet/minecraft/world/MenuProvider;createMenu(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;"))
    private AbstractContainerMenu open(MenuProvider provider,int id,Inventory inventory,Player player){AbstractContainerMenu menu=provider.createMenu(id,inventory,player);ServerPlayer p=(ServerPlayer)player;if(CombatStorage.enderChest(p,menu)&&CombatStorage.blocked(p)){menu.removed(p);CombatStorage.explain(p);return null;}return menu;}
}
