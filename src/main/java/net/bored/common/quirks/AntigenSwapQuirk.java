package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.List;

public class AntigenSwapQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "antigen_swap");

    private static final List<String> BLOOD_TYPES = Arrays.asList(
            "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"
    );

    public AntigenSwapQuirk() { super(ID); }

    @Override
    public int getIconColor() { return 0xFFFF5555; } // Red

    @Override
    public void registerAbilities() {
        this.addAbility(new QuirkSystem.Ability("Swap Type", QuirkSystem.AbilityType.INSTANT, 20, 1, 5.0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                String current = data.bloodType;
                int index = BLOOD_TYPES.indexOf(current);
                if (index == -1) index = 0;

                int nextIndex = (index + 1) % BLOOD_TYPES.size();
                String nextType = BLOOD_TYPES.get(nextIndex);

                data.bloodType = nextType;

                if (entity instanceof PlayerEntity p) {
                    p.sendMessage(Text.of("§c[Antigen] Blood Type changed to: " + nextType), true);
                }

                // Sync data to update client (vital for potential future HUDs or logic)
                if (entity instanceof ServerPlayerEntity sp) {
                    PlusUltraNetwork.sync(sp);
                }

                this.triggerCooldown(instance);
            }
        });
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        // Passive: Maybe immunity to Wither/Poison due to adaptable blood?
        // For now, just the swapping ability as requested.
    }
}