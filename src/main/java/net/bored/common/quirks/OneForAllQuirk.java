package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.bored.common.QuirkRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class OneForAllQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "one_for_all");

    public OneForAllQuirk() { super(ID); }

    @Override
    public float getPowerMultiplier(int count, QuirkSystem.QuirkData data) {
        return 1.0f + (data.meta * 0.05f);
    }

    @Override
    public void registerAbilities() {
        // Only Bestowal is native to the Quirk Core itself
        this.addAbility(new QuirkSystem.Ability("Bestow (Transfer)", QuirkSystem.AbilityType.INSTANT, 1200, 1, 100.0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                data.runtimeTags.put("OFA_MODE", "TRANSFER");
                data.currentStamina -= this.getCost();
                if(entity instanceof PlayerEntity p) {
                    p.sendMessage(Text.of("§e[Bestowal] Next hit will TRANSFER One For All!"), true);
                }
                this.triggerCooldown();
            }
        });
    }

    // DYNAMIC MERGE LOGIC
    @Override
    public List<QuirkSystem.Ability> getAbilities(QuirkSystem.QuirkData.QuirkInstance instance) {
        List<QuirkSystem.Ability> dynamicList = new ArrayList<>();

        // 1. Get the fused quirk ID from NBT
        if (instance.persistentData.contains("FirstQuirk")) {
            String fusedId = instance.persistentData.getString("FirstQuirk");
            QuirkSystem.Quirk fusedQuirk = QuirkRegistry.get(new Identifier(fusedId));

            if (fusedQuirk != null) {
                // Add all abilities from the fused quirk
                dynamicList.addAll(fusedQuirk.getAbilities(instance));
            }
        }

        // 2. Add Bestowal (which is in the base list)
        dynamicList.addAll(super.getAbilities(instance));

        return dynamicList;
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        // 1. Delegate Update to fused quirk (for passives like Stockpile Accumulation or Regen)
        if (instance.persistentData.contains("FirstQuirk")) {
            String fusedId = instance.persistentData.getString("FirstQuirk");
            QuirkSystem.Quirk fusedQuirk = QuirkRegistry.get(new Identifier(fusedId));
            if (fusedQuirk != null) {
                fusedQuirk.onUpdate(entity, data, instance);
            }
        }

        // 2. Unlocking Logic
        if (entity.age % 100 == 0) {
            int generation = instance.persistentData.getInt("Generation");
            if (generation >= 9 && data.level >= 35) {
                boolean changesMade = false;
                for (QuirkSystem.QuirkData.QuirkInstance qi : data.getQuirks()) {
                    if (qi.isLocked) {
                        qi.isLocked = false;
                        changesMade = true;
                    }
                }
                if (changesMade) {
                    if (entity instanceof PlayerEntity p) {
                        p.sendMessage(Text.of("§6§l[One For All] Past users' quirks have awakened!"), true);
                        p.playSound(net.minecraft.sound.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    }
                    if (entity instanceof ServerPlayerEntity sp) {
                        PlusUltraNetwork.sync(sp);
                    }
                }
            }
        }
    }
}