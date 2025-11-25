package net.bored.common.quirks;

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
        // Passive quirk, no active abilities needed.
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        // Server Side Only: Audio feedback and Chat warnings
        if (entity.getWorld().isClient) return;

        // Manage Cooldown
        if (data.runtimeTags.containsKey("DANGER_SENSE_COOLDOWN")) {
            try {
                int timer = Integer.parseInt(data.runtimeTags.get("DANGER_SENSE_COOLDOWN"));
                if (timer > 0) {
                    data.runtimeTags.put("DANGER_SENSE_COOLDOWN", String.valueOf(timer - 1));
                    return; // Still on cooldown
                }
            } catch (NumberFormatException e) {
                // Ignore corrupt data
            }
        }

        // Detection Parameters
        double baseRange = 15.0 + (data.meta * 1.5);
        Box box = entity.getBoundingBox().expand(baseRange);
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
}