package com.celik.kingdomscombat;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Effective permissions only. No stored/transient LuckPerms nodes are modified. */
public final class StaffPermissions {
    private static final ThreadLocal<Integer> RAW=ThreadLocal.withInitial(()->0);
    private final CombatService service;
    private final Set<UUID> online=ConcurrentHashMap.newKeySet(),operators=ConcurrentHashMap.newKeySet(),staff=ConcurrentHashMap.newKeySet();
    StaffPermissions(CombatService service){this.service=service;}
    public static <T>T raw(Supplier<T> action){int depth=RAW.get();RAW.set(depth+1);try{return action.get();}finally{RAW.set(depth);}}
    public static boolean rawQuery(){return RAW.get()>0;}
    public void join(ServerPlayer p){online.add(p.getUUID());refreshOperator(p);qualifies(p.getUUID(),null);}
    public void leave(ServerPlayer p){online.remove(p.getUUID());operators.remove(p.getUUID());staff.remove(p.getUUID());}
    public void refreshOperator(ServerPlayer p){if(service.server.getPlayerList().isOp(p.nameAndId()))operators.add(p.getUUID());else operators.remove(p.getUUID());}
    public boolean authorize(ServerPlayer p){return raw(()->CombatService.allowed(p.createCommandSourceStack(),service.config.staffModePermission,service.config.adminLevel));}
    private boolean qualifies(UUID id,QueryOptions options){
        if(operators.contains(id)){staff.add(id);return true;}
        if(staff.contains(id))return true;
        boolean result=raw(()->{var user=LuckPermsProvider.get().getUserManager().getUser(id);if(user==null)return false;var data=options==null?user.getCachedData().getPermissionData():user.getCachedData().getPermissionData(options);return data.checkPermission(service.config.staffModePermission).asBoolean()||data.checkPermission(service.config.adminPermission).asBoolean()||data.checkPermission(service.config.staffToolPermissionPrefix+"tools").asBoolean();});
        if(result)staff.add(id);return result;
    }
    public boolean restricted(UUID id,QueryOptions options){
        if(rawQuery()||id==null||!service.config.staffDefaultPermissionsWhenOff||!online.contains(id)||!qualifies(id,options))return false;
        if(!service.staffFlag(id))return true;
        return !raw(()->{var user=LuckPermsProvider.get().getUserManager().getUser(id);if(user==null)return operators.contains(id);var result=(options==null?user.getCachedData().getPermissionData():user.getCachedData().getPermissionData(options)).checkPermission(service.config.staffModePermission);return result==Tristate.TRUE||result==Tristate.UNDEFINED&&operators.contains(id);});
    }
    public static boolean restricted(ServerPlayer p){var s=KingdomsCombat.service;return s!=null&&s.permissions.restricted(p.getUUID(),null);}
    public static net.minecraft.server.permissions.PermissionSet defaultSet(ServerPlayer p){
        return new net.minecraft.server.permissions.LevelBasedPermissionSet(){
            public net.minecraft.server.permissions.PermissionLevel level(){return net.minecraft.server.permissions.LevelBasedPermissionSet.ALL.level();}
            public boolean hasPermission(net.minecraft.server.permissions.Permission permission){
                if(permission instanceof net.minecraft.server.permissions.Permission.Atom atom){
                    var id=atom.id();var options=LuckPermsProvider.get().getContextManager().getQueryOptions(p);
                    var result=override(p.getUUID(),options,id.getNamespace()+"."+id.getPath().replace('/','.'));
                    if(result!=null&&result!=Tristate.UNDEFINED)return result.asBoolean();
                }
                return net.minecraft.server.permissions.LevelBasedPermissionSet.ALL.hasPermission(permission);
            }
        };
    }
    public static Tristate override(UUID owner,QueryOptions options,String node){
        var s=KingdomsCombat.service;if(s==null||!s.permissions.restricted(owner,options))return null;
        return raw(()->{var group=LuckPermsProvider.get().getGroupManager().getGroup(s.config.staffDefaultGroup);return group==null?Tristate.FALSE:group.getCachedData().getPermissionData(options).checkPermission(node);});
    }
    public static Map<String,Boolean> overrideMap(UUID owner,QueryOptions options){
        var s=KingdomsCombat.service;if(s==null||!s.permissions.restricted(owner,options))return null;
        return raw(()->{var group=LuckPermsProvider.get().getGroupManager().getGroup(s.config.staffDefaultGroup);return group==null?Map.of():group.getCachedData().getPermissionData(options).getPermissionMap();});
    }
    public void refreshCommands(ServerPlayer p){if(p.connection!=null)service.server.getCommands().sendCommands(p);}
}
