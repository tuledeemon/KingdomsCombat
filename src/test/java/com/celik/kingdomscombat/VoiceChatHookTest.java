package com.celik.kingdomscombat;
import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.*;
import org.junit.jupiter.api.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class VoiceChatHookTest {
    @AfterEach void reset(){VoicePolicy.reset();}
    @SuppressWarnings("unchecked") private <T>T proxy(Class<T> type,Map<String,Object> values){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->values.get(m.getName()));}
    @SuppressWarnings("unchecked") private Map<Class<?>,Consumer<Object>> events(){Map<Class<?>,Consumer<Object>> events=new HashMap<>();new VoiceChatHook().registerEvents((EventRegistration)Proxy.newProxyInstance(EventRegistration.class.getClassLoader(),new Class[]{EventRegistration.class},(p,m,a)->{events.put((Class<?>)a[0],(Consumer<Object>)a[1]);return null;}));return events;}
    private VoicechatConnection connection(UUID id){return proxy(VoicechatConnection.class,Map.of("getPlayer",proxy(de.maxhenkel.voicechat.api.ServerPlayer.class,Map.of("getUuid",id))));}
    private boolean packet(Class<?> type,Consumer<Object> handler,UUID sender,UUID recipient){boolean[] cancelled={false};Object event=Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->switch(m.getName()){case "getSenderConnection"->sender==null?null:connection(sender);case "getReceiverConnection"->recipient==null?null:connection(recipient);case "isCancellable"->true;case "cancel"->{cancelled[0]=true;yield true;}default->null;});handler.accept(event);return cancelled[0];}
    @Test void mutedMicrophonePacketsAreCancelledIncludingGroupInput(){UUID muted=UUID.randomUUID(),normal=UUID.randomUUID();VoicePolicy.snapshot=new VoicePolicy.Snapshot(Set.of(),Map.of(muted,System.currentTimeMillis()+60000));var handlers=events();assertTrue(packet(MicrophonePacketEvent.class,handlers.get(MicrophonePacketEvent.class),muted,null));assertFalse(packet(MicrophonePacketEvent.class,handlers.get(MicrophonePacketEvent.class),normal,null));}
    @Test void arenaSpectatorsCannotSendOrReceiveVoicePackets(){UUID spectator=UUID.randomUUID(),fighter=UUID.randomUUID();VoicePolicy.snapshot=new VoicePolicy.Snapshot(Set.of(spectator),Map.of());var handlers=events();for(Class<?> type:List.of(EntitySoundPacketEvent.class,LocationalSoundPacketEvent.class,StaticSoundPacketEvent.class)){assertTrue(packet(type,handlers.get(type),fighter,spectator));assertTrue(packet(type,handlers.get(type),spectator,fighter));assertFalse(packet(type,handlers.get(type),fighter,fighter));}}
    @Test void arenaSpectatorsHearStaffButCannotSpeak(){UUID spectator=UUID.randomUUID(),staff=UUID.randomUUID(),fighter=UUID.randomUUID();VoicePolicy.snapshot=new VoicePolicy.Snapshot(Set.of(spectator),Map.of(),Set.of(staff));var handlers=events();for(Class<?> type:List.of(EntitySoundPacketEvent.class,LocationalSoundPacketEvent.class,StaticSoundPacketEvent.class)){assertFalse(packet(type,handlers.get(type),staff,spectator));assertTrue(packet(type,handlers.get(type),fighter,spectator));}assertTrue(packet(MicrophonePacketEvent.class,handlers.get(MicrophonePacketEvent.class),spectator,null));}
    @Test void anAudibleStaffMuteStillBlocksSpeech(){UUID spectator=UUID.randomUUID(),staff=UUID.randomUUID();VoicePolicy.snapshot=new VoicePolicy.Snapshot(Set.of(spectator),Map.of(staff,System.currentTimeMillis()+60000),Set.of(staff));var handlers=events();assertTrue(packet(EntitySoundPacketEvent.class,handlers.get(EntitySoundPacketEvent.class),staff,spectator));}
    @Test void expiredModerationMuteDoesNotCancelAudio(){UUID id=UUID.randomUUID();VoicePolicy.snapshot=new VoicePolicy.Snapshot(Set.of(),Map.of(id,System.currentTimeMillis()-1));assertFalse(VoicePolicy.muted(id));}
}
