package net.bored.api;

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
        public abstract void onUpdate(LivingEntity entity, boolean isActive);
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

        public Ability(String name, AbilityType type, int cooldownMax, int requiredLevel) {
            this.name = name;
            this.type = type;
            this.cooldownMax = cooldownMax;
            this.requiredLevel = requiredLevel;
        }
        public abstract void onActivate(LivingEntity entity, QuirkData data);
        public void onHoldTick(LivingEntity entity, QuirkData data) {}
        public void onRelease(LivingEntity entity, QuirkData data) {}
        public void tick() { if (currentCooldown > 0) currentCooldown--; }
        public boolean isReady() { return currentCooldown <= 0; }
        public boolean canUse(QuirkData data) { return data.level >= requiredLevel && isReady(); }
        public void triggerCooldown() { this.currentCooldown = cooldownMax; }
        public String getName() { return name; }
        public int getRequiredLevel() { return requiredLevel; }
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
        }

        public double getMaxStaminaPool() { return 100 + (staminaMax * 5); }

        public void writeToNbt(NbtCompound nbt) {
            nbt.putInt("Level", level);
            nbt.putDouble("StaminaCur", currentStamina);
            nbt.putInt("SelectedQ", selectedQuirkIndex);
            nbt.putInt("SelectedA", selectedAbilityIndex);

            NbtList quirkList = new NbtList();
            for(QuirkInstance qi : quirks) {
                NbtCompound qTag = new NbtCompound();
                qTag.putString("ID", qi.quirkId);
                qTag.putBoolean("Awakened", qi.awakened);
                quirkList.add(qTag);
            }
            nbt.put("Quirks", quirkList);
        }

        public void readFromNbt(NbtCompound nbt) {
            if (nbt.contains("Level")) level = nbt.getInt("Level");
            if (nbt.contains("StaminaCur")) currentStamina = nbt.getDouble("StaminaCur");
            selectedQuirkIndex = nbt.getInt("SelectedQ");
            selectedAbilityIndex = nbt.getInt("SelectedA");

            quirks.clear();
            NbtList quirkList = nbt.getList("Quirks", NbtElement.COMPOUND_TYPE);
            for(int i = 0; i < quirkList.size(); i++) {
                NbtCompound qTag = quirkList.getCompound(i);
                QuirkInstance qi = new QuirkInstance(qTag.getString("ID"));
                qi.awakened = qTag.getBoolean("Awakened");
                quirks.add(qi);
            }
        }

        public List<QuirkInstance> getQuirks() { return quirks; }
        public int getSelectedQuirkIndex() { return selectedQuirkIndex; }
        public void setSelectedQuirkIndex(int index) { this.selectedQuirkIndex = index; }
        public int getSelectedAbilityIndex() { return selectedAbilityIndex; }
        public void cycleAbility(int direction) { selectedAbilityIndex = Math.max(0, selectedAbilityIndex + direction); }
    }
}