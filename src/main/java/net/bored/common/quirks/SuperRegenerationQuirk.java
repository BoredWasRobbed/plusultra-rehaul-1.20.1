package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class SuperRegenerationQuirk extends QuirkSystem.Quirk {
    // RENAMED ID
    public static final Identifier ID = new Identifier("plusultra", "super_regeneration");

    public SuperRegenerationQuirk() { super(ID); }

    @Override
    public void registerAbilities() {
        this.addAbility(new QuirkSystem.Ability("Regeneration", QuirkSystem.AbilityType.TOGGLE, 20, 1, 0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data) {
                boolean isNowActive = !data.runtimeTags.containsKey("REGEN_ACTIVE");

                if (isNowActive) {
                    data.runtimeTags.put("REGEN_ACTIVE", "true");
                    if(entity instanceof PlayerEntity p) p.sendMessage(Text.of("§aRegeneration Active"), true);
                } else {
                    data.runtimeTags.remove("REGEN_ACTIVE");
                    if(entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cRegeneration Disabled"), true);
                }
                this.triggerCooldown();
            }
        });
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data) {
        if (data.runtimeTags.containsKey("REGEN_ACTIVE")) {
            // Tick every second (20 ticks)
            if (entity.age % 20 == 0) {
                // Check if player actually needs healing
                if (entity.getHealth() < entity.getMaxHealth()) {
                    if (data.currentStamina >= 5.0) {
                        entity.heal(1.0f);
                        data.currentStamina -= 5.0;

                        // CRITICAL: Sync data to client so bar goes down
                        if (entity instanceof ServerPlayerEntity serverPlayer) {
                            PlusUltraNetwork.sync(serverPlayer);
                        }
                    } else {
                        // Out of stamina -> Turn off
                        data.runtimeTags.remove("REGEN_ACTIVE");
                        if(entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cRegeneration Disabled (Low Stamina)"), true);
                    }
                }
                // Optional: If you want it to drain stamina just for being ON, move the deduction here.
            }
        }
    }
}