package net.bored.common.quirks;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

public class BloodcurdleQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "bloodcurdle");

    public BloodcurdleQuirk() { super(ID); }

    @Override
    public int getIconColor() { return 0xFF880000; } // Blood Red

    @Override
    public void registerAbilities() {
        this.addAbility(new QuirkSystem.Ability("Paralyze", QuirkSystem.AbilityType.INSTANT, 100, 1, 10.0) {
            @Override
            public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                // AI logic: Use if we have stolen blood
                return data.runtimeTags.containsKey("BLOOD_STOLEN_FROM");
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (!data.runtimeTags.containsKey("BLOOD_STOLEN_FROM")) {
                    if (entity instanceof PlayerEntity p) {
                        p.sendMessage(Text.of("§cNo blood on weapon! Use a sharp weapon to draw blood first."), true);
                    }
                    return;
                }

                String targetUUIDStr = data.runtimeTags.get("BLOOD_STOLEN_FROM");
                String bloodType = data.runtimeTags.getOrDefault("BLOOD_STOLEN_TYPE", "O+");

                // Find target
                UUID targetId = UUID.fromString(targetUUIDStr);
                LivingEntity target = null;

                // Search world entities (Server side optimized)
                if (!entity.getWorld().isClient && entity.getWorld() instanceof ServerWorld serverWorld) {
                    Entity e = serverWorld.getEntity(targetId);
                    if (e instanceof LivingEntity le) {
                        target = le;
                    }
                }

                if (target != null) {
                    QuirkSystem.QuirkData targetData = ((IQuirkDataAccessor) target).getQuirkData();

                    int ticks = calculateParalysisTicks(bloodType);
                    targetData.runtimeTags.put("BLOODCURDLE_ACTIVE", "true");
                    targetData.runtimeTags.put("BLOODCURDLE_TIMER", String.valueOf(ticks));

                    // Visual/Audio feedback
                    entity.playSound(net.minecraft.sound.SoundEvents.ENTITY_GENERIC_DRINK, 1.0f, 0.5f);

                    if (entity instanceof PlayerEntity p) {
                        // Obscured feedback for the user
                        p.sendMessage(Text.of("§4Target paralyzed."), true);
                    }
                    if (target instanceof PlayerEntity tp) {
                        tp.sendMessage(Text.of("§cYou have been paralyzed by Bloodcurdle!"), true);
                    }

                    if (target instanceof ServerPlayerEntity sp) PlusUltraNetwork.sync(sp);
                } else {
                    if (entity instanceof PlayerEntity p) {
                        p.sendMessage(Text.of("§cTarget not found or out of range."), true);
                    }
                }

                // Consume blood
                data.runtimeTags.remove("BLOOD_STOLEN_FROM");
                data.runtimeTags.remove("BLOOD_STOLEN_TYPE");
                data.currentStamina -= this.getCost();
                this.triggerCooldown(instance);
            }
        });
    }

    public static int calculateParalysisTicks(String bloodType) {
        int baseTicks = 0;
        // Base Durations
        if (bloodType.contains("B")) {
            if (bloodType.contains("AB")) baseTicks = 7200; // AB = 6 mins
            else baseTicks = 9600; // B = 8 mins
        } else if (bloodType.contains("A")) {
            baseTicks = 4800; // A = 4 mins
        } else {
            baseTicks = 2400; // O = 2 mins
        }

        // Rh Factor Modifier
        boolean isB = bloodType.startsWith("B"); // Matches B+, B- (but not AB)

        if (bloodType.contains("+")) {
            if (!isB) {
                baseTicks += 600; // +30s
            }
        } else if (bloodType.contains("-")) {
            baseTicks -= 600; // -30s
        }

        return Math.max(0, baseTicks);
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        // Passive logic if any
    }
}