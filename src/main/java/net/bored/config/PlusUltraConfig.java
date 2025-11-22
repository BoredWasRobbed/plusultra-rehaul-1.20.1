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
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class PlusUltraConfig {
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "plusultra.toml");

    private static PlusUltraConfig INSTANCE;

    // --- Config Options ---
    public boolean disableQuirkDestruction = true;
    public boolean limitUniqueQuirks = true;
    // Using TreeMap to keep quirks sorted alphabetically in the file
    public Map<String, Boolean> enabledQuirks = new TreeMap<>();

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
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    // Skip comments and empty lines
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    // Skip section headers [Section]
                    if (line.startsWith("[") && line.endsWith("]")) continue;

                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();

                        // Remove quotes from keys if present (TOML requires quotes for colons)
                        if (key.startsWith("\"") && key.endsWith("\"")) {
                            key = key.substring(1, key.length() - 1);
                        }

                        if (key.equals("disableQuirkDestruction")) {
                            INSTANCE.disableQuirkDestruction = Boolean.parseBoolean(value);
                        } else if (key.equals("limitUniqueQuirks")) {
                            INSTANCE.limitUniqueQuirks = Boolean.parseBoolean(value);
                        } else {
                            // Assume it's a quirk
                            INSTANCE.enabledQuirks.put(key, Boolean.parseBoolean(value));
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

            // Skip Bestowal (Internal logic handles it)
            if (key.equals("plusultra:quirk_bestowal")) continue;

            if (!enabledQuirks.containsKey(key)) {
                enabledQuirks.put(key, true);
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
            writer.println("");
            writer.println("# If true, One For All and All For One can only be held by one player per world.");
            writer.println("limitUniqueQuirks = " + INSTANCE.limitUniqueQuirks);
            writer.println("");

            writer.println("[Quirks]");
            writer.println("# Set to 'false' to disable specific quirks.");
            writer.println("# Note: Disabling One For All also disables Quirk Bestowal.");

            for (Map.Entry<String, Boolean> entry : INSTANCE.enabledQuirks.entrySet()) {
                // In TOML, keys with colons must be quoted
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
}