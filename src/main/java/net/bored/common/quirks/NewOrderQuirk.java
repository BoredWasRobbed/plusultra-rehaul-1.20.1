package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Iterator;
import java.util.UUID;

public class NewOrderQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "new_order");

    public NewOrderQuirk() { super(ID); }

    @Override
    public int getIconColor() { return 0xFFFFFF55; } // Bright Yellow/Gold

    @Override
    public void registerAbilities() {
        // The "Ability" here is just opening the menu to edit rules
        this.addAbility(new QuirkSystem.Ability("Rule Editor", QuirkSystem.AbilityType.INSTANT, 20, 1, 0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (entity instanceof ServerPlayerEntity sp) {
                    // Send packet to open GUI on client
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeInt(sp.getId()); // dummy data
                    ServerPlayNetworking.send(sp, new Identifier("plusultra", "open_new_order_menu"), buf);
                }
            }
        });
    }

    // --- Active Rule Logic ---

    public static class ActiveRule {
        public String phrase;
        public String effect;
        public UUID targetUuid;
        public int durationTicks;
        public int maxDuration;

        public ActiveRule(String phrase, String effect, UUID targetUuid, int duration) {
            this.phrase = phrase;
            this.effect = effect;
            this.targetUuid = targetUuid;
            this.durationTicks = duration;
            this.maxDuration = duration;
        }
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        if (entity.getWorld().isClient) return;

        NbtList activeRulesList = instance.persistentData.getList("ActiveRules", NbtElement.COMPOUND_TYPE);

        boolean changesMade = false;
        Iterator<NbtElement> it = activeRulesList.iterator();

        while (it.hasNext()) {
            NbtCompound ruleTag = (NbtCompound) it.next();
            int timeLeft = ruleTag.getInt("TimeLeft");
            UUID targetId = ruleTag.getUuid("TargetUUID");
            String effect = ruleTag.getString("Effect");
            String phrase = ruleTag.getString("Phrase");

            Entity target = ((ServerWorld)entity.getWorld()).getEntity(targetId);

            if (timeLeft > 0 && target != null && target.isAlive()) {
                // APPLY EFFECT
                applyRuleEffect(entity, target, effect, timeLeft);
                ruleTag.putInt("TimeLeft", timeLeft - 1);
            } else {
                // Rule Expired
                if (entity instanceof PlayerEntity p) {
                    p.sendMessage(Text.of("§7Rule expired: \"" + phrase + "\""), true);
                }

                // Run Cleanup
                cleanupRuleEffect(target, effect);

                it.remove();
                changesMade = true;
            }
        }

        if (changesMade) {
            if (entity instanceof ServerPlayerEntity sp) PlusUltraNetwork.sync(sp);
        }
    }

    private void applyRuleEffect(LivingEntity user, Entity targetEntity, String effect, int timeLeft) {
        if (!(targetEntity instanceof LivingEntity target)) return;

        ServerWorld world = (ServerWorld) user.getWorld();

        // Particles to show rule is active
        if (timeLeft % 20 == 0) {
            world.spawnParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + 2, target.getZ(), 5, 0.5, 0.5, 0.5, 0);
        }

        switch (effect) {
            case "POISON":
                if (timeLeft % 20 == 0) {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 40, 1));
                    target.damage(user.getDamageSources().magic(), 1.0f);
                }
                break;
            case "BURN":
                target.setFireTicks(20);
                break;
            case "STOP":
                target.setVelocity(0,0,0);
                target.velocityModified = true;
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 10));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 10, 10));
                break;
            case "FLOAT":
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 10, 2));
                break;
            case "SMITE":
                if (timeLeft % 40 == 0) { // Slower tick for Smite to balance damage
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY()+1, target.getZ(), 1, 0, 0, 0, 0);
                    target.damage(user.getDamageSources().lightningBolt(), 4.0f);
                }
                break;
            case "WEAKEN":
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 3));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, 3));
                break;
            case "BOOST":
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, 1));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 1));
                break;
            // --- NEW EFFECTS ---
            case "SHIELD":
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 2));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 40, 0));
                break;
            case "HEAL":
                if (timeLeft % 20 == 0) {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 1));
                }
                break;
            case "LAUNCH":
                if (timeLeft % 60 == 0) { // Intermittent launch
                    target.addVelocity(0, 1.0, 0);
                    target.velocityModified = true;
                    world.playSound(null, target.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                break;
            case "GLOW":
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0));
                break;
            case "WITHER":
                if (timeLeft % 20 == 0) {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 40, 1));
                }
                break;
        }
    }

    public void cleanupRuleEffect(Entity targetEntity, String effect) {
        if (targetEntity instanceof LivingEntity target) {
            // Remove lingering effects if rule is cancelled early
            if (effect.equals("STOP")) {
                target.removeStatusEffect(StatusEffects.SLOWNESS);
                target.removeStatusEffect(StatusEffects.WEAKNESS);
            } else if (effect.equals("FLOAT")) {
                target.removeStatusEffect(StatusEffects.LEVITATION);
            } else if (effect.equals("SHIELD")) {
                target.removeStatusEffect(StatusEffects.RESISTANCE);
                target.removeStatusEffect(StatusEffects.ABSORPTION);
            } else if (effect.equals("GLOW")) {
                target.removeStatusEffect(StatusEffects.GLOWING);
            } else if (effect.equals("WEAKEN")) {
                target.removeStatusEffect(StatusEffects.WEAKNESS);
                target.removeStatusEffect(StatusEffects.MINING_FATIGUE);
            } else if (effect.equals("BOOST")) {
                target.removeStatusEffect(StatusEffects.STRENGTH);
                target.removeStatusEffect(StatusEffects.SPEED);
            }
        }
    }

    // Called by Chat Handler AND Network Handler (for removal)
    public void removeRule(ServerPlayerEntity player, QuirkSystem.QuirkData.QuirkInstance instance, int index) {
        NbtList activeRules = instance.persistentData.getList("ActiveRules", NbtElement.COMPOUND_TYPE);
        if (index >= 0 && index < activeRules.size()) {
            NbtCompound rule = activeRules.getCompound(index);
            String effect = rule.getString("Effect");
            UUID targetId = rule.getUuid("TargetUUID");

            Entity target = ((ServerWorld)player.getWorld()).getEntity(targetId);
            cleanupRuleEffect(target, effect);

            player.sendMessage(Text.of("§cRule Removed: \"" + rule.getString("Phrase") + "\""), true);
            activeRules.remove(index);

            instance.persistentData.put("ActiveRules", activeRules);
            PlusUltraNetwork.sync(player);
        }
    }

    public void activateRule(ServerPlayerEntity player, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance, String phrase, String effect, Entity target) {
        NbtList activeRules = instance.persistentData.getList("ActiveRules", NbtElement.COMPOUND_TYPE);

        // LIMIT CHECK: Only 2 rules allowed. FIFO.
        if (activeRules.size() >= 2) {
            // Remove the first (oldest) one
            removeRule(player, instance, 0);
            // Refresh list reference as removeRule modifies it in place but we need to be safe
            activeRules = instance.persistentData.getList("ActiveRules", NbtElement.COMPOUND_TYPE);
        }

        NbtCompound newRule = new NbtCompound();
        newRule.putString("Phrase", phrase);
        newRule.putString("Effect", effect);
        newRule.putUuid("TargetUUID", target.getUuid());

        // Base duration 30 seconds (600 ticks), scales with Meta
        int duration = 600 + (data.meta * 40);
        newRule.putInt("TimeLeft", duration);

        activeRules.add(newRule);
        instance.persistentData.put("ActiveRules", activeRules);

        player.sendMessage(Text.of("§eNew Order: §f\"" + phrase + "\" applied to " + target.getName().getString()), true);

        // Visuals
        ((ServerWorld)player.getWorld()).spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, target.getX(), target.getY() + 1, target.getZ(), 20, 0.5, 0.5, 0.5, 0.5);
        player.playSound(net.minecraft.sound.SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);

        PlusUltraNetwork.sync(player);
    }
}