package net.bored.api;

import net.bored.common.QuirkRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Arrays;
import java.util.Random;

public class QuirkSystem {

    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d");
    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("2b3c4d5e-6f7a-8b9c-0d1e-2f3a4b5c6d7e");
    private static final UUID ATTACK_MODIFIER_ID = UUID.fromString("3c4d5e6f-7a8b-9c0d-1e2f-3a4b5c6d7e8f");

    public static String getFormalName(String quirkId) {
        try {
            Identifier id = new Identifier(quirkId);
            String path = id.getPath().replace("_", " ");
            StringBuilder sb = new StringBuilder();
            for (String s : path.split(" ")) {
                if (!s.isEmpty()) {
                    sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1).toLowerCase()).append(" ");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return quirkId;
        }
    }

    public static String getFormalName(QuirkData.QuirkInstance instance) {
        String baseName = getFormalName(instance.quirkId);
        if (instance.persistentData.contains("OriginalOwner")) {
            String owner = instance.persistentData.getString("OriginalOwner");
            return baseName + " (" + owner + ")";
        }
        return baseName;
    }

    public enum AbilityType { INSTANT, HOLD, TOGGLE }

    public static abstract class Quirk {
        private final Identifier id;
        private final List<Ability> abilities = new ArrayList<>();

        public Quirk(Identifier id) {
            this.id = id;
            this.abilities.add(new StrikeAbility());
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

    public static class StrikeAbility extends Ability {
        public StrikeAbility() {
            super("Strike", AbilityType.INSTANT, 20, 1, 5.0);
        }

        @Override
        public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkData data, QuirkData.QuirkInstance instance) {
            return target != null && distanceSquared < 9.0;
        }

        @Override
        public void onActivate(LivingEntity entity, QuirkData data, QuirkData.QuirkInstance instance) {
            entity.swingHand(Hand.MAIN_HAND, true);
            float damage = 3.0f + (data.strength * 1.5f);
            net.minecraft.util.hit.HitResult hit = entity.raycast(3.5, 0, false);
            if (hit.getType() == net.minecraft.util.hit.HitResult.Type.ENTITY) {
                net.minecraft.util.hit.EntityHitResult entityHit = (net.minecraft.util.hit.EntityHitResult) hit;
                if (entityHit.getEntity() instanceof LivingEntity target) {
                    target.damage(entity.getDamageSources().mobAttack(entity), damage);
                    target.takeKnockback(0.5f, entity.getX() - target.getX(), entity.getZ() - target.getZ());
                    if (entity instanceof PlayerEntity p) {
                        p.sendMessage(Text.of("§7Strike! [" + String.format("%.1f", damage) + " Dmg]"), true);
                    }
                }
            } else {
                List<LivingEntity> targets = entity.getWorld().getEntitiesByClass(LivingEntity.class,
                        entity.getBoundingBox().expand(1.0).offset(entity.getRotationVector().multiply(1.5)),
                        e -> e != entity);

                for (LivingEntity target : targets) {
                    target.damage(entity.getDamageSources().mobAttack(entity), damage);
                    target.takeKnockback(0.5f, entity.getX() - target.getX(), entity.getZ() - target.getZ());
                    if (entity instanceof PlayerEntity p) {
                        p.sendMessage(Text.of("§7Strike! [" + String.format("%.1f", damage) + " Dmg]"), true);
                    }
                    break;
                }
            }
            this.triggerCooldown(instance);
        }

        @Override
        public boolean isHidden(QuirkData data, QuirkData.QuirkInstance instance) { return false; }
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
            if (isHidden(data, instance)) return false;
            return data.level >= requiredLevel && isReady(instance) && data.currentStamina >= staminaCost;
        }

        public void triggerCooldown(QuirkData.QuirkInstance instance) {
            instance.cooldowns.put(this.name, this.cooldownMax);
        }

        public boolean isHidden(QuirkData data, QuirkData.QuirkInstance instance) {
            return data.level < this.requiredLevel;
        }

        public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkData data, QuirkData.QuirkInstance instance) {
            return false;
        }

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
        // New Field: Blood Type
        public String bloodType = "";

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

