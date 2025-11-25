package net.bored.common.quirks;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class CellActivationQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "cell_activation");
    private static final List<String> BLOOD_TYPES = Arrays.asList(
            "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"
    );

    public CellActivationQuirk() { super(ID); }

    @Override
    public int getIconColor() { return 0xFF00FF88; } // Spring Green

    @Override
    public void registerAbilities() {
        this.addAbility(new QuirkSystem.Ability("Activate Cells", QuirkSystem.AbilityType.INSTANT, 40, 1, 15.0) {
            @Override
            public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                return target != null && target.getHealth() < target.getMaxHealth() && distanceSquared < 9.0;
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (!instance.persistentData.contains("BlockedBlood")) {
                    String randomType = BLOOD_TYPES.get(new Random().nextInt(BLOOD_TYPES.size()));
                    instance.persistentData.putString("BlockedBlood", randomType);
                }
                String blockedType = instance.persistentData.getString("BlockedBlood");

                entity.swingHand(Hand.MAIN_HAND, true);

                // FIXED: Use Erasure's targeting logic to hit entities properly
                LivingEntity target = ErasureQuirk.findTargetInLineOfSight(entity, 3.0);

                if (target == null) {
                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cNo target to activate."), true);
                    return;
                }

                if (!(target instanceof IQuirkDataAccessor)) return;

                // Check Blood Compatibility
                QuirkSystem.QuirkData targetData = ((IQuirkDataAccessor)target).getQuirkData();
                String targetBlood = targetData.bloodType;

                if (targetBlood != null && targetBlood.equals(blockedType)) {
                    // Fail Effect
                    entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0f, 0.5f);
                    if (entity instanceof PlayerEntity p) {
                        p.sendMessage(Text.of("§cIncompatible Blood Type! (" + targetBlood + ")"), true);
                    }
                    if (target instanceof PlayerEntity tp) {
                        tp.sendMessage(Text.of("§cCell Activation rejected by your blood!"), true);
                    }
                    this.triggerCooldown(instance);
                    return;
                }

                // Success Effect
                data.currentStamina -= this.getCost();
                float healAmount = 4.0f + (data.meta * 0.5f);
                target.heal(healAmount);

                entity.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 2.0f);

                if (entity.getWorld() instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 1, target.getZ(), 10, 0.5, 0.5, 0.5, 0.5);
                    sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1, target.getZ(), 5, 0.3, 0.5, 0.3, 0.1);
                }

                if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§aCells Activated!"), true);

                this.triggerCooldown(instance);
            }
        });
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        if (!instance.persistentData.contains("BlockedBlood")) {
            String randomType = BLOOD_TYPES.get(new Random().nextInt(BLOOD_TYPES.size()));
            instance.persistentData.putString("BlockedBlood", randomType);
            if (entity instanceof ServerPlayerEntity sp) PlusUltraNetwork.sync(sp);
        }
    }
}