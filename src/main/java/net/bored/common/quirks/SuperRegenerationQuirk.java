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
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                boolean isNowActive = !data.runtimeTags.containsKey("REGEN_ACTIVE");

                if (isNowActive) {
                    data.runtimeTags.put("REGEN_ACTIVE", "true");
                    // UPDATED: Now passes 'data' for Meta scaling
                    float power = getPowerMultiplier(instance.count, data);
                    if(entity instanceof PlayerEntity p) p.sendMessage(Text.of("§aRegeneration Active (Power: " + String.format("%.1fx", power) + ")"), true);
                } else {
                    data.runtimeTags.remove("REGEN_ACTIVE");
                    if(entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cRegeneration Disabled"), true);
                }
                this.triggerCooldown();
            }
        });
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        if (data.runtimeTags.containsKey("REGEN_ACTIVE")) {
            // Calculate speed based on count
            // 1 copy = 20 ticks (1s)
            // 2 copies = 15 ticks
            // 3 copies = 10 ticks
            int tickDelay = Math.max(5, 25 - (instance.count * 5));

            // Apply Meta Scaling to speed/effectiveness
            // Higher Meta -> Faster Regen? Or just more healing?
            // For now let's just use standard heal logic but we could implement meta here.

            if (entity.age % tickDelay == 0) {
                if (entity.getHealth() < entity.getMaxHealth()) {
                    if (data.currentStamina >= 5.0) {
                        float power = getPowerMultiplier(instance.count, data);
                        entity.heal(power); // Heal scales with Meta now
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