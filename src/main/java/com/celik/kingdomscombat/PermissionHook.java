package com.celik.kingdomscombat;
import net.minecraft.server.level.ServerPlayer;
final class PermissionHook {
    static boolean bypass(ServerPlayer player,String permission){return me.lucko.fabric.api.permissions.v0.Permissions.check(player,permission,false);}
    static boolean allowed(net.minecraft.commands.CommandSourceStack source,String permission,boolean fallback){return me.lucko.fabric.api.permissions.v0.Permissions.check(source,permission,fallback);}
}
