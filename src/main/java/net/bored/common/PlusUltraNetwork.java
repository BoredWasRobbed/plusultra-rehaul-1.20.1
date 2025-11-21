package net.bored.common;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound; // <--- Added this missing import
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class PlusUltraNetwork {
    public static final Identifier ACTIVATE_ABILITY = new Identifier("plusultra", "activate_ability");
    public static final Identifier SWITCH_ABILITY = new Identifier("plusultra", "switch_ability");
    public static final Identifier SWITCH_QUIRK = new Identifier("plusultra", "switch_quirk");
    public static final Identifier SYNC_DATA = new Identifier("plusultra", "sync_data");

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ACTIVATE_ABILITY, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                // Activation logic placeholder
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SWITCH_ABILITY, (server, player, handler, buf, responseSender) -> {
            int direction = buf.readInt();
            server.execute(() -> {
                ((IQuirkDataAccessor)player).getQuirkData().cycleAbility(direction);
                sync(player); // Sync after switch
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SWITCH_QUIRK, (server, player, handler, buf, responseSender) -> {
            int index = buf.readInt();
            server.execute(() -> {
                ((IQuirkDataAccessor)player).getQuirkData().setSelectedQuirkIndex(index);
                sync(player); // Sync after switch
            });
        });
    }

    // --- SYNCING LOGIC ---
    public static void sync(ServerPlayerEntity player) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor)player).getQuirkData();

        PacketByteBuf buf = PacketByteBufs.create();
        NbtCompound nbt = new NbtCompound();
        data.writeToNbt(nbt); // Serialize data
        buf.writeNbt(nbt);

        ServerPlayNetworking.send(player, SYNC_DATA, buf);
    }
}