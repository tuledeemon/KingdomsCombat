package com.celik.kingdomscombat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Pattern;

public final class HostedEventRules {
    private static final Pattern PART=Pattern.compile("(\\d+(?:\\.\\d+)?)([smhd]?)");
    private HostedEventRules(){}
    public static int seconds(String input,int maximum){
        var matcher=PART.matcher(input.toLowerCase(Locale.ROOT));
        BigDecimal total=BigDecimal.ZERO;
        int end=0;
        while(matcher.find()){
            if(matcher.start()!=end)throw new IllegalArgumentException("Use a duration such as 30s, 10m or 1h30m.");
            int multiplier=switch(matcher.group(2)){case "m"->60;case "h"->3600;case "d"->86400;default->1;};
            total=total.add(new BigDecimal(matcher.group(1)).multiply(BigDecimal.valueOf(multiplier)));
            end=matcher.end();
        }
        if(end!=input.length()||total.signum()<=0||total.compareTo(BigDecimal.valueOf(maximum))>0)throw new IllegalArgumentException("Event duration must be between 1s and "+maximum+"s.");
        return total.setScale(0,RoundingMode.CEILING).intValueExact();
    }
}
