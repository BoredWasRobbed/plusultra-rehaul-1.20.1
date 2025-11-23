package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class SuperRegenerationQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "super_regeneration");

    public SuperRegenerationQuirk() { super(ID); }

    @Override
    public void registerAbilities() {
        this.addAbility(new QuirkSystem.Ability("Regeneration", QuirkSystem.AbilityType.TOGGLE, 20, 1, 0) {
            @Override
            public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                boolean active = data.runtimeTags.containsKey("REGEN_ACTIVE");
                if (!active && user.getHealth() < user.getMaxHealth() * 0.8) return true;
                return false;
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                boolean isNowActive = !data.runtimeTags.containsKey("REGEN_ACTIVE");

                if (isNowActive) {
                    data.runtimeTags.put("REGEN_ACTIVE", "true");
                    float power = getPowerMultiplier(instance.count, data);
                    if(entity instanceof PlayerEntity p) p.sendMessage(Text.of("§aRegeneration Active (Power: " + String.format("%.1fx", power) + ")"), true);
                } else {
                    data.runtimeTags.remove("REGEN_ACTIVE");
                    if(entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cRegeneration Disabled"), true);
                }
                this.triggerCooldown(instance);
            }
        });
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        if (data.runtimeTags.containsKey("REGEN_ACTIVE")) {
            // For AI, turn off if full health to save stamina
            if (!(entity instanceof PlayerEntity) && entity.getHealth() >= entity.getMaxHealth()) {
                data.runtimeTags.remove("REGEN_ACTIVE");
                return;
            }

            int tickDelay = Math.max(5, 25 - (instance.count * 5));

            if (entity.age % tickDelay == 0) {
                if (entity.getHealth() < entity.getMaxHealth()) {
                    if (data.currentStamina >= 5.0) {
                        float power = getPowerMultiplier(instance.count, data);
                        entity.heal(power);
                        data.currentStamina -= 5.0;

                        if (entity instanceof ServerPlayerEntity serverPlayer) {
                            PlusUltraNetwork.sync(serverPlayer);
                        }
                    } else {
                        data.runtimeTags.remove("REGEN_ACTIVE");
                        if(entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cRegeneration Disabled (Low Stamina)"), true);
                    }
                }
            }
        }
    }

    @Override
    public void onRemove(LivingEntity entity, QuirkSystem.QuirkData data) {
        if (data.runtimeTags.containsKey("REGEN_ACTIVE")) {
            data.runtimeTags.remove("REGEN_ACTIVE");
            if(entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cRegeneration Disabled (Quirk Lost)"), true);
        }
    }
}