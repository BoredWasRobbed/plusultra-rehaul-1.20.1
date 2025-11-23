package net.bored.common;

import net.bored.api.QuirkSystem;
import net.bored.common.quirks.AllForOneQuirk;
import net.bored.common.quirks.DecayQuirk;
import net.bored.common.quirks.OneForAllQuirk;
import net.bored.common.quirks.SuperRegenerationQuirk;
import net.bored.common.quirks.StockpileQuirk;
import net.minecraft.util.Identifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class QuirkRegistry {
    private static final Map<Identifier, QuirkSystem.Quirk> QUIRKS = new HashMap<>();

    public static void registerAll() {
        register(new AllForOneQuirk());
        register(new SuperRegenerationQuirk());
        register(new StockpileQuirk());
        register(new OneForAllQuirk());
        register(new DecayQuirk());
    }

    public static void register(QuirkSystem.Quirk quirk) {
        QUIRKS.put(quirk.getId(), quirk);
    }

    public static QuirkSystem.Quirk get(Identifier id) { return QUIRKS.get(id); }
    public static Set<Identifier> getKeys() { return QUIRKS.keySet(); }
}