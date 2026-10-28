package com.celik.kingdomscombat;

import java.util.*;

/** Bounded observations, not verdicts about installed client software. */
public final class FlagRules {
    public record Click(double cps,double deviation,double burstFraction,boolean tickAligned) {}
    public static Click clicks(List<Long> nanos){
        if(nanos.size()<3)return new Click(0,0,1,true);
        double sum=0,squares=0;int bursts=0,aligned=0;
        for(int i=1;i<nanos.size();i++){double ms=(nanos.get(i)-nanos.get(i-1))/1e6;if(ms<=0)return new Click(0,0,1,true);sum+=ms;squares+=ms*ms;if(ms<5)bursts++;if(Math.abs(ms/50-Math.rint(ms/50))<.04)aligned++;}
        int n=nanos.size()-1;double mean=sum/n;
        return new Click(1000/mean,Math.sqrt(Math.max(0,squares/n-mean*mean)),(double)bursts/n,aligned>=n*.9);
    }
    public static boolean slowTiming(List<Long> nanos,CombatConfig c){Click s=clicks(nanos);return nanos.size()>=c.antiCheatSlowSamples+1&&s.cps()>=c.antiCheatSlowMinCps&&s.cps()<=c.antiCheatSlowMaxCps&&s.deviation()<=c.antiCheatSlowStdMs&&s.burstFraction()<=.1;}
    public record Step(long token,boolean moved,boolean turned,int actions) {}
    public static int repeatedCycle(List<Step> steps,int minimum,int maximum,int repeats,double match){
        int size=steps.size();for(int period=minimum;period<=Math.min(maximum,size/repeats);period++){
            int start=size-period*repeats,bad=0,comparisons=period*(repeats-1),moved=0,turns=0,actions=0;Set<Long> variety=new HashSet<>();
            for(int i=size-period;i<size;i++){Step s=steps.get(i);variety.add(s.token);if(s.moved)moved++;if(s.turned)turns++;actions+=s.actions;}
            if(variety.size()<6||moved<period*.3||turns<4||actions<8)continue;
            int allowance=(int)Math.floor(comparisons*(1-match));
            for(int i=start;i<size-period&&bad<=allowance;i++)if(steps.get(i).token!=steps.get(i+period).token)bad++;
            if(bad<=allowance)return period;
        }return 0;
    }
    public static double score(long time,long now,int hours){return 10*Math.max(0,1-Math.max(0,now-time)/(hours*3600000.0));}
    private FlagRules(){}
}
