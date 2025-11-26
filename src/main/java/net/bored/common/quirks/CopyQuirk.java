package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.bored.common.QuirkRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CopyQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "copy");

    public CopyQuirk() {
        super(ID);
    }

    @Override
    public int getIconColor() {
        return 0xFFCCCCCC; // Light Grey for Copy
    }

    @Override
    public void registerAbilities() {
        // No static abilities; all are dynamic.
    }

    @Override
    public List<QuirkSystem.Ability> getAbilities(QuirkSystem.QuirkData.QuirkInstance instance) {
        List<QuirkSystem.Ability> list = new ArrayList<>();
        int unlockedSlots = getUnlockedSlots(instance);

        // Default to -1 (No slot active) if not set
        if (!instance.persistentData.contains("ActiveSlot")) {
            instance.persistentData.putInt("ActiveSlot", -1);
        }
        int activeSlot = instance.persistentData.getInt("ActiveSlot");

        for (int i = 0; i < unlockedSlots; i++) {
            String key = "Slot_" + i;
            boolean isFilled = instance.persistentData.contains(key);

            if (i == activeSlot && isFilled) {
                // --- ACTIVE SLOT: Show Abilities ---
                NbtCompound slotData = instance.persistentData.getCompound(key);
                String copiedId = slotData.getString("QuirkId");
                QuirkSystem.Quirk copiedQuirk = QuirkRegistry.get(new Identifier(copiedId));

                if (copiedQuirk != null) {
                    List<QuirkSystem.Ability> realAbilities = copiedQuirk.getAbilities(createFakeInstance(instance, slotData, copiedId, i));
                    for (QuirkSystem.Ability realAbility : realAbilities) {
                        list.add(new WrappedAbility(realAbility, i));
                    }
                } else {
                    // Error fallback
                    list.add(new ActivateSlotAbility(i, "Error: Unknown"));
                }
            } else {
                // --- INACTIVE SLOT: Show Button ---
                if (isFilled) {
                    NbtCompound slotData = instance.persistentData.getCompound(key);
                    String copiedId = slotData.getString("QuirkId");
                    String name = getFormalName(copiedId);
                    // Button to open this slot
                    list.add(new ActivateSlotAbility(i, name));
                } else {
                    // Button to copy into this slot
                    list.add(new CopySlotAbility(i));
                }
            }
        }
        return list;
    }

    private int getUnlockedSlots(QuirkSystem.QuirkData.QuirkInstance instance) {
        int level = instance.persistentData.getInt("CachedLevel");
        if (level == 0) level = 1;

        if (level < 10) return 1;
        if (level < 30) return 2;
        if (level < 50) return 3;
        if (level < 80) return 4;
        return 5;
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        instance.persistentData.putInt("CachedLevel", data.level);

        int unlockedSlots = getUnlockedSlots(instance);
        int activeSlot = instance.persistentData.getInt("ActiveSlot");
        boolean changes = false;

        // Tick down timers for occupied slots
        for (int i = 0; i < unlockedSlots; i++) {
            String key = "Slot_" + i;
            if (instance.persistentData.contains(key)) {
                NbtCompound slotData = instance.persistentData.getCompound(key);
                long timeLeft = slotData.getLong("TimeLeft");

                if (timeLeft > 0) {
                    slotData.putLong("TimeLeft", timeLeft - 1);
                    instance.persistentData.put(key, slotData);

                    // Only update the ACTIVE quirk logic (passives etc)
                    if (i == activeSlot) {
                        String copiedId = slotData.getString("QuirkId");
                        QuirkSystem.Quirk copiedQuirk = QuirkRegistry.get(new Identifier(copiedId));
                        if (copiedQuirk != null) {
                            QuirkSystem.QuirkData.QuirkInstance fake = createFakeInstance(instance, slotData, copiedId, i);
                            copiedQuirk.onUpdate(entity, data, fake);
                            saveFakeInstance(instance, fake, i);
                        }
                    }
                } else {
                    // Expire
                    instance.persistentData.remove(key);
                    // If active slot expired, reset active slot
                    if (activeSlot == i) {
                        instance.persistentData.putInt("ActiveSlot", -1);
                    }
                    changes = true;
                    if (entity instanceof PlayerEntity p) {
                        p.sendMessage(Text.of("§7Copy Slot " + (i+1) + " has expired."), true);
                    }
                }
            }
        }

        if (changes && entity instanceof ServerPlayerEntity sp) {
            PlusUltraNetwork.sync(sp);
        }
    }

    // --- Helpers ---
    private QuirkSystem.QuirkData.QuirkInstance createFakeInstance(QuirkSystem.QuirkData.QuirkInstance parent, NbtCompound slotData, String quirkId, int slotIndex) {
        QuirkSystem.QuirkData.QuirkInstance fake = new QuirkSystem.QuirkData.QuirkInstance(quirkId);
        fake.count = parent.count;
        fake.innate = false;
        fake.awakened = false;
        fake.persistentData = slotData.getCompound("Data");

        String prefix = "Slot_" + slotIndex + "_";
        for (Map.Entry<String, Integer> entry : parent.cooldowns.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String realAbilityName = entry.getKey().substring(prefix.length());
                fake.cooldowns.put(realAbilityName, entry.getValue());
            }
        }
        return fake;
    }

    private void saveFakeInstance(QuirkSystem.QuirkData.QuirkInstance parent, QuirkSystem.QuirkData.QuirkInstance fake, int slotIndex) {
        String key = "Slot_" + slotIndex;
        if (!parent.persistentData.contains(key)) return;

        NbtCompound slotData = parent.persistentData.getCompound(key);
        slotData.put("Data", fake.persistentData);
        parent.persistentData.put(key, slotData);

        String prefix = "Slot_" + slotIndex + "_";
        for (Map.Entry<String, Integer> entry : fake.cooldowns.entrySet()) {
            parent.cooldowns.put(prefix + entry.getKey(), entry.getValue());
        }
    }

    private static String getFormalName(String quirkId) {
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

    // --- Abilities ---

    // Ability to OPEN a filled slot
    private class ActivateSlotAbility extends QuirkSystem.Ability {
        private final int slotIndex;
        private final String quirkName;

        public ActivateSlotAbility(int slotIndex, String quirkName) {
            super(quirkName, QuirkSystem.AbilityType.INSTANT, 10, 1, 0);
            this.slotIndex = slotIndex;
            this.quirkName = quirkName;
        }

        @Override
        public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
            return false;
        }

        @Override
        public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
            instance.persistentData.putInt("ActiveSlot", slotIndex);
            if (entity instanceof PlayerEntity p) {
                p.sendMessage(Text.of("§aSwitched to " + quirkName), true);
            }
            // Sync needed to update client list immediately
            if (entity instanceof ServerPlayerEntity sp) {
                PlusUltraNetwork.sync(sp);
            }
        }
    }

    // Ability to COPY into an empty slot
    private class CopySlotAbility extends QuirkSystem.Ability {
        private final int slotIndex;

        public CopySlotAbility(int slotIndex) {
            super("Copy Slot " + (slotIndex + 1), QuirkSystem.AbilityType.INSTANT, 20, 1, 10.0);
            this.slotIndex = slotIndex;
        }

        @Override
        public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
            // Simple AI: If target has quirk, try to copy
            if (target != null && distanceSquared < 9.0) {
                // Incomplete implementation since we can't easily check target data without accessor
                // Simplified: Always try copy if close and slot available
                return true;
            }
            return false;
        }

        @Override
        public void onAIUse(LivingEntity user, LivingEntity target, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
            user.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target.getPos());
            super.onAIUse(user, target, data, instance);
        }

        @Override
        public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
            // Cancel if already trying to copy to this slot
            if (data.runtimeTags.containsKey("COPY_MODE_SLOT") && data.runtimeTags.get("COPY_MODE_SLOT").equals(String.valueOf(slotIndex))) {
                data.runtimeTags.remove("COPY_MODE_SLOT");
                if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§7Copy mode cancelled."), true);
                return;
            }

            // Set tag for AttackHandler
            data.runtimeTags.put("COPY_MODE_SLOT", String.valueOf(slotIndex));
            // Ensure no slot is active when trying to copy
            instance.persistentData.putInt("ActiveSlot", -1);

            if (entity instanceof PlayerEntity p) {
                p.sendMessage(Text.of("§7[Copy] Punch a target to copy their quirk to Slot " + (slotIndex + 1)), true);
            }
            this.triggerCooldown(instance);
            if (entity instanceof ServerPlayerEntity sp) PlusUltraNetwork.sync(sp);
        }
    }

    // Wrapper for abilities inside an ACTIVE slot
    private class WrappedAbility extends QuirkSystem.Ability {
        private final QuirkSystem.Ability original;
        private final int slotIndex;

        public WrappedAbility(QuirkSystem.Ability original, int slotIndex) {
            super(original.getName(), original.getType(), 0, original.getRequiredLevel(), original.getCost());
            this.original = original;
            this.slotIndex = slotIndex;
        }

        @Override
        public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
            // Delegate to the real ability!
            return original.shouldAIUse(user, target, distanceSquared, data, instance);
        }

        @Override
        public void onAIUse(LivingEntity user, LivingEntity target, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
            // Delegate logic with Wrapped Runner
            runWrapped(user, data, instance, (e, d, f) -> original.onAIUse(e, target, d, f));
        }

        @Override
        public int getCurrentCooldown(QuirkSystem.QuirkData.QuirkInstance instance) {
            return instance.cooldowns.getOrDefault("Slot_" + slotIndex + "_" + original.getName(), 0);
        }

        @Override
        public void tick(QuirkSystem.QuirkData.QuirkInstance instance) {
            int cd = getCurrentCooldown(instance);
            if (cd > 0) {
                instance.cooldowns.put("Slot_" + slotIndex + "_" + original.getName(), cd - 1);
            }
        }

        @Override
        public void triggerCooldown(QuirkSystem.QuirkData.QuirkInstance instance) {
            String key = "Slot_" + slotIndex;
            if (!instance.persistentData.contains(key)) return;

            NbtCompound slotData = instance.persistentData.getCompound(key);
            String qId = slotData.getString("QuirkId");
            QuirkSystem.QuirkData.QuirkInstance fake = createFakeInstance(instance, slotData, qId, slotIndex);

            original.triggerCooldown(fake);
            saveFakeInstance(instance, fake, slotIndex);
        }

        @Override
        public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
            runWrapped(entity, data, instance, (e, d, f) -> original.onActivate(e, d, f));
        }

        @Override
        public void onHoldTick(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
            runWrapped(entity, data, instance, (e, d, f) -> original.onHoldTick(e, d, f));
        }

        @Override
        public void onRelease(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
            runWrapped(entity, data, instance, (e, d, f) -> original.onRelease(e, d, f));
        }

        @Override
        public boolean isHidden(QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
            String key = "Slot_" + slotIndex;
            if (!instance.persistentData.contains(key)) return true;
            NbtCompound slotData = instance.persistentData.getCompound(key);
            String qId = slotData.getString("QuirkId");
            QuirkSystem.QuirkData.QuirkInstance fake = createFakeInstance(instance, slotData, qId, slotIndex);
            return original.isHidden(data, fake);
        }

        private interface Action { void run(LivingEntity e, QuirkSystem.QuirkData d, QuirkSystem.QuirkData.QuirkInstance f); }

        private void runWrapped(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance, Action action) {
            String key = "Slot_" + slotIndex;
            if (!instance.persistentData.contains(key)) return;

            NbtCompound slotData = instance.persistentData.getCompound(key);
            String qId = slotData.getString("QuirkId");
            QuirkSystem.QuirkData.QuirkInstance fake = createFakeInstance(instance, slotData, qId, slotIndex);

            action.run(entity, data, fake);

            saveFakeInstance(instance, fake, slotIndex);
        }
    }
}