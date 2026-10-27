package com.celik.kingdomscombat;

import java.util.*;

/** Immutable server-thread snapshot: UDP voice callbacks never read Minecraft worlds or mutable saves. */
public final class VoicePolicy {
    public record Snapshot(Set<UUID> restricted,Map<UUID,Long> mutedUntil){}
    public static volatile Snapshot snapshot=new Snapshot(Set.of(),Map.of());
    public static void refresh(CombatService service){
        Set<UUID> restricted=new HashSet<>();Map<UUID,Long> mutes=new HashMap<>();
        if(service.config.staffMuteVoice)for(var e:service.staff.state.mutes.entrySet())if(e.getValue().until>service.now())mutes.put(e.getKey(),e.getValue().until);
        if(service.config.arenaVoiceRestricted)for(var p:service.server.getPlayerList().getPlayers())if(service.arenas.inside(p.level(),p.blockPosition())&&!service.arenas.current(p.getUUID())&&!service.arenas.admin(p))restricted.add(p.getUUID());
        snapshot=new Snapshot(Set.copyOf(restricted),Map.copyOf(mutes));
    }
    public static boolean muted(UUID id){Snapshot state=snapshot;return state.restricted.contains(id)||state.mutedUntil.getOrDefault(id,0L)>System.currentTimeMillis();}
    public static void reset(){snapshot=new Snapshot(Set.of(),Map.of());}
    private VoicePolicy(){}
}
