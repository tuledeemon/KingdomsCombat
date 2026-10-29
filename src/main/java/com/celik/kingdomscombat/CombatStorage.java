package com.celik.kingdomscombat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

public final class CombatStorage {
    public static boolean enderChest(ServerPlayer p,AbstractContainerMenu menu){return menu!=null&&menu.slots.stream().anyMatch(slot->slot.container==p.getEnderChestInventory());}
    public static boolean blocked(ServerPlayer p){var s=KingdomsCombat.service;return s!=null&&s.config.blockEnderChestsInCombat&&s.status(p).combatMillis()>0;}
    public static void explain(ServerPlayer p){p.sendSystemMessage(CombatService.text("Ender chests are unavailable during combat.","red"));}
    public static void enforce(ServerPlayer p){if(enderChest(p,p.containerMenu)&&blocked(p)){p.closeContainer();explain(p);}}
    private CombatStorage(){}
}
