package com.celik.kingdomscombat;

import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

final class PermissionCompatibility {
    static void run(ServerPlayer operator,ServerPlayer ordinary,CombatService service,BiConsumer<Boolean,String> check){
        Map<UUID,Map<String,Boolean>> grants=new HashMap<>();
        PermissionCheckEvent.EVENT.register((source,node)->{
            if(source instanceof CommandSourceStack commands&&commands.getPlayer()!=null){
                Boolean value=grants.getOrDefault(commands.getPlayer().getUUID(),Map.of()).get(node);
                if(value!=null)return value?TriState.TRUE:TriState.FALSE;
            }
            return TriState.DEFAULT;
        });
        var config=service.config;
        grants.put(ordinary.getUUID(),new HashMap<>(Map.of(config.staffModePermission,true,config.eventHostPermission,true)));
        var source=ordinary.createCommandSourceStack();
        check.accept(CombatService.allowed(source,config.staffModePermission,config.adminLevel),"permission provider grants staff mode to a non-operator");
        ordinary.level().getServer().getCommands().performPrefixedCommand(source,"staffmode");
        check.accept(service.staffMode(ordinary),"non-operator staff command works through the actual permissions API");
        grants.get(ordinary.getUUID()).put(config.staffModePermission,false);
        check.accept(!service.staffMode(ordinary),"revoking a permission node immediately disables the exemption");
        grants.put(operator.getUUID(),Map.of(config.staffModePermission,false));
        check.accept(!CombatService.allowed(operator.createCommandSourceStack(),config.staffModePermission,config.adminLevel),"explicit permission denial overrides operator fallback");
        check.accept(CombatService.allowed(source,config.eventHostPermission,config.adminLevel)&&!CombatService.allowed(source,config.adminPermission,config.adminLevel),"eventhost grant does not grant unrelated combat admin access");
        ordinary.level().getServer().getCommands().performPrefixedCommand(source,"eventhost \"Permission event\" 30s");
        check.accept(service.events.active(),"non-operator event host command works with its own permission node");
        service.events.stop(source);service.disconnect(ordinary);grants.clear();
    }
}
