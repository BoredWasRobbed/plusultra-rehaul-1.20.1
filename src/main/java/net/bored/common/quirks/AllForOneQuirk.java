package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class AllForOneQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "all_for_one");

    public AllForOneQuirk() { super(ID); }

    @Override
    public void registerAbilities() {
        this.addAbility(new QuirkSystem.Ability("Forced Transfer (Steal)", QuirkSystem.AbilityType.INSTANT, 100, 1, 20.0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data) {
                data.runtimeTags.put("AFO_MODE", "STEAL");
                data.currentStamina -= this.getCost();
                if(entity instanceof PlayerEntity p) p.sendMessage(Text.of("§c[AFO] Next hit will STEAL a quirk."), true);
                this.triggerCooldown();
            }
        });

        this.addAbility(new QuirkSystem.Ability("Bestowal (Give)", QuirkSystem.AbilityType.INSTANT, 100, 1, 20.0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data) {
                data.runtimeTags.put("AFO_MODE", "GIVE");
                data.currentStamina -= this.getCost();
                if(entity instanceof PlayerEntity p) p.sendMessage(Text.of("§e[AFO] Next hit will GIVE your selected quirk."), true);
                this.triggerCooldown();
            }
        });
    }

    // FIXED: Now correctly overrides the new signature
    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data) {
        // AFO Passive Logic (Optional)
        // For example, could passively increase Meta stat here:
        // if (entity.age % 100 == 0) data.meta = Math.min(50, data.meta + 1);
    }
}