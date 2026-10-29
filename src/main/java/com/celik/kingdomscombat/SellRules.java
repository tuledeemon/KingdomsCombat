package com.celik.kingdomscombat;

import java.util.*;

/** Per-item supply pressure. Every item in a batch uses the next marginal price. */
public final class SellRules {
    public static final class Policy {
        public double floorMultiplier=.25, volumeForFloor=16384, recoveryPerHour=4096;
        public void validate(){if(!Double.isFinite(floorMultiplier)||floorMultiplier<=0||floorMultiplier>1||!Double.isFinite(volumeForFloor)||volumeForFloor<1||volumeForFloor>1e9||!Double.isFinite(recoveryPerHour)||recoveryPerHour<0||recoveryPerHour>1e9)throw new IllegalArgumentException("Invalid sell price policy.");}
    }
    public static final class Config {
        public boolean enabled=true, blockInCombat=true;
        public Policy defaults=new Policy();
        public Map<String,Policy> items=new HashMap<>();
        public void validate(){if(defaults==null||items==null)throw new IllegalArgumentException("Invalid sell market settings.");defaults.validate();items.forEach((id,p)->{if(id==null||p==null)throw new IllegalArgumentException("Invalid item policy.");p.validate();});}
        public Policy policy(String key){return items.getOrDefault(key,defaults);}
    }
    public static final class Supply {public double volume;public long time;}
    public record Quote(long total,Supply supply){}
    public static double volume(Supply supply,Policy policy,long now){return supply==null?0:Math.max(0,supply.volume-Math.max(0,(double)now-supply.time)*policy.recoveryPerHour/3_600_000);}
    public static long unit(long base,double volume,Policy p){if(base<1)throw new IllegalArgumentException("Item is not sellable.");return Math.max(1,(long)Math.ceil(base*Math.max(p.floorMultiplier,1-Math.min(volume/p.volumeForFloor,1)*(1-p.floorMultiplier))-1e-9));}
    public static Quote quote(long base,int count,Supply supply,Policy policy,long now){
        if(count<0||count>100000)throw new IllegalArgumentException("Invalid sale amount.");
        double volume=volume(supply,policy,now);long total=0;
        for(int i=0;i<count;i++)total=Math.addExact(total,unit(base,volume+i,policy));
        Supply next=new Supply();next.volume=Math.min(policy.volumeForFloor,volume+count);next.time=supply==null?now:Math.max(now,supply.time);
        return new Quote(total,next);
    }
    private SellRules(){}
}
