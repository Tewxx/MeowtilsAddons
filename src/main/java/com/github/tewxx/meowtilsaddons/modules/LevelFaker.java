package com.github.tewxx.meowtilsaddons.modules;

import wtf.tatp.meowtils.gui.Module;
import wtf.tatp.meowtils.gui.values.NumberValue;
import wtf.tatp.meowtils.gui.values.BooleanValue;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import wtf.tatp.meowtils.config.cfg;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.util.ChatComponentText;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScorePlayerTeam;
import java.util.Collection;
import java.util.ArrayList;

public class LevelFaker extends Module {
    public static volatile LevelFaker INSTANCE;
    private final NumberValue networkLevel;
    private final BooleanValue networkLevelToggle;
    private final NumberValue bedwarsLevel;

    private final BooleanValue bedwarsScoreboardToggle;
    private final BooleanValue bedwarsChatToggle;
    private final BooleanValue bedwarsXpToggle;
    private boolean faking;
    private int savedLevel;
    private int tick;

    private net.minecraft.world.World lastWorldRef;
    private boolean inGameByChat;
    private long lastWorldChangeAtMs;

    private boolean appliedNetworkLast;
    private int prevLevelBeforeNetwork;
    private float prevProgBeforeNetwork;

    private boolean appliedBedwarsLast;
    private int prevLevelBeforeBedwars;
    private float prevProgBeforeBedwars;

    private int lastSyncedNetworkLevel = Integer.MIN_VALUE;
    private int lastSyncedBedwarsLevel = Integer.MIN_VALUE;


    private TreeMap<Integer, String> starTemplates;

    private static final Pattern BRACKETED_NUMBER = Pattern.compile("\\[(\\d{1,4})(?:[^0-9\\]]+)?\\]?");

    public LevelFaker() {
        super("LevelFaker", "levelFakerKey", "levelFaker", Module.Category.Advanced);
        INSTANCE = this;
        try { this.tooltip("Hypixel Network Level Faker & Bedwars Star Faker\n§5/bedwarslevel <value>\n§5/networklevel <value>§r"); } catch (Throwable ignored) {}

        this.networkLevelToggle = new BooleanValue("Network Level", "networkLevelEnabledBool");
        this.addBoolean(networkLevelToggle);

        this.networkLevel = new NumberValue("Network Level", 1.0, 500.0, 1.0, null, "networkLevel", Integer.TYPE);
        this.addValue(networkLevel);

        this.bedwarsLevel = new NumberValue("Bedwars Level", 1.0, 5000.0, 1.0, null, "bedwarsLevel", Integer.TYPE);
        this.addValue(bedwarsLevel);

        this.bedwarsScoreboardToggle = new BooleanValue("Bedwars Level - Scoreboard", "bedwarsScoreboardEnabledBool");
        this.addBoolean(bedwarsScoreboardToggle);
        this.bedwarsChatToggle = new BooleanValue("Bedwars Level - Chat", "bedwarsChatEnabledBool");
        this.addBoolean(bedwarsChatToggle);
        this.bedwarsXpToggle = new BooleanValue("Bedwars Level - XP", "bedwarsXpEnabledBool");
        this.addBoolean(bedwarsXpToggle);
    }

    public void onDisable() {
        try {
            if (appliedNetworkLast) {
                int restoreLevel = prevLevelBeforeNetwork;
                if (restoreLevel <= 0) {
                    Integer fromSb = extractNetworkLevelFromScoreboard();
                    if (fromSb != null) restoreLevel = fromSb;
                }
                if (this.mc != null && this.mc.thePlayer != null && restoreLevel > 0) {
                    this.mc.thePlayer.experienceLevel = restoreLevel;
                    this.mc.thePlayer.experience = prevProgBeforeNetwork;
                }
                appliedNetworkLast = false;
            }
            if (appliedBedwarsLast && this.mc != null && this.mc.thePlayer != null) {
                this.mc.thePlayer.experienceLevel = prevLevelBeforeBedwars;
                this.mc.thePlayer.experience = prevProgBeforeBedwars;
                appliedBedwarsLast = false;
            }
        } catch (Throwable ignored) {}
    }