        public void assignRandomBloodType() {
            List<String> types = Arrays.asList("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");
            this.bloodType = types.get(new Random().nextInt(types.size()));
        }

        public void addQuirk(String id, boolean isInnate) { addQuirk(id, isInnate, null); }
        public void addQuirk(String id) { addQuirk(id, false, null); }

        public void addQuirk(String id, boolean isInnate, NbtCompound data) {
            quirks.removeIf(q -> q.quirkId.contains("quirkless"));
            boolean isSpecial = data != null && data.contains("OriginalOwner");
            if (!isSpecial) {
                for (QuirkInstance q : quirks) {
                    if (q.quirkId.equals(id) && !q.persistentData.contains("OriginalOwner")) {
                        q.count++;
                        return;
                    }
                }
            }
            if (quirks.isEmpty()) isInnate = true;
            QuirkInstance newInstance = new QuirkInstance(id);
            newInstance.innate = isInnate;
            if (data != null) newInstance.persistentData = data.copy();
            quirks.add(newInstance);
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
            strength = Math.min(strength, 50);
            endurance = Math.min(endurance, 50);
            speed = Math.min(speed, 50);
            staminaMax = Math.min(staminaMax, 50);
            meta = Math.min(meta, 50);

            // Ensure Blood Type exists
            if (bloodType == null || bloodType.isEmpty()) {
                assignRandomBloodType();
            }

            if (!entity.getWorld().isClient) {
                EntityAttributeInstance movementSpeed = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
                if (movementSpeed != null) {
                    if (movementSpeed.getModifier(SPEED_MODIFIER_ID) != null) movementSpeed.removeModifier(SPEED_MODIFIER_ID);
                    if (speed > 0) {
                        double amount = speed * 0.03;
                        movementSpeed.addTemporaryModifier(new EntityAttributeModifier(SPEED_MODIFIER_ID, "Quirk Speed", amount, EntityAttributeModifier.Operation.MULTIPLY_BASE));
                    }
                }

                if (entity instanceof PlayerEntity player) {
                    float newStepHeight = 0.6f;
                    if (speed >= 5) newStepHeight = 1.5f;
                    if (player.getStepHeight() != newStepHeight) player.setStepHeight(newStepHeight);
                }

                EntityAttributeInstance maxHealth = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                if (maxHealth != null) {
                    if (maxHealth.getModifier(HEALTH_MODIFIER_ID) != null) maxHealth.removeModifier(HEALTH_MODIFIER_ID);
                    if (endurance > 0) {
                        double amount = endurance * 2.0;
                        maxHealth.addTemporaryModifier(new EntityAttributeModifier(HEALTH_MODIFIER_ID, "Quirk Health", amount, EntityAttributeModifier.Operation.ADDITION));
                    }
                }

                EntityAttributeInstance attackDamage = entity.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
                if (attackDamage != null) {
                    if (attackDamage.getModifier(ATTACK_MODIFIER_ID) != null) attackDamage.removeModifier(ATTACK_MODIFIER_ID);
                    if (strength > 0) {
                        double amount = strength * 0.5;
                        attackDamage.addTemporaryModifier(new EntityAttributeModifier(ATTACK_MODIFIER_ID, "Quirk Strength", amount, EntityAttributeModifier.Operation.ADDITION));
                    }
                }

                if (entity instanceof PlayerEntity player && endurance > 0) {
                    if (entity.age % 20 == 0) {
                        float currentExhaustion = player.getHungerManager().getExhaustion();
                        if (currentExhaustion > 0) {
                            float reduction = currentExhaustion * (endurance * 0.02f);
                            player.getHungerManager().setExhaustion(Math.max(0, currentExhaustion - reduction));
                        }
                    }
                }
            }

            double regen = 0.5 + (staminaMax * 0.15);
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

        public double getMaxStaminaPool() { return 100 + (staminaMax * 10); }
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

            if(bloodType != null) nbt.putString("BloodType", bloodType);

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

            if (nbt.contains("BloodType")) bloodType = nbt.getString("BloodType");
            else assignRandomBloodType();

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
            int current = selectedAbilityIndex;
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