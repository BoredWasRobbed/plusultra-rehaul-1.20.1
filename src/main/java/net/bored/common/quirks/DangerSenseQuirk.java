package net.bored.common.quirks;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class DangerSenseQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "danger_sense");

    public DangerSenseQuirk() { super(ID); }

    @Override
    public int getIconColor() { return 0xFFFFAA00; } // Orange

    @Override
    public void registerAbilities() {
        // Ability 1: Toggle Sense
        this.addAbility(new QuirkSystem.Ability("Toggle Sense", QuirkSystem.AbilityType.TOGGLE, 20, 1, 0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (data.runtimeTags.containsKey("DANGER_SENSE_OFF")) {
                    data.runtimeTags.remove("DANGER_SENSE_OFF");
                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§6Danger Sense Enabled"), true);
                } else {
                    data.runtimeTags.put("DANGER_SENSE_OFF", "true");
                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cDanger Sense Disabled"), true);
                }
                this.triggerCooldown(instance);
            }
        });
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        // Check if toggled off
        if (data.runtimeTags.containsKey("DANGER_SENSE_OFF")) {
            // Reset any client glow if we turned it off
            if (entity.getWorld().isClient && entity instanceof PlayerEntity) {
                resetGlow(entity, data);
            }
            return;
        }

        double baseRange = 15.0 + (data.meta * 1.5);
        Box box = entity.getBoundingBox().expand(baseRange);

        // --- CLIENT SIDE VISUALS ---
        if (entity.getWorld().isClient) {
            if (entity instanceof PlayerEntity player && player.isMainPlayer()) {
                List<Entity> nearby = entity.getWorld().getOtherEntities(entity, box);
                for (Entity e : nearby) {
                    boolean isDangerous = false;
                    if (e instanceof ProjectileEntity p) {
                        if (!p.isOnGround() && p.getOwner() != entity) isDangerous = true;
                    } else if (e instanceof HostileEntity m) {
                        if (m.getTarget() == entity) isDangerous = true;
                    }

                    if (isDangerous) {
                        e.setGlowing(true);
                    } else {
                        // Only reset glowing if we set it (simple heuristic: close range check)
                        if (e.isGlowing() && e.squaredDistanceTo(entity) < (baseRange * baseRange)) {
                            e.setGlowing(false);
                        }
                    }
                }
            }
            return; // Stop here for client
        }

        // --- SERVER SIDE LOGIC (Sound/Chat) ---

        // Manage Cooldown
        if (data.runtimeTags.containsKey("DANGER_SENSE_COOLDOWN")) {
            try {
                int timer = Integer.parseInt(data.runtimeTags.get("DANGER_SENSE_COOLDOWN"));
                if (timer > 0) {
                    data.runtimeTags.put("DANGER_SENSE_COOLDOWN", String.valueOf(timer - 1));
                    return; // Still on cooldown
                }
            } catch (NumberFormatException e) {}
        }

        List<Entity> dangerousEntities = new ArrayList<>();

        // 1. Check for Projectiles
        List<ProjectileEntity> projectiles = entity.getWorld().getEntitiesByClass(ProjectileEntity.class, box, p -> {
            boolean isMoving = !p.isOnGround();
            boolean notOwner = p.getOwner() != entity;
            return isMoving && notOwner;
        });
        dangerousEntities.addAll(projectiles);

        // 2. Check for Hostile Mobs targeting the player
        List<HostileEntity> mobs = entity.getWorld().getEntitiesByClass(HostileEntity.class, box, m -> {
            return m.getTarget() == entity;
        });
        dangerousEntities.addAll(mobs);

        if (dangerousEntities.isEmpty()) return;

        // Calculate Threat Metrics
        double closestDistSq = Double.MAX_VALUE;
        for (Entity e : dangerousEntities) {
            double d = e.squaredDistanceTo(entity);
            if (d < closestDistSq) closestDistSq = d;
        }
        double closestDist = Math.sqrt(closestDistSq);
        int threatCount = dangerousEntities.size();

        // Scaling Logic
        float distRatio = (float) MathHelper.clamp(closestDist / baseRange, 0.0, 1.0);
        float countFactor = Math.min(threatCount, 5) / 5.0f;

        // Dynamic Pitch: Closer = Higher Pitch
        float pitch = MathHelper.lerp(distRatio, 2.0f, 0.5f) + (countFactor * 0.2f);

        // Dynamic Volume: Closer = Louder
        float volume = MathHelper.lerp(distRatio, 1.0f, 0.2f) + (countFactor * 0.5f);

        // Dynamic Cooldown: Closer = Faster Pings
        int baseCooldown = (int) MathHelper.lerp(distRatio, 10, 40);
        int cooldown = Math.max(5, baseCooldown - (int)(countFactor * 10));

        // Play Sound
        entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, volume, pitch);

        // Visual Text Feedback (Throttled by cooldown)
        if (entity instanceof PlayerEntity p) {
            if (closestDist < 5.0 || cooldown > 20) {
                String urgency = (closestDist < 5.0) ? "§c§lIMMEDIATE DANGER!" : "§eDanger nearby...";
                if (threatCount > 2) urgency += " §7(Multiple Threats)";
                p.sendMessage(Text.of("§6[Sense] " + urgency), true);
            }
        }

        // Set Cooldown
        data.runtimeTags.put("DANGER_SENSE_COOLDOWN", String.valueOf(cooldown));
    }

    private void resetGlow(LivingEntity entity, QuirkSystem.QuirkData data) {
        double range = 15.0 + (data.meta * 1.5);
        Box box = entity.getBoundingBox().expand(range);
        List<Entity> nearby = entity.getWorld().getOtherEntities(entity, box);
        for (Entity e : nearby) {
            if (e.isGlowing()) e.setGlowing(false);
        }
    }
}