package com.celik.kingdomscombat;

import java.util.*;

public final class CombatConfig {
    public boolean enabled=true;
    public boolean eventAreasEnabled=true;
    public boolean blockEnderChestsInCombat=true;
    public boolean playtimeRoleEnabled=false;
    public String playtimeRoleId="";
    public double playtimeRoleHours=4;
    public int playtimeRoleCheckSeconds=300,playtimeRoleRetrySeconds=900,playtimeRoleQueueLimit=20;
    public int baltopCacheSeconds=30,baltopMaxEntries=10000,baltopPageSize=28;

    public boolean arenaMinecarts=true;
    public boolean arenaEnabled=true,arenaBuildAllowed=true,arenaVoiceRestricted=true,staffMuteVoice=true,arenaAutoNext=true,arenaFluidFlow=true,arenaSpectatorsHearStaff=true;
    public int arenaCountdownSeconds=10,arenaBetweenMatchesSeconds=15,arenaMaxFightSeconds=600;
    public int arenaMaxTeams=64,arenaMaxGates=512,arenaMaxChangedBlocks=4096,arenaMaxRegionVolume=4000000,arenaMaxRegionChunks=256;
    public int arenaEloStart=1000,arenaEloK=32,arenaEloScale=400;
    public List<String> arenaAllowedCommands=new ArrayList<>(List.of("event","tournament","combat","msg","tell","reply","r"));
    public int durationSeconds=20, warLockSeconds=20, airDropLockSeconds=20;
    public int pollTicks=5, actionBarIntervalTicks=20, saveIntervalSeconds=5;
    public boolean tagAttacker=true, tagVictim=true, tagBlockedHits=true;
    public boolean tagCreativePlayers=false, tagSpectators=false;
    public boolean killEndsCombat=true, killClearsAllOpponents=false;
    public boolean warRestrictions=true, airDropRestrictions=true, restrictWarTerritoryVisitors=false;
    public boolean airDropBlockIncomingTeleports=true;
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
    public boolean staffDefaultPermissionsWhenOff=true,staffActionBar=true;
    public String staffDefaultGroup="default",staffToolPermissionPrefix="kingdomscombat.staff.";
    public int staffReasonMaxLength=512,staffNotesLimit=100,staffTeleportTimeoutSeconds=15;
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
    public List<String> mutedCommands=new ArrayList<>(List.of("me","say","tell","msg","w","teammsg","tm","whisper","message","reply","r"));
    public void validate(){
        if(playtimeRoleId==null||!playtimeRoleId.isEmpty()&&!playtimeRoleId.matches("[0-9]{16,20}")||!Double.isFinite(playtimeRoleHours)||playtimeRoleHours<.01||playtimeRoleHours>20000||playtimeRoleCheckSeconds<30||playtimeRoleCheckSeconds>86400||playtimeRoleRetrySeconds<30||playtimeRoleRetrySeconds>86400||playtimeRoleQueueLimit<1||playtimeRoleQueueLimit>100||baltopCacheSeconds<5||baltopCacheSeconds>3600||baltopMaxEntries<10||baltopMaxEntries>100000||baltopPageSize<1||baltopPageSize>28)throw new IllegalArgumentException("Invalid playtime-role or balance leaderboard settings.");

        if(arenaCountdownSeconds<3||arenaCountdownSeconds>120||arenaBetweenMatchesSeconds<1||arenaBetweenMatchesSeconds>600||arenaMaxFightSeconds<10||arenaMaxFightSeconds>7200||arenaMaxTeams<2||arenaMaxTeams>256||arenaMaxGates<1||arenaMaxGates>4096||arenaMaxChangedBlocks<1||arenaMaxChangedBlocks>100000||arenaMaxRegionVolume<1||arenaMaxRegionVolume>16000000||arenaMaxRegionChunks<1||arenaMaxRegionChunks>1024||arenaEloStart<0||arenaEloStart>100000||arenaEloK<1||arenaEloK>1000||arenaEloScale<1||arenaEloScale>10000)throw new IllegalArgumentException("Invalid arena settings.");
        if(arenaAllowedCommands==null||arenaAllowedCommands.size()>128||arenaAllowedCommands.stream().anyMatch(v->v==null||v.isBlank()||v.length()>128))throw new IllegalArgumentException("Invalid arena command list.");
        if(mutedCommands==null||mutedCommands.size()>512||mutedCommands.stream().anyMatch(s->s==null||s.isBlank()||s.length()>256))throw new IllegalArgumentException("Invalid muted command list.");
        if(staffToolPermissionPrefix==null||!staffToolPermissionPrefix.matches("[a-z0-9_.-]{1,100}\\."))throw new IllegalArgumentException("Invalid staff tool permission prefix.");
        if(staffDefaultGroup==null||!staffDefaultGroup.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid staff default group.");
        if(staffReasonMaxLength<1||staffReasonMaxLength>2048||staffNotesLimit<1||staffNotesLimit>1000||staffTeleportTimeoutSeconds<1||staffTeleportTimeoutSeconds>120)throw new IllegalArgumentException("Invalid moderation limits.");
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
