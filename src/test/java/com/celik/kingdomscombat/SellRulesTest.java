package com.celik.kingdomscombat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SellRulesTest {
    private SellRules.Policy policy(){var p=new SellRules.Policy();p.volumeForFloor=100;p.recoveryPerHour=100;return p;}
    @Test void volumeLowersPriceAndNeverCrossesFloor(){var p=policy();assertEquals(100,SellRules.unit(100,0,p));assertEquals(25,SellRules.unit(100,10000,p));assertTrue(SellRules.unit(100,50,p)<100);}
    @Test void cheapItemsAlwaysPayAtLeastOne(){assertEquals(1,SellRules.unit(1,100000,policy()));}
    @Test void splittingSalesDoesNotIncreaseRevenue(){var p=policy();var full=SellRules.quote(100,180,null,p,1000);var a=SellRules.quote(100,37,null,p,1000);var b=SellRules.quote(100,143,a.supply(),p,1000);assertEquals(full.total(),a.total()+b.total());assertEquals(full.supply().volume,b.supply().volume);}
    @Test void offlineTimeRecoversSupplyWithoutBackgroundScans(){var p=policy();var sold=SellRules.quote(100,100,null,p,1000);assertEquals(50,SellRules.volume(sold.supply(),p,1801000));assertEquals(0,SellRules.volume(sold.supply(),p,3601000));}
    @Test void clockGoingBackDoesNotRemovePressure(){var p=policy();var sold=SellRules.quote(100,80,null,p,2000);assertEquals(80,SellRules.volume(sold.supply(),p,1000));}
    @Test void invalidPoliciesAndCountsAreRejected(){var p=policy();p.recoveryPerHour=Double.NaN;assertThrows(IllegalArgumentException.class,p::validate);assertThrows(IllegalArgumentException.class,()->SellRules.quote(100,-1,null,policy(),0));assertThrows(IllegalArgumentException.class,()->SellRules.unit(0,0,policy()));}
    @Test void emptySaleDoesNotCreateMoney(){assertEquals(0,SellRules.quote(100,0,null,policy(),0).total());}
    @Test void perItemPoliciesRemainIndependent(){var c=new SellRules.Config();var p=policy();c.items.put("minecraft:diamond",p);c.validate();assertSame(p,c.policy("minecraft:diamond"));assertSame(c.defaults,c.policy("minecraft:oak_leaves"));}
}
