package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class AllForOneQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "all_for_one");

    public AllForOneQuirk() {
        super(ID);
    }

    @Override
    public void registerAbilities() {
        // Ability 1: Prepared to Steal
        this.addAbility(new QuirkSystem.Ability("Steal", QuirkSystem.AbilityType.INSTANT, 100, 1) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data) {
                // Set the "next hit" mode to STEAL
                data.runtimeTags.put("AFO_MODE", "STEAL");
                if (entity instanceof PlayerEntity p) {
                    p.sendMessage(Text.of("§c[AFO] Next hit will STEAL a quirk."), true);
                }
                // Trigger cooldown immediately
                this.triggerCooldown();
            }
        });

        // Ability 2: Prepared to Give
        this.addAbility(new QuirkSystem.Ability("Bestowal", QuirkSystem.AbilityType.INSTANT, 100, 1) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data) {
                // Set the "next hit" mode to GIVE
                data.runtimeTags.put("AFO_MODE", "GIVE");
                if (entity instanceof PlayerEntity p) {
                    p.sendMessage(Text.of("§e[AFO] Next hit will GIVE your selected quirk."), true);
                }
                this.triggerCooldown();
            }
        });
    }

    @Override
    public void onUpdate(LivingEntity entity, boolean isActive) {
        // Passive: AFO users naturally have higher meta stats?
        // Implementation optional
    }
}