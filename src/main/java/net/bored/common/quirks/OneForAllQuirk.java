package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.bored.common.QuirkRegistry;
import net.bored.common.UniqueQuirkState;
import net.bored.config.PlusUltraConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
        this.addAbility(new QuirkSystem.Ability("Bestow (Transfer)", QuirkSystem.AbilityType.INSTANT, 1200, 1, 100.0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                data.runtimeTags.put("OFA_MODE", "TRANSFER");
                data.currentStamina -= this.getCost();
                if(entity instanceof PlayerEntity p) {
                    p.sendMessage(Text.of("§e[Bestowal] Next hit will TRANSFER One For All!"), true);
                }
                this.triggerCooldown(instance);
            }
        });
    }

    @Override
    public List<QuirkSystem.Ability> getAbilities(QuirkSystem.QuirkData.QuirkInstance instance) {
        List<QuirkSystem.Ability> dynamicList = new ArrayList<>();
        if (instance.persistentData.contains("FirstQuirk")) {
            String fusedId = instance.persistentData.getString("FirstQuirk");
            QuirkSystem.Quirk fusedQuirk = QuirkRegistry.get(new Identifier(fusedId));
            if (fusedQuirk != null) {
                dynamicList.addAll(fusedQuirk.getAbilities(instance));
            }
        }
        dynamicList.addAll(super.getAbilities(instance));
        return dynamicList;
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        if (PlusUltraConfig.get().limitUniqueQuirks && !entity.getWorld().isClient && entity.age % 100 == 0) {
            UniqueQuirkState state = UniqueQuirkState.getServerState((ServerWorld) entity.getWorld());
            if (!state.isQuirkTaken(ID.toString())) {
                state.setQuirkTaken(ID.toString(), true);
            }
        }

        if (instance.persistentData.contains("FirstQuirk")) {
            String fusedId = instance.persistentData.getString("FirstQuirk");
            QuirkSystem.Quirk fusedQuirk = QuirkRegistry.get(new Identifier(fusedId));
            if (fusedQuirk != null) {
                fusedQuirk.onUpdate(entity, data, instance);
            }
        }

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

    @Override
    public void onRemove(LivingEntity entity, QuirkSystem.QuirkData data) {
        if (!entity.getWorld().isClient && PlusUltraConfig.get().limitUniqueQuirks) {
            UniqueQuirkState state = UniqueQuirkState.getServerState((ServerWorld) entity.getWorld());
            state.setQuirkTaken(ID.toString(), false);
            if (entity instanceof PlayerEntity p) {
                p.sendMessage(Text.of("§7The embers of One For All fade..."), false);
            }
        }
    }
}