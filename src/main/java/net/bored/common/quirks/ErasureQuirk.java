package net.bored.common.quirks;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
// REMOVED: import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
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
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class ErasureQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "erasure");

    public ErasureQuirk() { super(ID); }

    @Override
    public int getIconColor() { return 0xFF333333; } // Dark Grey

    @Override
    public void registerAbilities() {
        // Ability 1: Erase (Single Target)
        this.addAbility(new QuirkSystem.Ability("Erase", QuirkSystem.AbilityType.TOGGLE, 20, 1, 0) {
            @Override
            public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                // AI: Toggle on if looking at a target with quirks
                if (target != null && distanceSquared < 100.0 && !data.runtimeTags.containsKey("ERASURE_ACTIVE")) {
                    if (user.canSee(target)) return true;
                }
                // Turn off if no target or low stamina
                if (data.runtimeTags.containsKey("ERASURE_ACTIVE")) {
                    return target == null || !user.canSee(target) || data.currentStamina < 10;
                }
                return false;
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (!data.runtimeTags.containsKey("ERASURE_ACTIVE")) {
                    // Try to lock onto a target
                    LivingEntity target = ErasureQuirk.findTargetInLineOfSight(entity, 30.0);

                    if (target != null) {
                        data.runtimeTags.put("ERASURE_ACTIVE", "true");
                        data.runtimeTags.put("ERASURE_TARGET_ID", String.valueOf(target.getId()));

                        if (entity instanceof PlayerEntity p) {
                            String name = target.getName().getString();
                            p.sendMessage(Text.of("§cErasure active on " + name), true);
                        }
                        entity.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0f, 2.0f);
                    } else {
                        if (entity instanceof PlayerEntity p) {
                            p.sendMessage(Text.of("§cNo target found."), true);
                        }
                    }
                } else {
                    data.runtimeTags.remove("ERASURE_ACTIVE");
                    data.runtimeTags.remove("ERASURE_TARGET_ID");
                    if (entity instanceof PlayerEntity p) {
                        p.sendMessage(Text.of("§7Erasure deactivated."), true);
                    }
                }
                this.triggerCooldown(instance);
            }
        });

        // Ability 2: Group Erase (AoE Cone)
        this.addAbility(new QuirkSystem.Ability("Group Erase", QuirkSystem.AbilityType.TOGGLE, 40, 10, 0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (!data.runtimeTags.containsKey("ERASURE_GROUP_ACTIVE")) {
                    data.runtimeTags.put("ERASURE_GROUP_ACTIVE", "true");
                    if (entity instanceof PlayerEntity p) {
                        p.sendMessage(Text.of("§cGroup Erasure active."), true);
                    }
                    entity.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.5f);
                } else {
                    data.runtimeTags.remove("ERASURE_GROUP_ACTIVE");
                    if (entity instanceof PlayerEntity p) {
                        p.sendMessage(Text.of("§7Group Erasure deactivated."), true);
                    }
                }
                this.triggerCooldown(instance);
            }
        });
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        // Client Side: Removed unsafe MinecraftClient usage.
        // Visual indicators must be handled in client-only classes (PlusUltraClientHandlers).
        if (entity.getWorld().isClient) return;

        // Server Side Logic
        boolean syncNeeded = false;

        // --- SINGLE TARGET ERASE ---
        if (data.runtimeTags.containsKey("ERASURE_ACTIVE")) {
            try {
                int targetId = Integer.parseInt(data.runtimeTags.get("ERASURE_TARGET_ID"));
                Entity targetEntity = entity.getWorld().getEntityById(targetId);

                // INCREASED COST: 2.5 per tick (was 0.5)
                double cost = 2.5;

                if (targetEntity instanceof LivingEntity target && target.isAlive()) {
                    // Apply Erasure
                    QuirkSystem.QuirkData targetData = ((IQuirkDataAccessor) target).getQuirkData();

                    // Set ERASED tag with 3-tick duration (needs constant refresh)
                    targetData.runtimeTags.put("ERASED", "3");

                    // Distance check
                    if (entity.squaredDistanceTo(target) > 4096.0) {
                        data.runtimeTags.remove("ERASURE_ACTIVE");
                        if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cTarget out of range."), true);
                    }

                } else {
                    // Target dead or null
                    data.runtimeTags.remove("ERASURE_ACTIVE");
                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cTarget lost."), true);
                }

                // DRAIN STAMINA
                if (data.currentStamina >= cost) {
                    data.currentStamina -= cost;
                } else {
                    data.currentStamina = 0;
                    data.runtimeTags.remove("ERASURE_ACTIVE");
                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cOut of Stamina!"), true);
                }
                syncNeeded = true;

            } catch (NumberFormatException e) {
                data.runtimeTags.remove("ERASURE_ACTIVE");
            }
        }

        // --- GROUP ERASE ---
        if (data.runtimeTags.containsKey("ERASURE_GROUP_ACTIVE")) {
            // INCREASED BASE COST: 3.0 + 2.0 per target
            double baseCost = 3.0;
            double costPerTarget = 2.0;
            double currentCost = baseCost;

            Vec3d start = entity.getEyePos();
            Vec3d dir = entity.getRotationVector();
            double range = 25.0;

            // Cone Scan
            List<LivingEntity> targets = entity.getWorld().getEntitiesByClass(LivingEntity.class,
                    entity.getBoundingBox().expand(range), e -> e != entity && e.isAlive());

            int targetsHit = 0;

            for (LivingEntity target : targets) {
                // Cone Check (Dot Product)
                Vec3d dirToTarget = target.getEyePos().subtract(start).normalize();
                double dot = dir.dotProduct(dirToTarget);

                // Angle approx 45 degrees (0.707)
                if (dot > 0.7 && entity.canSee(target) && entity.squaredDistanceTo(target) < (range*range)) {
                    QuirkSystem.QuirkData targetData = ((IQuirkDataAccessor) target).getQuirkData();
                    targetData.runtimeTags.put("ERASED", "3");
                    targetsHit++;
                }
            }

            currentCost += (targetsHit * costPerTarget);

            // DRAIN STAMINA
            if (data.currentStamina >= currentCost) {
                data.currentStamina -= currentCost;
            } else {
                data.currentStamina = 0;
                data.runtimeTags.remove("ERASURE_GROUP_ACTIVE");
                if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cOut of Stamina!"), true);
            }
            syncNeeded = true;
        }

        // Sync stamina changes to client occasionally (every 10 ticks) to prevent network spam,
        // unless stamina ran out (already handled above).
        if (syncNeeded && entity.age % 10 == 0 && entity instanceof ServerPlayerEntity sp) {
            PlusUltraNetwork.sync(sp);
        }
    }

    // Made public static so client handler can use it
    public static LivingEntity findTargetInLineOfSight(LivingEntity entity, double range) {
        Vec3d start = entity.getCameraPosVec(1.0f);
        Vec3d dir = entity.getRotationVector();
        Vec3d end = start.add(dir.multiply(range));

        Box box = entity.getBoundingBox().stretch(dir.multiply(range)).expand(1.0);

        double closestDist = range * range;
        LivingEntity target = null;

        for (Entity e : entity.getWorld().getOtherEntities(entity, box)) {
            if (e instanceof LivingEntity living) {
                float border = e.getTargetingMargin() + 0.25f;
                Box hitBox = e.getBoundingBox().expand(border);
                if (hitBox.raycast(start, end).isPresent()) {
                    double d = start.squaredDistanceTo(e.getPos());
                    if (d < closestDist) {
                        closestDist = d;
                        target = living;
                    }
                }
            }
        }
        return target;
    }

    @Override
    public void onRemove(LivingEntity entity, QuirkSystem.QuirkData data) {
        data.runtimeTags.remove("ERASURE_ACTIVE");
        data.runtimeTags.remove("ERASURE_GROUP_ACTIVE");
    }
}