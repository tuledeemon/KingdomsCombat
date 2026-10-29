package com.celik.kingdomscombat;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerUtilitiesTest {
    @Test void fourHoursUsesRecordedTicksAndExactThreshold(){assertFalse(PlaytimeRoles.qualifies(287999,4));assertTrue(PlaytimeRoles.qualifies(288000,4));assertTrue(PlaytimeRoles.qualifies(400000,4));assertFalse(PlaytimeRoles.qualifies(-1,4));}
    @Test void fractionalHoursWork(){assertFalse(PlaytimeRoles.qualifies(35999,.5));assertTrue(PlaytimeRoles.qualifies(36000,.5));}
    @Test void balanceRankingIncludesOfflineIdentifiersAndUsesLongOrdering(){UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();var rows=BalanceTop.rank(Map.of(a,500L,b,Long.MAX_VALUE,c,0L),10);assertEquals(List.of(b,a,c),rows.stream().map(BalanceTop.Row::player).toList());}
    @Test void balanceLimitAndTiesAreStable(){UUID a=new UUID(0,1),b=new UUID(0,2);assertEquals(a,BalanceTop.rank(Map.of(b,10L,a,10L),1).getFirst().player());}
    @Test void negativeBalancesAreNotRanked(){assertTrue(BalanceTop.rank(Map.of(UUID.randomUUID(),-5L),10).isEmpty());}
    @Test void newDefaultsProtectStorageWithoutEnablingUnconfiguredDiscordWrites(){var c=CombatStore.JSON.fromJson("{}",CombatConfig.class);c.validate();assertTrue(c.blockEnderChestsInCombat);assertFalse(c.playtimeRoleEnabled);assertEquals(4,c.playtimeRoleHours);assertEquals(28,c.baltopPageSize);}
    @Test void malformedRoleAndUnboundedSettingsAreRejected(){var c=new CombatConfig();c.playtimeRoleId="not-a-role";assertThrows(IllegalArgumentException.class,c::validate);c=new CombatConfig();c.playtimeRoleHours=Double.POSITIVE_INFINITY;assertThrows(IllegalArgumentException.class,c::validate);c=new CombatConfig();c.baltopPageSize=29;assertThrows(IllegalArgumentException.class,c::validate);}
}
