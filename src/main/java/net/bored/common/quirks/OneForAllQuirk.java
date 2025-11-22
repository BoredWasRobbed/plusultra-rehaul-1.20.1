package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class OneForAllQuirk extends QuirkSystem.Quirk {
    // UPDATED: ID is now quirk_bestowal
    public static final Identifier ID = new Identifier("plusultra", "quirk_bestowal");

    public OneForAllQuirk() { super(ID); }

    @Override
    public float getPowerMultiplier(int count, QuirkSystem.QuirkData data) {
        // Scales heavily with Meta
        return 1.0f + (data.meta * 0.05f);
    }

    @Override
    public void registerAbilities() {
        // Ability 1: Transfer
        this.addAbility(new QuirkSystem.Ability("Bestow (Transfer)", QuirkSystem.AbilityType.INSTANT, 1200, 1, 100.0) { // High stamina cost
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                // Set tag for attack handler
                data.runtimeTags.put("OFA_MODE", "TRANSFER");

                // Deduct Stamina (Transfer is exhausting)
                data.currentStamina -= this.getCost();

                if(entity instanceof PlayerEntity p) {
                    p.sendMessage(Text.of("§e[Bestowal] Next hit will TRANSFER Quirk Bestowal and all your quirks!"), true);
                    p.sendMessage(Text.of("§7Punch a successor to pass on the torch."), false);
                }
                this.triggerCooldown();
            }
        });
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        // LOGIC: Check for unlocking Generation 9 quirks
        if (entity.age % 100 == 0) {
            int generation = instance.persistentData.getInt("Generation");

            // Unlocking Condition: 9th User AND Level 35+
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
                        p.sendMessage(Text.of("§6§l[Bestowal] Past users' quirks have awakened!"), true);
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