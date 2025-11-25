package net.bored.config;

import net.bored.common.QuirkRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.TreeMap;

public class PlusUltraConfig {
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "plusultra.toml");

    private static PlusUltraConfig INSTANCE;

    // --- Config Options ---
    public boolean disableQuirkDestruction = false;
    public boolean limitUniqueQuirks = true; // For OFA/AFO specifically
    public boolean uniqueQuirks = false; // New: For ALL high-tier quirks

    // --- Hardcore Options ---
    public boolean hardcoreMode = false;
    public int lives = 1;
    public boolean quirkReset = false;
    public boolean awakeningReset = false;
    public boolean levelReset = false;
    public boolean pointsReset = false;
    public boolean statsReset = false;
    public boolean expReset = false;

    // --- Mob Spawn Options ---
    public boolean mobsCanSpawnWithQuirks = true;
    public double mobQuirkChance = 0.05; // 5% chance by default

    // --- Villager Spawn Options ---
    public boolean villagersCanSpawnWithQuirks = true;
    public double villagerQuirkChance = 0.20; // 20% chance by default

    // Using TreeMap to keep quirks sorted alphabetically in the file
    public Map<String, Boolean> enabledQuirks = new TreeMap<>();

    // New: Map to specifically ban quirks from appearing on mobs (true = banned)
    public Map<String, Boolean> mobBannedQuirks = new TreeMap<>();

    public PlusUltraConfig() {
    }

    public static PlusUltraConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        INSTANCE = new PlusUltraConfig();

        if (CONFIG_FILE.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE))) {
                String line;
                String currentSection = "";

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    if (line.startsWith("[") && line.endsWith("]")) {
                        currentSection = line;
                        continue;
                    }

                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();

                        if (key.startsWith("\"") && key.endsWith("\"")) {
                            key = key.substring(1, key.length() - 1);
                        }

                        // General
                        if (key.equals("disableQuirkDestruction")) {
                            INSTANCE.disableQuirkDestruction = Boolean.parseBoolean(value);
                        } else if (key.equals("limitUniqueQuirks")) {
                            INSTANCE.limitUniqueQuirks = Boolean.parseBoolean(value);
                        } else if (key.equals("uniqueQuirks")) {
                            INSTANCE.uniqueQuirks = Boolean.parseBoolean(value);
                        }
                        // Hardcore
                        else if (key.equals("hardcoreMode")) {
                            INSTANCE.hardcoreMode = Boolean.parseBoolean(value);
                        } else if (key.equals("lives")) {
                            try { INSTANCE.lives = Integer.parseInt(value); } catch (NumberFormatException e) { INSTANCE.lives = 1; }
                        } else if (key.equals("quirkReset")) {
                            INSTANCE.quirkReset = Boolean.parseBoolean(value);
                        } else if (key.equals("awakeningReset")) {
                            INSTANCE.awakeningReset = Boolean.parseBoolean(value);
                        } else if (key.equals("levelReset")) {
                            INSTANCE.levelReset = Boolean.parseBoolean(value);
                        } else if (key.equals("pointsReset")) {
                            INSTANCE.pointsReset = Boolean.parseBoolean(value);
                        } else if (key.equals("statsReset")) {
                            INSTANCE.statsReset = Boolean.parseBoolean(value);
                        } else if (key.equals("expReset")) {
                            INSTANCE.expReset = Boolean.parseBoolean(value);
                        }
                        // Mobs
                        else if (key.equals("mobsCanSpawnWithQuirks")) {
                            INSTANCE.mobsCanSpawnWithQuirks = Boolean.parseBoolean(value);
                        } else if (key.equals("mobQuirkChance")) {
                            try {
                                INSTANCE.mobQuirkChance = Double.parseDouble(value);
                            } catch (NumberFormatException e) {
                                INSTANCE.mobQuirkChance = 0.05;
                            }
                        } else if (key.equals("villagersCanSpawnWithQuirks")) {
                            INSTANCE.villagersCanSpawnWithQuirks = Boolean.parseBoolean(value);
                        } else if (key.equals("villagerQuirkChance")) {
                            try {
                                INSTANCE.villagerQuirkChance = Double.parseDouble(value);
                            } catch (NumberFormatException e) {
                                INSTANCE.villagerQuirkChance = 0.20;
                            }
                        } else {
                            if ("[MobBlacklist]".equals(currentSection)) {
                                INSTANCE.mobBannedQuirks.put(key, Boolean.parseBoolean(value));
                            } else {
                                INSTANCE.enabledQuirks.put(key, Boolean.parseBoolean(value));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void populateQuirkDefaults() {
        boolean changed = false;

        for (Identifier id : QuirkRegistry.getKeys()) {
            String key = id.toString();
            if (key.equals("plusultra:quirk_bestowal")) continue;

            if (!enabledQuirks.containsKey(key)) {
                enabledQuirks.put(key, true);
                changed = true;
            }

            if (!mobBannedQuirks.containsKey(key)) {
                // Default ban OFA, AFO, and Quirkless from mobs if not already set
                boolean shouldBan = key.equals("plusultra:one_for_all") ||
                        key.equals("plusultra:all_for_one") ||
                        key.equals("plusultra:quirkless");
                mobBannedQuirks.put(key, shouldBan);
                changed = true;
            }
        }

        if (changed || !CONFIG_FILE.exists()) {
            save();
        }
    }

    public static void save() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            writer.println("# ==========================================");
            writer.println("#         Plus Ultra Mod Configuration");
            writer.println("# ==========================================");
            writer.println("");

            writer.println("[General]");
            writer.println("# If true, quirks like Stockpile will not break blocks.");
            writer.println("disableQuirkDestruction = " + INSTANCE.disableQuirkDestruction);
            writer.println("# If true, One For All and All For One can only be held by one player per world.");
            writer.println("limitUniqueQuirks = " + INSTANCE.limitUniqueQuirks);
            writer.println("# If true, ALL quirks (except lower tier ones like Regen) can only be held by one entity per world.");
            writer.println("uniqueQuirks = " + INSTANCE.uniqueQuirks);
            writer.println("");

            writer.println("[Hardcore]");
            writer.println("# Enable hardcore reset mechanics on death.");
            writer.println("hardcoreMode = " + INSTANCE.hardcoreMode);
            writer.println("# Number of deaths before data reset logic triggers.");
            writer.println("lives = " + INSTANCE.lives);
            writer.println("# Reset quirks on full death.");
            writer.println("quirkReset = " + INSTANCE.quirkReset);
            writer.println("# Un-awaken quirks on full death.");
            writer.println("awakeningReset = " + INSTANCE.awakeningReset);
            writer.println("# Reset Level (and thus Exp, Points, Stats) on full death.");
            writer.println("levelReset = " + INSTANCE.levelReset);
            writer.println("# Reset Stat Points on full death (ignored if levelReset is true).");
            writer.println("pointsReset = " + INSTANCE.pointsReset);
            writer.println("# Reset Attribute Stats (STR, END, etc) on full death (ignored if levelReset is true).");
            writer.println("statsReset = " + INSTANCE.statsReset);
            writer.println("# Reset Experience on full death (ignored if levelReset is true).");
            writer.println("expReset = " + INSTANCE.expReset);
            writer.println("");

            writer.println("[MobSpawning]");
            writer.println("# If true, hostile monsters can naturally spawn with quirks.");
            writer.println("mobsCanSpawnWithQuirks = " + INSTANCE.mobsCanSpawnWithQuirks);
            writer.println("# The chance (0.0 to 1.0) for a hostile mob to spawn with a quirk. 0.05 = 5%.");
            writer.println("mobQuirkChance = " + INSTANCE.mobQuirkChance);
            writer.println("# If true, villagers can naturally spawn with quirks.");
            writer.println("villagersCanSpawnWithQuirks = " + INSTANCE.villagersCanSpawnWithQuirks);
            writer.println("# The chance (0.0 to 1.0) for a villager to spawn with a quirk. 0.20 = 20%.");
            writer.println("villagerQuirkChance = " + INSTANCE.villagerQuirkChance);
            writer.println("");

            writer.println("[Quirks]");
            writer.println("# Set to 'false' to disable specific quirks globally.");
            for (Map.Entry<String, Boolean> entry : INSTANCE.enabledQuirks.entrySet()) {
                String key = "\"" + entry.getKey() + "\"";
                writer.println(key + " = " + entry.getValue());
            }

            writer.println("");
            writer.println("[MobBlacklist]");
            writer.println("# Set to 'true' to PREVENT mobs from spawning with this quirk.");
            for (Map.Entry<String, Boolean> entry : INSTANCE.mobBannedQuirks.entrySet()) {
                String key = "\"" + entry.getKey() + "\"";
                writer.println(key + " = " + entry.getValue());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isQuirkEnabled(String quirkId) {
        if ("plusultra:quirk_bestowal".equals(quirkId)) {
            return enabledQuirks.getOrDefault("plusultra:one_for_all", true);
        }
        return enabledQuirks.getOrDefault(quirkId, true);
    }

    public boolean isQuirkBannedForMobs(String quirkId) {
        if ("plusultra:quirk_bestowal".equals(quirkId)) return true;
        return mobBannedQuirks.getOrDefault(quirkId, false);
    }
}