package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.bored.config.PlusUltraConfig;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

public class BloodletQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "bloodlet");

    public BloodletQuirk() { super(ID); }

    @Override
    public int getIconColor() { return 0xFF8B0000; } // Dark Red

    @Override
    public void registerAbilities() {
        // Ability: Expel & Retract
        this.addAbility(new QuirkSystem.Ability("Expel & Retract", QuirkSystem.AbilityType.INSTANT, 100, 1, 15.0) {
            @Override
            public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                return target != null && distanceSquared < 25.0; // 5 blocks
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                // Trigger Expel
                data.runtimeTags.put("BLOODLET_STAGE", "EXPEL");
                data.runtimeTags.put("BLOODLET_TIMER", "20"); // 1 second duration for expel

                // Visuals & Sound
                entity.swingHand(Hand.MAIN_HAND, true);
                entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.5f);

                // EXPEL LOGIC (Damage)
                float damage = 6.0f + (data.strength * 0.5f);
                Box box = entity.getBoundingBox().expand(4.0); // 4 block radius

                if (entity.getWorld() instanceof ServerWorld sw) {
                    // Explosion of "blood" particles (Using Redstone Block particles)
                    sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState()),
                            entity.getX(), entity.getY() + 1, entity.getZ(),
                            100, 0.5, 0.5, 0.5, 1.0);

                    sw.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, entity.getX(), entity.getY() + 1, entity.getZ(), 20, 2.0, 2.0, 2.0, 0.5);
                    sw.spawnParticles(ParticleTypes.LANDING_LAVA, entity.getX(), entity.getY() + 1, entity.getZ(), 50, 3.0, 0.5, 3.0, 0.1);
                }

                entity.getWorld().getEntitiesByClass(LivingEntity.class, box, e -> e != entity).forEach(e -> {
                    e.damage(entity.getDamageSources().magic(), damage);
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 1));
                });

                data.currentStamina -= this.getCost();
                if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§4[Bloodlet] Expelling..."), true);

                this.triggerCooldown(instance);
            }
        });
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        if (data.runtimeTags.containsKey("BLOODLET_STAGE")) {
            int timer = Integer.parseInt(data.runtimeTags.getOrDefault("BLOODLET_TIMER", "0"));

            if (timer > 0) {
                data.runtimeTags.put("BLOODLET_TIMER", String.valueOf(timer - 1));
                // Continuous particles while "blood is out"
                if (entity.getWorld() instanceof ServerWorld sw && entity.age % 2 == 0) {
                    sw.spawnParticles(ParticleTypes.DRIPPING_LAVA, entity.getX(), entity.getY() + 1, entity.getZ(), 5, 3.0, 1.0, 3.0, 0.0);
                }
            } else {
                String stage = data.runtimeTags.get("BLOODLET_STAGE");
                if ("EXPEL".equals(stage)) {
                    // Switch to Retract
                    retractBlood(entity, data);
                    data.runtimeTags.remove("BLOODLET_STAGE");
                    data.runtimeTags.remove("BLOODLET_TIMER");
                }
            }
        }
    }

    private void retractBlood(LivingEntity entity, QuirkSystem.QuirkData data) {
        // Heal based on "blood returned"
        float healAmount = 4.0f + (data.meta * 0.2f);
        entity.heal(healAmount);

        if (entity.getWorld() instanceof ServerWorld sw) {
            // Implosion effect
            sw.spawnParticles(ParticleTypes.HEART, entity.getX(), entity.getY() + 1.5, entity.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
            sw.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, entity.getX(), entity.getY() + 1, entity.getZ(), 20, 0.2, 0.2, 0.2, 0.05);
        }

        entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED, SoundCategory.PLAYERS, 1.0f, 1.5f);

        if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§4[Bloodlet] Retracted."), true);
    }
}