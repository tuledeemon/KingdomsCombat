package com.celik.kingdomscombat;

import java.util.*;

/** Immutable server-thread snapshot: UDP voice callbacks never read Minecraft worlds or mutable saves. */
public final class VoicePolicy {
    public record Snapshot(Set<UUID> restricted,Map<UUID,Long> mutedUntil,Set<UUID> audibleStaff){public Snapshot(Set<UUID> restricted,Map<UUID,Long> mutedUntil){this(restricted,mutedUntil,Set.of());}}
    public static volatile Snapshot snapshot=new Snapshot(Set.of(),Map.of());
    public static void refresh(CombatService service){
        Set<UUID> restricted=new HashSet<>(),audibleStaff=new HashSet<>();Map<UUID,Long> mutes=new HashMap<>();
        if(service.config.staffMuteVoice)for(var e:service.staff.state.mutes.entrySet())if(e.getValue().until>service.now())mutes.put(e.getKey(),e.getValue().until);
        for(var p:service.server.getPlayerList().getPlayers()){boolean staff=service.arenas.admin(p)||CombatService.allowed(p.createCommandSourceStack(),service.config.eventHostPermission,service.config.adminLevel);if(staff&&service.config.arenaSpectatorsHearStaff)audibleStaff.add(p.getUUID());if(service.config.arenaVoiceRestricted&&service.arenas.inside(p.level(),p.blockPosition())&&!service.arenas.current(p.getUUID())&&!staff)restricted.add(p.getUUID());}
        snapshot=new Snapshot(Set.copyOf(restricted),Map.copyOf(mutes),Set.copyOf(audibleStaff));
    }
    public static boolean muted(UUID id){Snapshot state=snapshot;return state.restricted.contains(id)||state.mutedUntil.getOrDefault(id,0L)>System.currentTimeMillis();}
    public static void reset(){snapshot=new Snapshot(Set.of(),Map.of());}
    private VoicePolicy(){}
}
