package com.celik.kingdomscombat;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** Optional Discord Integration 3.2.1/JDA adapter; no credentials or libraries are bundled. */
final class PlaytimeRoleBridge implements PlaytimeRoles.Gateway {
    @Override public PlaytimeRoles.Result grant(UUID player,String roleId)throws Exception{
        Class<?> integration;try{integration=Class.forName("de.erdbeerbaerlp.dcintegration.common.DiscordIntegration");}catch(ClassNotFoundException missing){return PlaytimeRoles.Result.UNAVAILABLE;}
        Object instance=integration.getField("INSTANCE").get(null);if(instance==null)return PlaytimeRoles.Result.UNAVAILABLE;
        Object jda=integration.getMethod("getJDA").invoke(instance);if(jda==null)return PlaytimeRoles.Result.UNAVAILABLE;
        Class<?> links=Class.forName("de.erdbeerbaerlp.dcintegration.common.storage.linking.LinkManager");Object link=links.getMethod("getLink",String.class,UUID.class).invoke(null,null,player);if(link==null)return PlaytimeRoles.Result.UNLINKED;
        String discord=String.valueOf(link.getClass().getField("discordID").get(link));
        Class<?> jdaApi=Class.forName("net.dv8tion.jda.api.JDA"),guildApi=Class.forName("net.dv8tion.jda.api.entities.Guild"),roleApi=Class.forName("net.dv8tion.jda.api.entities.Role"),memberApi=Class.forName("net.dv8tion.jda.api.entities.Member"),userApi=Class.forName("net.dv8tion.jda.api.entities.UserSnowflake");
        Object role=null,guild=null;for(Object candidate:(List<?>)jdaApi.getMethod("getGuilds").invoke(jda)){role=guildApi.getMethod("getRoleById",String.class).invoke(candidate,roleId);if(role!=null){guild=candidate;break;}}
        if(role==null)throw new IllegalArgumentException("Configured Discord role was not found in the bot's guilds");
        if(Boolean.TRUE.equals(roleApi.getMethod("isManaged").invoke(role))||Boolean.TRUE.equals(roleApi.getMethod("isPublicRole").invoke(role)))throw new IllegalArgumentException("Choose an ordinary assignable Discord role");
        Object member=guildApi.getMethod("getMemberById",String.class).invoke(guild,discord);
        if(member==null)member=complete(guildApi.getMethod("retrieveMemberById",String.class).invoke(guild,discord));
        for(Object assigned:(List<?>)memberApi.getMethod("getRoles").invoke(member))if(roleId.equals(String.valueOf(roleApi.getMethod("getId").invoke(assigned))))return PlaytimeRoles.Result.ALREADY_PRESENT;
        Object request=guildApi.getMethod("addRoleToMember",userApi,roleApi).invoke(guild,member,role);complete(request);return PlaytimeRoles.Result.GRANTED;
    }
    private Object complete(Object action)throws Exception{Class<?> api=Class.forName("net.dv8tion.jda.api.requests.RestAction");Object timed=api.getMethod("timeout",long.class,TimeUnit.class).invoke(action,15L,TimeUnit.SECONDS);return api.getMethod("complete").invoke(timed);}
}
