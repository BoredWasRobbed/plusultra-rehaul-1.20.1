package net.bored.common;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class PlusUltraNetwork {
    public static final Identifier ACTIVATE_ABILITY = new Identifier("plusultra", "activate_ability");
    public static final Identifier SWITCH_ABILITY = new Identifier("plusultra", "switch_ability");
    public static final Identifier SWITCH_QUIRK = new Identifier("plusultra", "switch_quirk");
    public static final Identifier SYNC_DATA = new Identifier("plusultra", "sync_data");

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ACTIVATE_ABILITY, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                QuirkSystem.QuirkData data = ((IQuirkDataAccessor)player).getQuirkData();
                if (data.getQuirks().isEmpty()) return;

                int qIndex = data.getSelectedQuirkIndex();
                if (qIndex < 0 || qIndex >= data.getQuirks().size()) return;

                QuirkSystem.QuirkData.QuirkInstance instance = data.getQuirks().get(qIndex);
                QuirkSystem.Quirk quirk = QuirkRegistry.get(new Identifier(instance.quirkId));

                if (quirk != null) {
                    int aIndex = data.getSelectedAbilityIndex();
                    if (aIndex >= 0 && aIndex < quirk.getAbilities().size()) {
                        QuirkSystem.Ability ability = quirk.getAbilities().get(aIndex);
                        if (ability.canUse(data)) {
                            ability.onActivate(player, data);
                            sync(player);
                        } else {
                            if (data.level < ability.getRequiredLevel()) player.sendMessage(Text.of("§cLevel too low!"), true);
                            else if (!ability.isReady()) player.sendMessage(Text.of("§cCooldown!"), true);
                            else player.sendMessage(Text.of("§cNot enough Stamina!"), true);
                        }
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SWITCH_ABILITY, (server, player, handler, buf, responseSender) -> {
            int direction = buf.readInt();
            server.execute(() -> {
                QuirkSystem.QuirkData data = ((IQuirkDataAccessor)player).getQuirkData();
                if (data.getQuirks().isEmpty()) return;

                QuirkSystem.QuirkData.QuirkInstance instance = data.getQuirks().get(data.getSelectedQuirkIndex());
                QuirkSystem.Quirk quirk = QuirkRegistry.get(new Identifier(instance.quirkId));

                if (quirk != null) {
                    data.cycleAbility(direction, quirk.getAbilities().size());
                    sync(player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SWITCH_QUIRK, (server, player, handler, buf, responseSender) -> {
            int index = buf.readInt();
            server.execute(() -> {
                ((IQuirkDataAccessor)player).getQuirkData().setSelectedQuirkIndex(index);
                ((IQuirkDataAccessor)player).getQuirkData().cycleAbility(0, 1); // Reset
                sync(player);
            });
        });
    }

    public static void sync(ServerPlayerEntity player) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor)player).getQuirkData();
        PacketByteBuf buf = PacketByteBufs.create();
        NbtCompound nbt = new NbtCompound();
        data.writeToNbt(nbt);
        buf.writeNbt(nbt);
        ServerPlayNetworking.send(player, SYNC_DATA, buf);
    }
}