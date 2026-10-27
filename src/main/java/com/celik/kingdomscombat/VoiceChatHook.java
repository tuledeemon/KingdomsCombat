package com.celik.kingdomscombat;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.*;

/** Optional Simple Voice Chat 2.6 API plugin. Nothing is bundled or required on vanilla clients. */
public final class VoiceChatHook implements VoicechatPlugin {
    @Override public String getPluginId(){return "kingdoms_combat";}
    @Override public void registerEvents(EventRegistration registration){
        registration.registerEvent(MicrophonePacketEvent.class,event->{var sender=event.getSenderConnection();if(sender!=null&&VoicePolicy.muted(sender.getPlayer().getUuid()))event.cancel();},1000);
        registration.registerEvent(EntitySoundPacketEvent.class,this::sound,1000);
        registration.registerEvent(LocationalSoundPacketEvent.class,this::sound,1000);
        registration.registerEvent(StaticSoundPacketEvent.class,this::sound,1000);
    }
    private void sound(PacketEvent<?> event){var sender=event.getSenderConnection();var receiver=event.getReceiverConnection();if(sender!=null&&VoicePolicy.muted(sender.getPlayer().getUuid())||receiver!=null&&VoicePolicy.snapshot.restricted().contains(receiver.getPlayer().getUuid()))event.cancel();}
}
