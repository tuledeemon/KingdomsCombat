package com.celik.kingdomscombat;
import eu.pb4.placeholders.api.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import com.celik.kingdoms.Ui;
import java.util.function.Function;

public final class CombatPlaceholders {
    private CombatPlaceholders(){}
    private static TagLedger.Status status(ServerPlayer p){return KingdomsCombat.service.status(p);}
    private static <T>T query(ServerPlayer p,Function<ServerPlayer,T> fn,T fallback){var s=KingdomsCombat.service;if(s==null||s.stopping)return fallback;return s.server.isSameThread()?fn.apply(p):s.server.submit(()->fn.apply(p)).join();}
    private static void value(String key,Function<ServerPlayer,String> fn){Placeholders.registerServer(Identifier.parse("kingdomscombat:"+key),(context,arg)->PlaceholderResult.value(context.hasServerPlayer()?query(context.serverPlayer(),fn,""):""));}
    public static void register(){
        value("tagged",p->Boolean.toString(status(p).active()));
        value("combat_tagged",p->Boolean.toString(status(p).combatMillis()>0));
        value("remaining",p->Integer.toString(status(p).seconds()));
        value("remaining_formatted",p->Ui.clock(status(p).millis()));
        value("combat_remaining",p->Long.toString((status(p).combatMillis()+999)/1000));
        value("war_remaining",p->Long.toString((status(p).warMillis()+999)/1000));
        value("airdrop_remaining",p->Long.toString((status(p).airDropMillis()+999)/1000));
        value("reasons",p->KingdomsCombat.service.reasons(status(p)));
        value("opponent_count",p->Integer.toString(status(p).opponents().size()));
        value("opponents",p->Ui.caps(String.join(", ",status(p).opponents().stream().map(id->{var other=p.level().getServer().getPlayerList().getPlayer(id);return other==null?id.toString():other.getName().getString();}).sorted().toList())));
        value("can_teleport",p->Boolean.toString(KingdomsCombat.service.allowTeleport(p,false)));
        value("logout_punished",p->Boolean.toString(KingdomsCombat.service.logoutPunished(p)));
        value("staffmode",p->Boolean.toString(KingdomsCombat.service.staffMode(p)));
        value("frozen",p->Boolean.toString(KingdomsCombat.service.staff.frozen(p.getUUID())));
        value("muted",p->Boolean.toString(KingdomsCombat.service.staff.muted(p.getUUID())));
        value("event_active",p->Boolean.toString(KingdomsCombat.service.events.active()));
        value("event_name",p->Ui.caps(KingdomsCombat.service.events.name()));
        value("event_remaining",p->Long.toString((KingdomsCombat.service.events.remainingMillis()+999)/1000));
        value("at_war",p->Boolean.toString(KingdomsBridge.war(p,KingdomsCombat.service.now(),KingdomsCombat.service.config)));
        value("airdrop_nearby",p->Boolean.toString(KingdomsBridge.airDrop(p,KingdomsCombat.service.now(),KingdomsCombat.service.config)));
        Placeholders.registerServer(Identifier.parse("kingdomscombat:tag"),(context,arg)->context.hasServerPlayer()?PlaceholderResult.value(query(context.serverPlayer(),p->KingdomsCombat.service.component(p),net.minecraft.network.chat.Component.empty())):PlaceholderResult.value(""));
    }
}
