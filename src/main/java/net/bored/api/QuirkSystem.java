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
        private boolean awakened = false;

        public Quirk(Identifier id) {
            this.id = id;
            registerAbilities();
        }
        public abstract void registerAbilities();

        // UPDATED: Now receives QuirkData
        public abstract void onUpdate(LivingEntity entity, QuirkData data);

        public boolean checkAwakening(LivingEntity entity, QuirkData data) { return false; }
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

        public abstract void onActivate(LivingEntity entity, QuirkData data);
        public void onHoldTick(LivingEntity entity, QuirkData data) {}
        public void onRelease(LivingEntity entity, QuirkData data) {}
        public void tick() { if (currentCooldown > 0) currentCooldown--; }
        public boolean isReady() { return currentCooldown <= 0; }

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
        public int level = 1, experience = 0;
        public double currentStamina = 100;

        private final List<QuirkInstance> quirks = new ArrayList<>();
        private int selectedQuirkIndex = 0;
        private int selectedAbilityIndex = 0;
        public int aiActionCooldown = 0;
        public Map<String, String> runtimeTags = new HashMap<>();

        public static class QuirkInstance {
            public String quirkId;
            public boolean isPassivesActive = true;
            public boolean awakened = false;
            public QuirkInstance(String id) { this.quirkId = id; }
        }

        public void tick(LivingEntity entity) {
            double regen = 0.5 + (staminaMax * 0.1);
            if (currentStamina < getMaxStaminaPool()) currentStamina = Math.min(currentStamina + regen, getMaxStaminaPool());
            if (aiActionCooldown > 0) aiActionCooldown--;

            // Tick Cooldowns & Passives
            for (QuirkInstance instance : quirks) {
                Quirk quirk = QuirkRegistry.get(new Identifier(instance.quirkId));
                if (quirk != null) {
                    // Tick Abilities
                    for (Ability ability : quirk.getAbilities()) {
                        ability.tick();
                    }

                    // Tick Passive Update
                    if (instance.isPassivesActive) {
                        quirk.onUpdate(entity, this); // Pass 'this' (QuirkData)
                    }
                }
            }
        }

        public double getMaxStaminaPool() { return 100 + (staminaMax * 5); }

        public void writeToNbt(NbtCompound nbt) {
            // Stats
            nbt.putInt("Strength", strength);
            nbt.putInt("Endurance", endurance);
            nbt.putInt("Speed", speed);
            nbt.putInt("StaminaMax", staminaMax);
            nbt.putInt("Meta", meta);

            // Progression
            nbt.putInt("Level", level);
            nbt.putInt("XP", experience);
            nbt.putDouble("StaminaCur", currentStamina);

            // State
            nbt.putInt("SelectedQ", selectedQuirkIndex);
            nbt.putInt("SelectedA", selectedAbilityIndex);

            NbtList quirkList = new NbtList();
            for(QuirkInstance qi : quirks) {
                NbtCompound qTag = new NbtCompound();
                qTag.putString("ID", qi.quirkId);
                qTag.putBoolean("Awakened", qi.awakened);

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