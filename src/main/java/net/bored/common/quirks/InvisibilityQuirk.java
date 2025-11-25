package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class InvisibilityQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "invisibility");

    public InvisibilityQuirk() { super(ID); }

    @Override
    public int getIconColor() { return 0xFFF0F8FF; } // Alice Blue / Clear-ish

    @Override
    public void registerAbilities() {
        // Ability 1: Warp Refraction
        this.addAbility(new QuirkSystem.Ability("Warp Refraction", QuirkSystem.AbilityType.INSTANT, 100, 1, 10.0) {
            @Override
            public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                return target != null && distanceSquared < 64.0;
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                data.currentStamina -= this.getCost();
                entity.swingHand(Hand.MAIN_HAND, true);

                if (!entity.getWorld().isClient) {
                    ServerWorld world = (ServerWorld) entity.getWorld();
                    world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 2.0f, 0.5f);

                    // Enhanced Visuals
                    world.spawnParticles(ParticleTypes.FLASH, entity.getX(), entity.getY() + 1, entity.getZ(), 3, 0.2, 0.2, 0.2, 0);
                    world.spawnParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY() + 1, entity.getZ(), 50, 0.5, 0.5, 0.5, 0.5);
                    world.spawnParticles(ParticleTypes.GLOW, entity.getX(), entity.getY() + 1, entity.getZ(), 20, 1.0, 1.0, 1.0, 0.1);

                    float range = 10.0f + (data.meta * 0.5f);
                    Box box = entity.getBoundingBox().expand(range);

                    for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box, e -> e != entity)) {
                        Vec3d look = target.getRotationVector().normalize();
                        Vec3d dirToUser = entity.getPos().subtract(target.getPos()).normalize();
                        double dot = look.dotProduct(dirToUser);

                        if (dot > 0.5 || entity.squaredDistanceTo(target) < 9.0) {
                            target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0));
                            target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0));
                            target.damage(entity.getDamageSources().magic(), 2.0f);
                        }
                    }
                }
                if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§eWarp Refraction!"), true);
                this.triggerCooldown(instance);
            }
        });

        // Ability 2: Prismatic Laser
        this.addAbility(new QuirkSystem.Ability("Prismatic Laser", QuirkSystem.AbilityType.INSTANT, 60, 10, 15.0) {
            @Override
            public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                return target != null && distanceSquared < 400.0;
            }

            @Override
            public void onAIUse(LivingEntity user, LivingEntity target, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                user.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target.getPos().add(0, target.getHeight()/2, 0));
                super.onAIUse(user, target, data, instance);
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                data.currentStamina -= this.getCost();
                entity.swingHand(Hand.MAIN_HAND, true);

                float multiplier = getPowerMultiplier(instance.count, data);
                float damage = 4.0f * multiplier;
                double range = 30.0;

                Vec3d start = entity.getCameraPosVec(1.0f);
                Vec3d dir = entity.getRotationVector();
                Vec3d end = start.add(dir.multiply(range));

                if (!entity.getWorld().isClient) {
                    ServerWorld world = (ServerWorld) entity.getWorld();
                    world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 2.0f);

                    for (double d = 0; d < range; d += 0.5) {
                        Vec3d p = start.add(dir.multiply(d));
                        world.spawnParticles(ParticleTypes.GLOW, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                    }

                    HitResult hit = entity.raycast(range, 0, false);
                    if (hit.getType() == HitResult.Type.ENTITY) {
                        EntityHitResult entityHit = (EntityHitResult) hit;
                        if (entityHit.getEntity() instanceof LivingEntity target) {
                            target.damage(entity.getDamageSources().inFire(), damage);
                            target.setFireTicks(60);
                        }
                    } else if (hit.getType() == HitResult.Type.BLOCK) {
                        world.spawnParticles(ParticleTypes.FLAME, hit.getPos().x, hit.getPos().y, hit.getPos().z, 5, 0.1, 0.1, 0.1, 0.05);
                    }

                    Box lineBox = entity.getBoundingBox().stretch(dir.multiply(range)).expand(0.5);
                    for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, lineBox, e -> e != entity)) {
                        if (target.getBoundingBox().expand(0.2).raycast(start, end).isPresent()) {
                            target.damage(entity.getDamageSources().inFire(), damage);
                            target.setFireTicks(60);
                        }
                    }
                }
                this.triggerCooldown(instance);
            }
        });

        // Ability 3: Toggle Invisibility (Awakening or AFO)
        this.addAbility(new QuirkSystem.Ability("Toggle Camouflage", QuirkSystem.AbilityType.TOGGLE, 20, 1, 0) {
            @Override
            public boolean isHidden(QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (instance.awakened) return false;
                for (QuirkSystem.QuirkData.QuirkInstance q : data.getQuirks()) {
                    if (q.quirkId.equals("plusultra:all_for_one")) return false;
                }
                return true;
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                boolean hasAFO = false;
                for (QuirkSystem.QuirkData.QuirkInstance q : data.getQuirks()) {
                    if (q.quirkId.equals("plusultra:all_for_one")) {
                        hasAFO = true;
                        break;
                    }
                }

                if (!instance.awakened && !hasAFO) return;

                // USE PERSISTENT DATA INSTEAD OF RUNTIME TAGS TO SYNC TO CLIENT
                boolean isActive = instance.persistentData.getBoolean("IsActive");
                instance.persistentData.putBoolean("IsActive", !isActive);

                if (!isActive) {
                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§aInvisibility Active"), true);
                } else {
                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cInvisibility Disabled"), true);
                }

                // FORCE SYNC TO PREVENT HUD FLICKER
                if (entity instanceof ServerPlayerEntity sp) PlusUltraNetwork.sync(sp);
                this.triggerCooldown(instance);
            }
        });
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        // CRITICAL FIX: Run on Server Only. Client assumes false if not synced, causing the flicker.
        if (entity.getWorld().isClient) return;

        boolean active = false;
        boolean hasAFO = false;
        for (QuirkSystem.QuirkData.QuirkInstance q : data.getQuirks()) {
            if (q.quirkId.equals("plusultra:all_for_one")) {
                hasAFO = true;
                break;
            }
        }

        if (!instance.awakened && !hasAFO) {
            // UN-AWAKENED & NO AFO: Always Active
            active = true;
        } else {
            // AWAKENED OR AFO: Active only if toggled ON (Saved in persistent NBT)
            if (instance.persistentData.getBoolean("IsActive")) {
                active = true;
            }
        }

        if (active) {
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 60, 0, false, false));
            entity.setInvisible(true);
        } else {
            boolean canToggle = instance.awakened || hasAFO;
            if (canToggle && !instance.persistentData.getBoolean("IsActive")) {
                if (entity.hasStatusEffect(StatusEffects.INVISIBILITY)) {
                    entity.removeStatusEffect(StatusEffects.INVISIBILITY);
                }
                entity.setInvisible(false);
            }
        }
    }

    @Override
    public void onRemove(LivingEntity entity, QuirkSystem.QuirkData data) {
        entity.removeStatusEffect(StatusEffects.INVISIBILITY);
        entity.setInvisible(false);
    }
}