    @Override
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (this.mc == null || this.mc.thePlayer == null) return;
        if (event.phase != TickEvent.Phase.END) return;
        tick++;
        try {
            int cfgNl = getCfgInt("networkLevel", 1);
            if (cfgNl != lastSyncedNetworkLevel) {
                applyNumberValueReflect("networkLevel", cfgNl);
                lastSyncedNetworkLevel = cfgNl;
            }
            int cfgBw = getCfgInt("bedwarsLevel", 1);
            if (cfgBw != lastSyncedBedwarsLevel) {
                applyNumberValueReflect("bedwarsLevel", cfgBw);
                lastSyncedBedwarsLevel = cfgBw;
            }
        } catch (Throwable ignored) {}
        if (!this.getState()) {
            try {
                if (appliedNetworkLast) {
                    int restoreLevel = prevLevelBeforeNetwork;
                    if (restoreLevel <= 0) {
                        Integer fromSb = extractNetworkLevelFromScoreboard();
                        if (fromSb != null) restoreLevel = fromSb;
                    }
                    if (restoreLevel > 0) {
                        this.mc.thePlayer.experienceLevel = restoreLevel;
                        this.mc.thePlayer.experience = prevProgBeforeNetwork;
                    }
                    appliedNetworkLast = false;
                }
                if (appliedBedwarsLast) {
                    this.mc.thePlayer.experienceLevel = prevLevelBeforeBedwars;
                    this.mc.thePlayer.experience = prevProgBeforeBedwars;
                    appliedBedwarsLast = false;
                }
            } catch (Throwable ignored) {}
            return;
        }

        if ((tick % 20) == 0) {
            try {
                if (this.mc.theWorld != null) {
                    Scoreboard sb = this.mc.theWorld.getScoreboard();
                    if (sb != null) {
                        ScoreObjective obj = sb.getObjectiveInDisplaySlot(1);
                        if (obj != null) {
                            Collection<Score> col = sb.getSortedScores(obj);
                            ArrayList<String> lines = new ArrayList<>();
                            for (Score sc : col) {
                                if (sc == null) continue;
                                String name = sc.getPlayerName();
                                if (name == null || name.startsWith("#")) continue;
                                ScorePlayerTeam team = sb.getPlayersTeam(name);
                                String formatted = ScorePlayerTeam.formatPlayerName(team, name);
                                lines.add(formatted);
                            }
                            int count = Math.min(lines.size(), 15);
                            System.out.println("[LevelFaker] (tick) Sidebar lines=" + count + ", objective='" + obj.getDisplayName() + "'");
                            for (int i = 0; i < count; i++) {
                                int idx = lines.size() - count + i;
                                String raw = lines.get(idx);
                                String bare = stripColors(raw);
                                System.out.println("[LevelFaker] (tick) sb[" + i + "]: raw='" + raw + "' bare='" + bare + "'");
                            }
                            boolean youDetected = false;
                            for (String raw : lines) {
                                String bare = stripColors(raw);
                                if (bare != null && bare.toUpperCase(java.util.Locale.ROOT).contains("YOU")) { youDetected = true; break; }
                            }
                            if (youDetected && (isBedWarsObjective(obj) || hasBedwarsCompassNamed()) && getCfgBool("bedwarsXpEnabledBool", true)) {
                                inGameByChat = true;
                                if (!appliedBedwarsLast) {
                                    prevLevelBeforeBedwars = this.mc.thePlayer.experienceLevel;
                                    prevProgBeforeBedwars = this.mc.thePlayer.experience;
                                    appliedBedwarsLast = true;
                                }
                                int bw = getCfgInt("bedwarsLevel", 1);
                                if (bw < 0) bw = 0; if (bw > 5000) bw = 5000;
                                float prog = this.mc.thePlayer.experience;
                                this.mc.thePlayer.experienceLevel = bw;
                                this.mc.thePlayer.experience = prog;
                            }
                            boolean bwCond = getCfgBool("bedwarsXpEnabledBool", true) && (isBedWarsObjective(obj) || hasBedwarsCompassNamed()) && youDetected;
                            if (appliedBedwarsLast && !bwCond) {
                                this.mc.thePlayer.experienceLevel = prevLevelBeforeBedwars;
                                this.mc.thePlayer.experience = prevProgBeforeBedwars;
                                appliedBedwarsLast = false;
                            }
                        } else {
                            System.out.println("[LevelFaker] (tick) No sidebar objective");
                        }
                    } else {
                        System.out.println("[LevelFaker] (tick) No scoreboard");
                    }
                }
            } catch (Throwable ignored) {}
        }


