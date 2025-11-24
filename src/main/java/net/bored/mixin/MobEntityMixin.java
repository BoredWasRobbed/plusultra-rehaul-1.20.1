package net.bored.mixin;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.bored.common.QuirkRegistry;
import net.bored.common.UniqueQuirkState;
import net.bored.config.PlusUltraConfig;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Mixin(MobEntity.class)
public class MobEntityMixin {

    @Inject(method = "initialize", at = @At("TAIL"))
    public void onInitialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, EntityData entityData, NbtCompound entityNbt, CallbackInfoReturnable<EntityData> cir) {
        PlusUltraConfig config = PlusUltraConfig.get();

        double chance = 0.0;
        boolean allowed = false;

        Object self = (Object)this;

        if (self instanceof VillagerEntity) {
            if (config.villagersCanSpawnWithQuirks) {
                allowed = true;
                chance = config.villagerQuirkChance;
            }
        } else if (self instanceof Monster) {
            if (config.mobsCanSpawnWithQuirks) {
                allowed = true;
                chance = config.mobQuirkChance;
            }
        }

        if (!allowed) return;

        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) this).getQuirkData();
        if (!data.getQuirks().isEmpty()) return;

        Random random = world.getRandom();

        if (random.nextDouble() < chance) {
            // Pass world to check uniqueness
            assignRandomQuirk(data, config, random, world);
        }
    }

    private void assignRandomQuirk(QuirkSystem.QuirkData data, PlusUltraConfig config, Random random, ServerWorldAccess worldAccess) {
        // Weighted List Logic
        // Map<QuirkID, Weight>
        Map<String, Integer> weightedQuirks = new HashMap<>();
        int totalWeight = 0;

        UniqueQuirkState uniqueState = null;
        if (config.uniqueQuirks && worldAccess instanceof ServerWorld sw) {
            uniqueState = UniqueQuirkState.getServerState(sw);
        }

        for (Identifier id : QuirkRegistry.getKeys()) {
            String idStr = id.toString();

            // Check 1: Is Quirk globally enabled?
            if (!config.isQuirkEnabled(idStr)) continue;

            // Check 2: Is Quirk specifically banned for mobs?
            if (config.isQuirkBannedForMobs(idStr)) continue;

            // Hard exclude OFA/AFO/Bestowal/Quirkless from random mob generation explicitly
            // (Though the new config blacklist covers this, keeping it as a hardcoded safety fallback is wise)
            if (idStr.equals("plusultra:one_for_all") ||
                    idStr.equals("plusultra:all_for_one") ||
                    idStr.equals("plusultra:quirk_bestowal") ||
                    idStr.equals("plusultra:quirkless")) { // Added Quirkless
                continue;
            }

            // Determine Tier/Weight
            boolean isLowTier = idStr.equals("plusultra:super_regeneration");

            // Uniqueness Check
            if (config.uniqueQuirks && !isLowTier) {
                if (uniqueState != null && uniqueState.isQuirkTaken(idStr)) {
                    continue; // Skip this quirk if it's taken and we are enforcing uniqueness
                }
            }

            int weight = isLowTier ? 50 : 5; // 10x more likely to get Regen than Decay/Warp/etc
            weightedQuirks.put(idStr, weight);
            totalWeight += weight;
        }

        if (totalWeight > 0) {
            int roll = random.nextInt(totalWeight);
            String selectedQuirk = null;

            for (Map.Entry<String, Integer> entry : weightedQuirks.entrySet()) {
                roll -= entry.getValue();
                if (roll < 0) {
                    selectedQuirk = entry.getKey();
                    break;
                }
            }

            if (selectedQuirk != null) {
                data.addQuirk(selectedQuirk, true);

                // If we assigned a unique quirk, mark it as taken
                boolean isLowTier = selectedQuirk.equals("plusultra:super_regeneration");
                if (config.uniqueQuirks && !isLowTier && uniqueState != null) {
                    uniqueState.setQuirkTaken(selectedQuirk, true);
                }

                data.staminaMax = random.nextInt(3);
                data.strength = 2 + random.nextInt(4);

                if (!((MobEntity)(Object)this).getWorld().isClient) {
                    PlusUltraNetwork.sync((MobEntity)(Object)this);
                }
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tickQuirkAI(CallbackInfo ci) {
        MobEntity mob = (MobEntity)(Object)this;
        if (mob.getWorld().isClient) return;

        QuirkSystem.QuirkData data = ((IQuirkDataAccessor)mob).getQuirkData();

        boolean isFatigued = data.runtimeTags.containsKey("AI_FATIGUED");
        double maxStamina = data.getMaxStaminaPool();

        if (isFatigued) {
            if (data.currentStamina >= maxStamina * 0.75) {
                data.runtimeTags.remove("AI_FATIGUED");
            } else {
                return;
            }
        } else {
            if (data.currentStamina < maxStamina * 0.15) {
                data.runtimeTags.put("AI_FATIGUED", "true");
                return;
            }
        }

        if (data.aiActionCooldown > 0) return;

        LivingEntity target = mob.getTarget();
        double distSq = (target != null) ? mob.squaredDistanceTo(target) : 0;

        for (QuirkSystem.QuirkData.QuirkInstance instance : data.getQuirks()) {
            QuirkSystem.Quirk quirk = QuirkRegistry.get(new Identifier(instance.quirkId));
            if (quirk == null) continue;

            List<QuirkSystem.Ability> abilities = quirk.getAbilities(instance);

            for (QuirkSystem.Ability ability : abilities) {
                if (ability.shouldAIUse(mob, target, distSq, data, instance)) {
                    if (ability.canUse(data, instance)) {
                        ability.onAIUse(mob, target, data, instance);
                        data.aiActionCooldown = 20;
                        PlusUltraNetwork.sync(mob);
                        return;
                    }
                }
            }
        }
    }
}