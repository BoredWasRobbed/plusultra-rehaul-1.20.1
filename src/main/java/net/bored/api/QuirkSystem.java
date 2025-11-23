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

        public float getPowerMultiplier(int count, QuirkData data) {
            float base = 1.0f + ((count - 1) * 0.5f);
            float metaMult = 1.0f + (data.meta * 0.02f);
            return base * metaMult;
        }

        public int getIconColor() { return 0xFF00E5FF; }

        public void addAbility(Ability ability) { this.abilities.add(ability); }

        public List<Ability> getAbilities() { return abilities; }

        public List<Ability> getAbilities(QuirkData.QuirkInstance instance) {
            return abilities;
        }

        public Identifier getId() { return id; }
    }

    public static abstract class Ability {
        private final String name;
        private final AbilityType type;
        private int cooldownMax;
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

        public void tick(QuirkData.QuirkInstance instance) {
            int cd = getCurrentCooldown(instance);
            if (cd > 0) {
                instance.cooldowns.put(this.name, cd - 1);
            }
        }

        public boolean isReady(QuirkData.QuirkInstance instance) {
            return getCurrentCooldown(instance) <= 0;
        }

        public AbilityType getType() { return type; }

        public boolean canUse(QuirkData data, QuirkData.QuirkInstance instance) {
            if (instance.isLocked) return false;
            // Also check if hidden (which implies unuseable)
            if (isHidden(data, instance)) return false;
            return data.level >= requiredLevel && isReady(instance) && data.currentStamina >= staminaCost;
        }

        public void triggerCooldown(QuirkData.QuirkInstance instance) {
            instance.cooldowns.put(this.name, this.cooldownMax);
        }

        // Check if ability should be hidden from list/selection
        public boolean isHidden(QuirkData data, QuirkData.QuirkInstance instance) {
            // Default behavior: Hide if level requirement not met
            return data.level < this.requiredLevel;
        }

        // --- AI METHODS ---

        /**
         * Determines if an AI mob should attempt to use this ability.
         * @param user The mob using the ability.
         * @param target The mob's current target (can be null).
         * @param distanceSquared Distance to target squared (if target exists).
         * @param data The mob's QuirkData.
         * @param instance The specific QuirkInstance.
         * @return True if the AI should activate this ability.
         */
        public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkData data, QuirkData.QuirkInstance instance) {
            return false;
        }

        /**
         * Called when the AI activates the ability. By default, delegates to onActivate.
         * Override if AI needs specific aiming logic (e.g., set look direction).
         */
        public void onAIUse(LivingEntity user, LivingEntity target, QuirkData data, QuirkData.QuirkInstance instance) {
            this.onActivate(user, data, instance);
        }

        public String getName() { return name; }
        public int getRequiredLevel() { return requiredLevel; }
        public double getCost() { return staminaCost; }

        public int getCurrentCooldown(QuirkData.QuirkInstance instance) {
            return instance.cooldowns.getOrDefault(this.name, 0);
        }

        public void setCurrentCooldown(QuirkData.QuirkInstance instance, int cd) {
            instance.cooldowns.put(this.name, cd);
        }
    }

    public static class QuirkData {
        public int strength = 0, endurance = 0, speed = 0, staminaMax = 0, meta = 0;
        public int statPoints = 1;
        public int level = 1, experience = 0;
        public double currentStamina = 100;
        public boolean cooldownsDisabled = false;

        private final List<QuirkInstance> quirks = new ArrayList<>();
        private int selectedQuirkIndex = 0;
        private int selectedAbilityIndex = 0;
        public int aiActionCooldown = 0;
        public Map<String, String> runtimeTags = new HashMap<>();
        public NbtCompound persistentData = new NbtCompound();

        public static class QuirkInstance {
            public String quirkId;
            public int count = 1;
            public boolean innate = false;
            public boolean isPassivesActive = true;
            public boolean awakened = false;
            public boolean isLocked = false;
            public NbtCompound persistentData = new NbtCompound();
            public Map<String, Integer> cooldowns = new HashMap<>();

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
                    List<Ability> instanceAbilities = quirk.getAbilities(instance);
                    for (Ability ability : instanceAbilities) {
                        ability.tick(instance);
                        if (this.cooldownsDisabled) {
                            ability.setCurrentCooldown(instance, 0);
                        }
                    }
                    if (instance.isPassivesActive && !instance.isLocked) {
                        quirk.onUpdate(entity, this, instance);
                    }
                }
            }
        }

        public double getMaxStaminaPool() { return 100 + (staminaMax * 5); }
        public float getMaxXp() { return level * 100f; }

        public void addXp(int amount) {
            if (level >= 100) return;
            this.experience += amount;
            while (this.experience >= getMaxXp() && level < 100) {
                this.experience -= getMaxXp();
                this.level++;
                this.statPoints++;
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
            nbt.putBoolean("CooldownsDisabled", cooldownsDisabled);
            nbt.putInt("SelectedQ", selectedQuirkIndex);
            nbt.putInt("SelectedA", selectedAbilityIndex);
            nbt.put("PlayerData", persistentData);

            NbtList quirkList = new NbtList();
            for(QuirkInstance qi : quirks) {
                NbtCompound qTag = new NbtCompound();
                qTag.putString("ID", qi.quirkId);
                qTag.putInt("Count", qi.count);
                qTag.putBoolean("Innate", qi.innate);
                qTag.putBoolean("Awakened", qi.awakened);
                qTag.putBoolean("Locked", qi.isLocked);
                qTag.put("Data", qi.persistentData);

                NbtCompound cdTag = new NbtCompound();
                for(Map.Entry<String, Integer> entry : qi.cooldowns.entrySet()) {
                    cdTag.putInt(entry.getKey(), entry.getValue());
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
            if (nbt.contains("CooldownsDisabled")) cooldownsDisabled = nbt.getBoolean("CooldownsDisabled");
            if (nbt.contains("PlayerData")) persistentData = nbt.getCompound("PlayerData");

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
                if (qTag.contains("Locked")) qi.isLocked = qTag.getBoolean("Locked");
                qi.awakened = qTag.getBoolean("Awakened");

                if (qTag.contains("Cooldowns")) {
                    NbtCompound cdTag = qTag.getCompound("Cooldowns");
                    for(String key : cdTag.getKeys()) {
                        qi.cooldowns.put(key, cdTag.getInt(key));
                    }
                }

                quirks.add(qi);
            }
        }

        public List<QuirkInstance> getQuirks() { return quirks; }
        public int getSelectedQuirkIndex() { return selectedQuirkIndex; }

        public void setSelectedQuirkIndex(int index) {
            this.selectedQuirkIndex = index;
            this.selectedAbilityIndex = 0;
        }

        public int getSelectedAbilityIndex() { return selectedAbilityIndex; }
        public void setSelectedAbilityIndex(int index) { this.selectedAbilityIndex = index; }

        public void cycleAbility(int direction, List<Ability> abilities, QuirkInstance instance) {
            if (abilities.isEmpty()) return;
            int max = abilities.size();
            int start = selectedAbilityIndex;
            int current = start;

            for(int i=0; i<max; i++) {
                current = (current + direction) % max;
                if (current < 0) current += max;

                if (!abilities.get(current).isHidden(this, instance)) {
                    selectedAbilityIndex = current;
                    return;
                }
            }
        }
    }
}