package com.celik.kingdomscombat;

import java.util.*;

public final class CombatConfig {
    public boolean enabled=true;
    public int durationSeconds=20, warLockSeconds=20, airDropLockSeconds=20;
    public int pollTicks=5, actionBarIntervalTicks=20, saveIntervalSeconds=5;
    public boolean tagAttacker=true, tagVictim=true, tagBlockedHits=true;
    public boolean tagCreativePlayers=false, tagSpectators=false;
    public boolean killEndsCombat=true, killClearsAllOpponents=false;
    public boolean warRestrictions=true, airDropRestrictions=true, restrictWarTerritoryVisitors=false;
    public double airDropRadius=100, airDropVerticalTolerance=-1;
    public boolean horizontalAirDropRadius=true;
    public boolean resetOnEnderPearl=true, resetOnWindCharge=true, resetOnSpearLunge=true;
    public boolean killOnCombatLogout=true, killOnWarLogout=false, killOnAirDropLogout=false;
    public boolean punishDuringShutdown=false;
    public boolean actionBar=true, appendToKingdomsActionBar=true;
    public boolean chatNotifications=true, titles=true, sounds=true, broadcastLogoutDeaths=false;
    public String prefix="combat", combatColor="red", warColor="gold", airDropColor="aqua", errorColor="red";
    public String actionBarTemplate="{reasons} › {seconds}s", separator=" | ";
    public String blockedMessage="Teleport commands are blocked: {reasons} — {seconds}s remaining.";
    public String taggedMessage="Combat tagged for {seconds}s. Logging out while combat tagged kills you.";
    public String safeMessage="Combat ended. You can log out safely.";
    public String warMessage="War teleport restriction active. The timer counts down after war ends.";
    public String airDropMessage="AirDrop teleport restriction active. Leave the area to start the countdown.";
    public String logoutMessage="{player} died after logging out during combat.";
    public String tagSound="minecraft:block.note_block.pling", endSound="minecraft:block.note_block.chime";
    public float soundVolume=.6f;
    public int adminLevel=2;
    public String staffModePermission="kingdomscombat.staffmode",eventHostPermission="kingdomscombat.eventhost",adminPermission="kingdomscombat.admin";
    public int eventMaxSeconds=86400;
    public int eventTeleportTimeoutSeconds=15;
    public int eventDescriptionMaxLength=2048;
    public String eventColor="light_purple",eventBossBarColor="purple";
    public String eventBarTemplate="event • {name} • {time}",eventStartMessage="{name} has started. Duration: {time}.",eventEndMessage="{name} has ended.",eventStopMessage="{name} was stopped.";
    public boolean eventTitles=true,eventSounds=true,eventChat=true;
    public boolean operatorBypass=false;
    public String bypassPermission="kingdomscombat.bypass";
    public boolean respectBypassPermission=true, huskHomesTeleportGuard=true;
    public List<String> blockedCommands=new ArrayList<>(List.of("home","phome","publichome","warp","spawn","tp","teleport","tpo","tphere","tpoffline","tpall","tpa","tpahere","tpaall","tpaccept","tpyes","rtp","back","hub","lobby","server"));
    public void validate(){
        if(durationSeconds<1||durationSeconds>86400||warLockSeconds<1||warLockSeconds>86400||airDropLockSeconds<1||airDropLockSeconds>86400)throw new IllegalArgumentException("Tag durations must be 1–86400 seconds.");
        if(pollTicks<1||pollTicks>20||actionBarIntervalTicks<1||actionBarIntervalTicks>100||saveIntervalSeconds<1||saveIntervalSeconds>300)throw new IllegalArgumentException("Invalid update/save interval.");
        if(!Double.isFinite(airDropRadius)||airDropRadius<1||airDropRadius>10000||!Double.isFinite(airDropVerticalTolerance)||airDropVerticalTolerance< -1||airDropVerticalTolerance>10000)throw new IllegalArgumentException("Invalid AirDrop radius/height tolerance.");
        if(adminLevel<1||adminLevel>4||!Float.isFinite(soundVolume)||soundVolume<0||soundVolume>4)throw new IllegalArgumentException("Invalid admin level or volume.");
        if(eventMaxSeconds<1||eventMaxSeconds>604800)throw new IllegalArgumentException("Event maximum must be 1–604800 seconds.");
        if(eventTeleportTimeoutSeconds<1||eventTeleportTimeoutSeconds>120)throw new IllegalArgumentException("Event teleport timeout must be 1–120 seconds.");
        if(eventDescriptionMaxLength<1||eventDescriptionMaxLength>4096)throw new IllegalArgumentException("Event description length must be 1–4096 characters.");
        if(!Set.of("pink","blue","red","green","yellow","purple","white").contains(eventBossBarColor))throw new IllegalArgumentException("Invalid event boss bar color.");
        if(blockedCommands==null||blockedCommands.size()>512||blockedCommands.stream().anyMatch(s->s==null||s.isBlank()||s.length()>256))throw new IllegalArgumentException("Invalid blocked command list.");
        for(var field:CombatConfig.class.getFields())try{if(field.getType()==String.class&&(field.get(this)==null||((String)field.get(this)).length()>1024))throw new IllegalArgumentException("Invalid text setting: "+field.getName());}catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
}
