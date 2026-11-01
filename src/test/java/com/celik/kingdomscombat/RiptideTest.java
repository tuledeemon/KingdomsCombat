package com.celik.kingdomscombat;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class RiptideTest {
    @Test void defaultCooldownIsDisabledAndRefreshEnabled(){var c=new CombatConfig();c.validate();assertEquals(0,c.riptideCooldownSeconds);assertTrue(c.resetOnRiptide);c.riptideCooldownSeconds=-1;assertThrows(IllegalArgumentException.class,c::validate);c.riptideCooldownSeconds=86401;assertThrows(IllegalArgumentException.class,c::validate);}
    @Test void legacyDataLoadsAndExpiredCooldownsPrune(){var d=CombatStore.JSON.fromJson("{\"schemaVersion\":1,\"tags\":{},\"logoutPenalties\":{}}",TagLedger.Data.class);var l=new TagLedger(d);UUID id=UUID.randomUUID();d.riptideCooldowns.put(id,100L);l.prune(99);assertTrue(d.riptideCooldowns.containsKey(id));l.prune(100);assertFalse(d.riptideCooldowns.containsKey(id));d.riptideCooldowns.put(id,-1L);assertThrows(IllegalStateException.class,()->TagLedger.validate(d));}
}
