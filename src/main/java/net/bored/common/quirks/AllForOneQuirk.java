package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class AllForOneQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "all_for_one");

    public AllForOneQuirk() { super(ID); }

    // Override to prevent Meta scaling for AFO
    @Override
    public float getPowerMultiplier(int count, QuirkSystem.QuirkData data) {
        // Returns base multiplier based only on count, ignoring data.meta
        return 1.0f + ((count - 1) * 0.5f);
    }

    @Override
    public void registerAbilities() {
        this.addAbility(new QuirkSystem.Ability("Forced Transfer (Steal)", QuirkSystem.AbilityType.INSTANT, 100, 1, 20.0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                data.runtimeTags.put("AFO_MODE", "STEAL");
                data.currentStamina -= this.getCost();

                // Pass data, though overridden method ignores it
                float power = getPowerMultiplier(instance.count, data);

                if(entity instanceof PlayerEntity p) p.sendMessage(Text.of("§c[AFO] Next hit will STEAL a quirk."), true);
                this.triggerCooldown();
            }
        });

        this.addAbility(new QuirkSystem.Ability("Bestowal (Give)", QuirkSystem.AbilityType.INSTANT, 100, 1, 20.0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                data.runtimeTags.put("AFO_MODE", "GIVE");
                data.currentStamina -= this.getCost();
                if(entity instanceof PlayerEntity p) p.sendMessage(Text.of("§e[AFO] Next hit will GIVE your selected quirk."), true);
                this.triggerCooldown();
            }
        });
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        // AFO Passive
    }
}