        if (this.mc.theWorld != lastWorldRef) {
            lastWorldRef = this.mc.theWorld;
            inGameByChat = false;
            lastWorldChangeAtMs = System.currentTimeMillis();
            applyCompassXpRule();
        }

        if (this.getState()) {
            applyCompassXpRule();
        }
    }

    @SubscribeEvent
    public void onClientChatReceived(ClientChatReceivedEvent event) {
        try {
            if (this.mc == null || this.mc.thePlayer == null) return;
            if (event == null || event.message == null) return;
            if (!this.getState()) return;

            String self = this.mc.thePlayer.getName();
            String formatted = event.message.getFormattedText();
            if (formatted == null) return;
            String bare = stripColors(formatted).toLowerCase(java.util.Locale.ROOT);
            if (bare.contains("has joined (")) {
                long now = System.currentTimeMillis();
                if (now - lastWorldChangeAtMs <= 1000L) {
                    this.mc.thePlayer.experience = 0.0f;
                }
            }
            ensureStarTemplates();
            int colonPos = formatted.indexOf(':');
            if (colonPos < 0) return;
            Matcher m = BRACKETED_NUMBER.matcher(formatted);
            int repStart = -1;
            int repEnd = -1;
            while (m.find()) {
                int s = m.start();
                int e = m.end();
                if (e > colonPos) break;
                String tail = formatted.substring(e, colonPos);
                String tailBare = stripColors(tail);
                if (tailBare == null) continue;
                String upperTail = tailBare.toUpperCase(java.util.Locale.ROOT);
                String upperSelf = self == null ? null : self.toUpperCase(java.util.Locale.ROOT);
                String selfOrYou = (upperSelf == null ? "YOU" : "YOU|" + java.util.regex.Pattern.quote(upperSelf));
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("^\\s*(\\[[^\\]]+\\]\\s*)*(" + selfOrYou + ")\\b");
                java.util.regex.Matcher mm = p.matcher(upperTail);
                if (mm.find()) {
                    repStart = s;
                    repEnd = e;
                    break;
                }
            }
            if (!getCfgBool("bedwarsChatEnabledBool", true)) return;

            int level = getCfgInt("bedwarsLevel", 1);
            if (level < 0) level = 0; if (level > 5000) level = 5000;
            String tag = buildStarTag(level);
            if (tag == null) return;

            if (repStart >= 0) {
                StringBuilder sb = new StringBuilder();
                sb.append(formatted, 0, repStart);
                sb.append(tag);
                sb.append(formatted.substring(repEnd > repStart ? repEnd : repStart));
                event.message = new ChatComponentText(sb.toString());
            }
            return;
        } catch (Throwable ignored) {}
    }

    private static int mapBareIndexToFormatted(String formatted, int bareIndex) {
        if (formatted == null) return -1;
        int fIdx = 0;
        int bIdx = 0;
        while (fIdx < formatted.length()) {
            char c = formatted.charAt(fIdx);
            if (c == '§' && fIdx + 1 < formatted.length()) {
                fIdx += 2;
                continue;
            }
            if (bIdx == bareIndex) return fIdx;
            fIdx++;
            bIdx++;
        }
        if (bIdx == bareIndex) return fIdx;
        return -1;
    }

    private boolean hasLobbyCompassNamed() {
        try {
            if (this.mc == null || this.mc.thePlayer == null || this.mc.thePlayer.inventory == null) return false;
            net.minecraft.entity.player.InventoryPlayer inv = this.mc.thePlayer.inventory;
            for (int i = 0; i < inv.mainInventory.length; i++) {
                ItemStack is = inv.mainInventory[i];
                if (is == null) continue;
                if (is.getItem() != Items.compass) continue;
                String name = is.getDisplayName();
                if (name == null) continue;
                String n = stripColors(name).trim();
                if (n.equalsIgnoreCase("Game Menu (Right Click)")) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean hasBedwarsCompassNamed() {
        try {
            if (this.mc == null || this.mc.thePlayer == null || this.mc.thePlayer.inventory == null) return false;
            net.minecraft.entity.player.InventoryPlayer inv = this.mc.thePlayer.inventory;
            for (int i = 0; i < inv.mainInventory.length; i++) {
                ItemStack is = inv.mainInventory[i];
                if (is == null) continue;
                if (is.getItem() != Items.compass) continue;
                String name = is.getDisplayName();
                if (name == null) continue;
                String n = stripColors(name).trim();
                if (n.equalsIgnoreCase("Compass (Right Click)")) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void applyCompassXpRule() {
        try {
            if (!this.getState() || this.mc == null || this.mc.thePlayer == null) return;
            boolean hasLobby = hasLobbyCompassNamed();
            boolean netToggle = getCfgBool("networkLevelEnabledBool", true);
            if (hasLobby && netToggle) {
                if (!appliedNetworkLast) {
                    prevLevelBeforeNetwork = this.mc.thePlayer.experienceLevel;
                    prevProgBeforeNetwork = this.mc.thePlayer.experience;
                    appliedNetworkLast = true;
                }
                int nl = getCfgInt("networkLevel", 1);
                if (nl < 0) nl = 0; if (nl > 5000) nl = 5000;
                float prog = this.mc.thePlayer.experience;
                this.mc.thePlayer.experienceLevel = nl;
                this.mc.thePlayer.experience = prog;
                return;
            }
            if (appliedNetworkLast && (!hasLobby || !netToggle)) {
                int restoreLevel = prevLevelBeforeNetwork;
                if (restoreLevel <= 0) {
                    Integer fromSb = extractNetworkLevelFromScoreboard();
                    if (fromSb != null) restoreLevel = fromSb;
                }
                if (restoreLevel > 0) {
                    this.mc.thePlayer.experienceLevel = restoreLevel;
                    this.mc.thePlayer.experience = prevProgBeforeNetwork;
                }
                appliedNetworkLast = false;
            }
            if (hasLobby && !netToggle) {
                Integer realNl = extractNetworkLevelFromScoreboard();
                if (realNl != null) {
                    float prog = this.mc.thePlayer.experience;
                    this.mc.thePlayer.experienceLevel = realNl;
                    this.mc.thePlayer.experience = prog;
                }
            }
            if (hasBedwarsCompassNamed() && getCfgBool("bedwarsXpEnabledBool", true)) {
                if (!appliedBedwarsLast) {
                    prevLevelBeforeBedwars = this.mc.thePlayer.experienceLevel;
                    prevProgBeforeBedwars = this.mc.thePlayer.experience;
                    appliedBedwarsLast = true;
                }
                int bw = getCfgInt("bedwarsLevel", 1);
                if (bw < 0) bw = 0; if (bw > 5000) bw = 5000;
                float prog = this.mc.thePlayer.experience;
                this.mc.thePlayer.experienceLevel = bw;
                this.mc.thePlayer.experience = prog;
                return;
            }
            if (appliedBedwarsLast && (!getCfgBool("bedwarsXpEnabledBool", true) || !hasBedwarsCompassNamed())) {
                this.mc.thePlayer.experienceLevel = prevLevelBeforeBedwars;
                this.mc.thePlayer.experience = prevProgBeforeBedwars;
                appliedBedwarsLast = false;
            }
        } catch (Throwable ignored) {}
    }

    private static String stripColors(String s) {
        if (s == null) return null;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) { i++; continue; }
            out.append(c);
        }
        return out.toString();
    }

    public static void setBedwarsLevelExternal(int value) {
        try {
            if (value < 1) value = 1; if (value > 5000) value = 5000;
            java.lang.reflect.Field f = cfg.v.getClass().getField("bedwarsLevel");
            f.setInt(cfg.v, value);
        } catch (Throwable ignored) {}
        if (INSTANCE != null) INSTANCE.applyNumberValueReflect("bedwarsLevel", value);
    }

    public static void setNetworkLevelExternal(int value) {
        try {
            if (value < 1) value = 1; if (value > 500) value = 500;
            java.lang.reflect.Field f = cfg.v.getClass().getField("networkLevel");
            f.setInt(cfg.v, value);
        } catch (Throwable ignored) {}
        if (INSTANCE != null) INSTANCE.applyNumberValueReflect("networkLevel", value);
    }

    private void applyNumberValueReflect(String fieldName, int value) {
        try {
            java.lang.reflect.Field fld = this.getClass().getDeclaredField(fieldName);
            fld.setAccessible(true);
            Object numberValue = fld.get(this);
            if (numberValue == null) return;
            Class<?> nvCls = numberValue.getClass();
            String[] methodNames = new String[] {"setValue", "set", "setInt", "setDouble", "setNumber"};
            for (String mn : methodNames) {
                try {
                    java.lang.reflect.Method m;
                    try { m = nvCls.getMethod(mn, int.class); m.invoke(numberValue, value); return; } catch (Throwable ignored) {}
                    try { m = nvCls.getMethod(mn, Integer.class); m.invoke(numberValue, Integer.valueOf(value)); return; } catch (Throwable ignored) {}
                    try { m = nvCls.getMethod(mn, double.class); m.invoke(numberValue, (double) value); return; } catch (Throwable ignored) {}
                    try { m = nvCls.getMethod(mn, Double.class); m.invoke(numberValue, Double.valueOf((double) value)); return; } catch (Throwable ignored) {}
                    try { m = nvCls.getMethod(mn, Number.class); m.invoke(numberValue, Integer.valueOf(value)); return; } catch (Throwable ignored) {}
                } catch (Throwable ignored) {}
            }

            String[] fieldNames = new String[] {"value", "val", "currentValue", "number", "num"};
            for (String fn : fieldNames) {
                try {
                    java.lang.reflect.Field vf = nvCls.getDeclaredField(fn);
                    vf.setAccessible(true);
                    Class<?> tp = vf.getType();
                    if (tp == int.class) { vf.setInt(numberValue, value); return; }
                    if (tp == Integer.class) { vf.set(numberValue, Integer.valueOf(value)); return; }
                    if (tp == double.class) { vf.setDouble(numberValue, (double) value); return; }
                    if (tp == Double.class) { vf.set(numberValue, Double.valueOf((double) value)); return; }
                    if (Number.class.isAssignableFrom(tp)) { vf.set(numberValue, Integer.valueOf(value)); return; }
                } catch (Throwable ignored) {}
            }

            try { nvCls.getMethod("onChange").invoke(numberValue); } catch (Throwable ignored) {}
            try { nvCls.getMethod("update").invoke(numberValue); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private Integer extractNetworkLevelFromScoreboard() {
        try {
            if (this.mc == null || this.mc.theWorld == null) return null;
            net.minecraft.scoreboard.Scoreboard sb = this.mc.theWorld.getScoreboard();
            if (sb == null) return null;
            net.minecraft.scoreboard.ScoreObjective obj = sb.getObjectiveInDisplaySlot(1);
            if (obj == null) return null;
            java.util.Collection<net.minecraft.scoreboard.Score> col = sb.getSortedScores(obj);
            for (net.minecraft.scoreboard.Score sc : col) {
                if (sc == null) continue;
                String name = sc.getPlayerName();
                if (name == null || name.startsWith("#")) continue;
                net.minecraft.scoreboard.ScorePlayerTeam team = sb.getPlayersTeam(name);
                String formatted = net.minecraft.scoreboard.ScorePlayerTeam.formatPlayerName(team, name);
                String bare = stripColors(formatted);
                if (bare == null) continue;
                String b = bare.trim().toLowerCase(java.util.Locale.ROOT);
                if (b.startsWith("level:")) {
                    String digits = bare.replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) {
                        try { return Integer.parseInt(digits); } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean isBedWarsObjective(ScoreObjective obj) {
        if (obj == null) return false;
        String t = stripStatic(obj.getDisplayName());
        if (t == null) return false;
        t = t.trim().toLowerCase(java.util.Locale.ROOT);
        return t.contains("bed wars") || t.contains("bedwars");
    }

    private static String stripStatic(String s) {
        if (s == null) return null;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) { i++; continue; }
            out.append(c);
        }
        return out.toString();
    }

    private void ensureStarTemplates() {
        if (starTemplates != null) return;
        starTemplates = new TreeMap<>();
        try {
            InputStream is = LevelFaker.class.getClassLoader().getResourceAsStream("meowtilsaddons_bedwars_stars.txt");
            if (is == null) return;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String k = line.substring(0, eq).trim();
                    String v = line.substring(eq + 1).trim();
                    try {
                        int key = Integer.parseInt(k);
                        starTemplates.put(key, v);
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private String buildStarTag(int level) {
        if (starTemplates == null || starTemplates.isEmpty()) return null;
        Map.Entry<Integer, String> floor = starTemplates.floorEntry(level - (level % 100));
        if (floor == null) floor = starTemplates.firstEntry();
        String template = floor.getValue();
        if (template == null) return null;

        int open = template.indexOf('[');
        int close = template.lastIndexOf(']');
        if (open < 0 || close < 0 || close <= open) return template;
        String prefix = template.substring(0, open + 1);
        String inside = template.substring(open + 1, close);
        String suffix = template.substring(close);

        int symIdx = lastSymbolIndex(inside);
        if (symIdx < 0) return template;
        String digitsColored = inside.substring(0, symIdx);
        String symbolAndColors = inside.substring(symIdx);

        String digits = String.valueOf(level);
        String rebuiltDigits = recolorDigits(digitsColored, digits);

        return prefix + rebuiltDigits + symbolAndColors + suffix;
    }

    private static int lastSymbolIndex(String s) {
        int idx = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '✫' || c == '✪' || c == '⚝' || c == '✥') { idx = i; break; }
        }
        return idx;
    }

    private static String recolorDigits(String digitsColored, String digits) {
        StringBuilder out = new StringBuilder();
        int d = 0;
        for (int i = 0; i < digitsColored.length(); ) {
            char c = digitsColored.charAt(i);
            if (c == '§' && i + 1 < digitsColored.length()) {
                out.append(c).append(digitsColored.charAt(i + 1));
                i += 2;
                if (i < digitsColored.length() && Character.isDigit(digitsColored.charAt(i))) {
                    char next = (d < digits.length()) ? digits.charAt(d++) : '0';
                    i += 1;
                    out.append(next);
                }
            } else {
                if (Character.isDigit(c)) {
                    char next = (d < digits.length()) ? digits.charAt(d++) : '0';
                    out.append(next);
                } else {
                    out.append(c);
                }
                i += 1;
            }
        }
        if (d < digits.length()) {
            char lastCode = 'f';
            for (int i = out.length() - 2; i >= 0; i--) {
                if (out.charAt(i) == '§') { lastCode = out.charAt(i + 1); break; }
            }
            while (d < digits.length()) {
                out.append('§').append(lastCode).append(digits.charAt(d++));
            }
        }
        return out.toString();
    }

    private void restoreLevel() {
        try { this.mc.thePlayer.experienceLevel = savedLevel; } catch (Throwable ignored) {}
        faking = false;
    }


    private static int getCfgInt(String field, int def) {
        try {
            java.lang.reflect.Field f = cfg.v.getClass().getField(field);
            Object val = f.get(cfg.v);
            if (val instanceof Number) return ((Number) val).intValue();
            if (val != null) return Integer.parseInt(String.valueOf(val));
            return def;
        } catch (Throwable t) { return def; }
    }

    private static boolean getCfgBool(String field, boolean def) {
        try {
            java.lang.reflect.Field f = cfg.v.getClass().getField(field);
            Object val = f.get(cfg.v);
            if (val instanceof Boolean) return (Boolean) val;
            if (val != null) return Boolean.parseBoolean(String.valueOf(val));
            return def;
        } catch (Throwable t) { return def; }
    }
}
