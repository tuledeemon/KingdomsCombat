package com.celik.kingdomscombat;

import java.util.*;
import java.util.regex.Pattern;

public final class CommandRules {
    private CommandRules(){}
    public static String normalize(String command){String s=command.trim().toLowerCase(Locale.ROOT);while(s.startsWith("/"))s=s.substring(1);return s.replaceAll("\\s+"," ");}
    public static boolean matches(String command,List<String> patterns){
        String line=normalize(command);
        for(String raw:patterns){String rule=normalize(raw),candidate=line;
            if(!rule.contains(":")){int end=candidate.indexOf(' ');if(end<0)end=candidate.length();int colon=candidate.indexOf(':');if(colon>=0&&colon<end)candidate=candidate.substring(colon+1);}
            StringBuilder regex=new StringBuilder("^");for(String piece:rule.split("\\*",-1)){if(regex.length()>1)regex.append(".*");regex.append(Pattern.quote(piece));}regex.append("(?:$| .*)");
            if(candidate.matches(regex.toString()))return true;
        }
        return false;
    }
}
