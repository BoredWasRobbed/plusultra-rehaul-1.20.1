package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.bored.common.UniqueQuirkState;
import net.bored.config.PlusUltraConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class AllForOneQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "all_for_one");

    public AllForOneQuirk() { super(ID); }

    @Override
    public float getPowerMultiplier(int count, QuirkSystem.QuirkData data) {
        return 1.0f + ((count - 1) * 0.5f);
    }

    @Override
    public void registerAbilities() {
        this.addAbility(new QuirkSystem.Ability("Forced Transfer (Steal)", QuirkSystem.AbilityType.INSTANT, 100, 1, 20.0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                data.runtimeTags.put("AFO_MODE", "STEAL");
                data.currentStamina -= this.getCost();
                if(entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cNext hit will STEAL a quirk."), true);
                this.triggerCooldown(instance);
            }
        });

        this.addAbility(new QuirkSystem.Ability("Bestowal (Give)", QuirkSystem.AbilityType.INSTANT, 100, 1, 20.0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                data.runtimeTags.put("AFO_MODE", "GIVE");
                data.currentStamina -= this.getCost();
                if(entity instanceof PlayerEntity p) p.sendMessage(Text.of("§eNext hit will GIVE your selected quirk."), true);
                this.triggerCooldown(instance);
            }
        });
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        if (PlusUltraConfig.get().limitUniqueQuirks && !entity.getWorld().isClient && entity.age % 100 == 0) {
            UniqueQuirkState state = UniqueQuirkState.getServerState((ServerWorld) entity.getWorld());
            if (!state.isQuirkTaken(ID.toString())) {
                state.setQuirkTaken(ID.toString(), true);
            }
        }
    }

    @Override
    public void onRemove(LivingEntity entity, QuirkSystem.QuirkData data) {
        if (!entity.getWorld().isClient && PlusUltraConfig.get().limitUniqueQuirks) {
            UniqueQuirkState state = UniqueQuirkState.getServerState((ServerWorld) entity.getWorld());
            state.setQuirkTaken(ID.toString(), false);
            if (entity instanceof PlayerEntity p) {
                p.sendMessage(Text.of("§7All For One has left you..."), false);
            }
        }
    }
}