package net.bored.api;

import net.bored.common.QuirkRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuirkSystem {

    public enum AbilityType { INSTANT, HOLD, TOGGLE }

    public static abstract class Quirk {
        private final Identifier id;
        private final List<Ability> abilities = new ArrayList<>();

        public Quirk(Identifier id) {
            this.id = id;
            registerAbilities();
        }
        public abstract void registerAbilities();

        public abstract void onUpdate(LivingEntity entity, QuirkData data, QuirkData.QuirkInstance instance);

        public void onRemove(LivingEntity entity, QuirkData data) {}

        // MODIFIED: Now accepts QuirkData to calculate Meta scaling
        public float getPowerMultiplier(int count, QuirkData data) {
            float base = 1.0f + ((count - 1) * 0.5f);
            // Scaling: 2% power per Meta point
            float metaMult = 1.0f + (data.meta * 0.02f);
            return base * metaMult;
        }

        public int getIconColor() { return 0xFF00E5FF; } // Default Cyan

        public void addAbility(Ability ability) { this.abilities.add(ability); }
        public List<Ability> getAbilities() { return abilities; }
        public Identifier getId() { return id; }
    }

    public static abstract class Ability {
        private final String name;
        private final AbilityType type;
        private int cooldownMax;
        private int currentCooldown;
        private final int requiredLevel;
        private final double staminaCost;

        public Ability(String name, AbilityType type, int cooldownMax, int requiredLevel, double staminaCost) {
            this.name = name;
            this.type = type;
            this.cooldownMax = cooldownMax;
            this.requiredLevel = requiredLevel;
            this.staminaCost = staminaCost;
        }

        public abstract void onActivate(LivingEntity entity, QuirkData data, QuirkData.QuirkInstance instance);

        public void onHoldTick(LivingEntity entity, QuirkData data, QuirkData.QuirkInstance instance) {}
        public void onRelease(LivingEntity entity, QuirkData data, QuirkData.QuirkInstance instance) {}

        public void tick() { if (currentCooldown > 0) currentCooldown--; }
        public boolean isReady() { return currentCooldown <= 0; }
        public AbilityType getType() { return type; }

        public boolean canUse(QuirkData data) {
            return data.level >= requiredLevel && isReady() && data.currentStamina >= staminaCost;
        }

        public void triggerCooldown() { this.currentCooldown = cooldownMax; }
        public String getName() { return name; }
        public int getRequiredLevel() { return requiredLevel; }
        public double getCost() { return staminaCost; }
        public int getCurrentCooldown() { return currentCooldown; }
        public void setCurrentCooldown(int cd) { this.currentCooldown = cd; }
    }

    public static class QuirkData {
        public int strength = 0, endurance = 0, speed = 0, staminaMax = 0, meta = 0;
        public int statPoints = 1; // Start with 1 point
        public int level = 1, experience = 0;
        public double currentStamina = 100;

        private final List<QuirkInstance> quirks = new ArrayList<>();
        private int selectedQuirkIndex = 0;
        private int selectedAbilityIndex = 0;
        public int aiActionCooldown = 0;
        public Map<String, String> runtimeTags = new HashMap<>();

        public static class QuirkInstance {
            public String quirkId;
            public int count = 1;
            public boolean innate = false;
            public boolean isPassivesActive = true;
            public boolean awakened = false;
            // NEW: Persistent data storage for quirks (like Stockpile percentage)
            public NbtCompound persistentData = new NbtCompound();

            public QuirkInstance(String id) { this.quirkId = id; }
        }

        public void addQuirk(String id, boolean isInnate) {
            for (QuirkInstance q : quirks) {
                if (q.quirkId.equals(id)) {
                    q.count++;
                    return;
                }
            }
            QuirkInstance newInstance = new QuirkInstance(id);
            newInstance.innate = isInnate;
            quirks.add(newInstance);
        }

        public void addQuirk(String id) {
            addQuirk(id, false);
        }

        public boolean removeQuirk(String id) {
            for (int i = 0; i < quirks.size(); i++) {
                QuirkInstance q = quirks.get(i);
                if (q.quirkId.equals(id)) {
                    if (q.count > 1) {
                        q.count--;
                        return false;
                    } else {
                        quirks.remove(i);
                        return true;
                    }
                }
            }
            return true;
        }

        public void tick(LivingEntity entity) {
            double regen = 0.5 + (staminaMax * 0.1);
            if (currentStamina < getMaxStaminaPool()) currentStamina = Math.min(currentStamina + regen, getMaxStaminaPool());
            if (aiActionCooldown > 0) aiActionCooldown--;

            for (QuirkInstance instance : quirks) {
                Quirk quirk = QuirkRegistry.get(new Identifier(instance.quirkId));
                if (quirk != null) {
                    for (Ability ability : quirk.getAbilities()) {
                        ability.tick();
                    }
                    if (instance.isPassivesActive) {
                        quirk.onUpdate(entity, this, instance);
                    }
                }
            }
        }

        public double getMaxStaminaPool() { return 100 + (staminaMax * 5); }
        public float getMaxXp() { return level * 100f; }

        public void addXp(int amount) {
            if (level >= 100) return; // Max Level 100 cap

            this.experience += amount;
            while (this.experience >= getMaxXp() && level < 100) {
                this.experience -= getMaxXp();
                this.level++;
                this.statPoints++; // Gain 1 point per level
            }
        }

        public void writeToNbt(NbtCompound nbt) {
            nbt.putInt("Strength", strength);
            nbt.putInt("Endurance", endurance);
            nbt.putInt("Speed", speed);
            nbt.putInt("StaminaMax", staminaMax);
            nbt.putInt("Meta", meta);
            nbt.putInt("StatPoints", statPoints);

            nbt.putInt("Level", level);
            nbt.putInt("XP", experience);
            nbt.putDouble("StaminaCur", currentStamina);

            nbt.putInt("SelectedQ", selectedQuirkIndex);
            nbt.putInt("SelectedA", selectedAbilityIndex);

            NbtList quirkList = new NbtList();
            for(QuirkInstance qi : quirks) {
                NbtCompound qTag = new NbtCompound();
                qTag.putString("ID", qi.quirkId);
                qTag.putInt("Count", qi.count);
                qTag.putBoolean("Innate", qi.innate);
                qTag.putBoolean("Awakened", qi.awakened);
                // Save Persistent Data
                qTag.put("Data", qi.persistentData);

                NbtCompound cdTag = new NbtCompound();
                Quirk q = QuirkRegistry.get(new Identifier(qi.quirkId));
                if (q != null) {
                    for(int i=0; i<q.getAbilities().size(); i++) {
                        cdTag.putInt("A"+i, q.getAbilities().get(i).getCurrentCooldown());
                    }
                }
                qTag.put("Cooldowns", cdTag);
                quirkList.add(qTag);
            }
            nbt.put("Quirks", quirkList);
        }

        public void readFromNbt(NbtCompound nbt) {
            if (nbt.contains("Strength")) strength = nbt.getInt("Strength");
            if (nbt.contains("Endurance")) endurance = nbt.getInt("Endurance");
            if (nbt.contains("Speed")) speed = nbt.getInt("Speed");
            if (nbt.contains("StaminaMax")) staminaMax = nbt.getInt("StaminaMax");
            if (nbt.contains("Meta")) meta = nbt.getInt("Meta");
            if (nbt.contains("StatPoints")) statPoints = nbt.getInt("StatPoints");

            if (nbt.contains("Level")) level = nbt.getInt("Level");
            if (nbt.contains("XP")) experience = nbt.getInt("XP");
            if (nbt.contains("StaminaCur")) currentStamina = nbt.getDouble("StaminaCur");

            selectedQuirkIndex = nbt.getInt("SelectedQ");
            selectedAbilityIndex = nbt.getInt("SelectedA");

            quirks.clear();
            NbtList quirkList = nbt.getList("Quirks", NbtElement.COMPOUND_TYPE);
            for(int i = 0; i < quirkList.size(); i++) {
                NbtCompound qTag = quirkList.getCompound(i);
                String id = qTag.getString("ID");
                QuirkInstance qi = new QuirkInstance(id);
                if (qTag.contains("Count")) qi.count = qTag.getInt("Count");
                if (qTag.contains("Innate")) qi.innate = qTag.getBoolean("Innate");
                if (qTag.contains("Data")) qi.persistentData = qTag.getCompound("Data");
                qi.awakened = qTag.getBoolean("Awakened");
                quirks.add(qi);

                if (qTag.contains("Cooldowns")) {
                    NbtCompound cdTag = qTag.getCompound("Cooldowns");
                    Quirk q = QuirkRegistry.get(new Identifier(id));
                    if (q != null) {
                        for(int j=0; j<q.getAbilities().size(); j++) {
                            if(cdTag.contains("A"+j)) {
                                q.getAbilities().get(j).setCurrentCooldown(cdTag.getInt("A"+j));
                            }
                        }
                    }
                }
            }
        }

        public List<QuirkInstance> getQuirks() { return quirks; }
        public int getSelectedQuirkIndex() { return selectedQuirkIndex; }
        public void setSelectedQuirkIndex(int index) { this.selectedQuirkIndex = index; }
        public int getSelectedAbilityIndex() { return selectedAbilityIndex; }

        public void cycleAbility(int direction, int maxAbilities) {
            if (maxAbilities <= 0) return;
            selectedAbilityIndex = (selectedAbilityIndex + direction) % maxAbilities;
            if (selectedAbilityIndex < 0) selectedAbilityIndex += maxAbilities;
        }
    }
}