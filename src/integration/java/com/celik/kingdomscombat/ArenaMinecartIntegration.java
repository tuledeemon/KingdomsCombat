package com.celik.kingdomscombat;

import com.celik.kingdoms.Protection;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.function.BiConsumer;

final class ArenaMinecartIntegration {
    static List<AbstractMinecart> run(ArenaService arena,ServerPlayer fighter,ServerPlayer waiting,BiConsumer<Boolean,String> check){
        var level=fighter.level();var region=arena.data.setup.region;Vec3 origin=fighter.position();ItemStack held=fighter.getMainHandItem().copy();List<AbstractMinecart> carts=new ArrayList<>();
        Item[] items={Items.MINECART,Items.TNT_MINECART,Items.CHEST_MINECART,Items.HOPPER_MINECART,Items.FURNACE_MINECART};
        CartSpawnCapture.enabled=true;CartSpawnCapture.attempts.clear();
        try{
            for(int i=0;i<items.length;i++){
                BlockPos rail=new BlockPos(region.minX+4+i,region.minY+1,region.minZ+3);
                try(var scope=Protection.scope(level,rail,fighter.getUUID(),fighter,Protection.Cause.PLAYER)){level.setBlock(rail,Blocks.RAIL.defaultBlockState(),3);}
                fighter.setPos(rail.getX()+.5,rail.getY(),rail.getZ()-1.5);fighter.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(items[i]));var result=fighter.gameMode.useItemOn(fighter,level,fighter.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(rail),Direction.UP,rail,false));
                var attempt=CartSpawnCapture.attempts.getLast();check.accept(result.consumesAction()&&attempt.accepted()&&ArenaService.temporaryMinecart(attempt.cart()),"active fighter can place "+items[i]+" through the real item-use path: "+attempt);carts.add(attempt.cart());
            }
            check.accept(Protection.owner(carts.getFirst()).equals(fighter.getUUID())&&arena.entityActive(carts.getFirst()),"placed carts retain their fighter provenance and match cleanup tag");
            check.accept(arena.useEntity(fighter,carts.getFirst())&&!arena.useEntity(waiting,carts.getFirst()),"only active fighters may interact with the match's non-storage carts");
            BlockPos rail=carts.getFirst().blockPosition();var denied=new Minecart(EntityTypes.MINECART,level);denied.setPos(carts.getFirst().position());try(var scope=Protection.scope(level,rail,waiting.getUUID(),waiting,Protection.Cause.PLAYER)){check.accept(!arena.admit(denied),"waiting entrants cannot spawn carts into a running match");}
            arena.service.config.arenaMinecarts=false;try(var scope=Protection.scope(level,rail,fighter.getUUID(),fighter,Protection.Cause.PLAYER)){check.accept(!arena.admit(denied),"the minecart placement option can be disabled independently");}finally{arena.service.config.arenaMinecarts=true;}
            var chest=(AbstractMinecartContainer)carts.get(2);check.accept(!arena.useEntity(fighter,chest)&&chest.createMenu(80,fighter.getInventory(),fighter)==null&&!chest.canPlaceItem(0,new ItemStack(Items.DIAMOND))&&!chest.canTakeItem(fighter.getInventory(),0,new ItemStack(Items.DIAMOND)),"temporary chest carts cannot open or transfer inventory through automation");
            var hopper=(MinecartHopper)carts.get(3);check.accept(!hopper.suckInItems(),"temporary hopper carts cannot collect loose items or inventory contents");
            AABB bounds=new AABB(region.minX-5,region.minY-5,region.minZ-5,region.maxX+6,region.maxY+6,region.maxZ+6);int before=level.getEntitiesOfClass(ItemEntity.class,bounds).size();chest.setItem(0,new ItemStack(Items.DIAMOND,5));chest.destroy(level,chest.damageSources().generic());check.accept(chest.isRemoved()&&level.getEntitiesOfClass(ItemEntity.class,bounds).size()==before,"destroyed temporary storage carts produce neither cart drops nor inventory loot");
            check.accept(carts.getFirst().spawnAtLocation(level,new ItemStack(Items.MINECART),Vec3.ZERO)==null,"temporary cart item drops cannot escape the match through the native drop method");
            var normal=new MinecartChest(EntityTypes.CHEST_MINECART,level);check.accept(normal.canPlaceItem(0,new ItemStack(Items.DIAMOND))&&normal.canTakeItem(fighter.getInventory(),0,new ItemStack(Items.DIAMOND)),"ordinary untagged storage carts keep normal transfer behavior");
            BlockPos button=new BlockPos(region.minX+2,region.minY+1,region.minZ+10);for(Block block:List.of(Blocks.STONE_BUTTON,Blocks.OAK_BUTTON)){
                try(var scope=Protection.scope(level,button,fighter.getUUID(),fighter,Protection.Cause.PLAYER)){level.setBlock(button,block.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE,AttachFace.FLOOR),3);level.getBlockState(button).useWithoutItem(level,fighter,new BlockHitResult(Vec3.atCenterOf(button),Direction.UP,button,false));}
                check.accept(level.getBlockState(button).getValue(ButtonBlock.POWERED),"active fighter can press "+block);
                try(var scope=Protection.scope(level,button,null,null,Protection.Cause.BLOCK)){level.getBlockState(button).tick(level,button,net.minecraft.util.RandomSource.create(1));}
                check.accept(!level.getBlockState(button).getValue(ButtonBlock.POWERED),"scheduled "+block+" release can retract inside the arena");
            }
            carts.get(4).setPos(region.maxX+80,region.minY+1,region.maxZ+80);return carts;
        }finally{CartSpawnCapture.enabled=false;CartSpawnCapture.attempts.clear();fighter.stopRiding();fighter.setPos(origin);fighter.setItemInHand(InteractionHand.MAIN_HAND,held);}
    }
    private ArenaMinecartIntegration(){}
}
