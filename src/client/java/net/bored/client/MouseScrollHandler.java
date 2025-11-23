package net.bored.client;

import net.bored.common.PlusUltraNetwork;
import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;

public class MouseScrollHandler {

    public static boolean onScroll(double horizontal, double vertical) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;

        // Switch Ability (R Key)
        if (PlusUltraClientHandlers.switchKey.isPressed()) {
            if (vertical != 0) {
                int direction = (vertical > 0) ? -1 : 1;
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(direction);
                ClientPlayNetworking.send(PlusUltraNetwork.SWITCH_ABILITY, buf);
                return true;
            }
        }

        // Quirk Special Adjustment (G Key)
        if (PlusUltraClientHandlers.specialKey.isPressed()) {
            QuirkSystem.QuirkData data = ((IQuirkDataAccessor)client.player).getQuirkData();
            if (!data.getQuirks().isEmpty()) {
                QuirkSystem.QuirkData.QuirkInstance active = data.getQuirks().get(data.getSelectedQuirkIndex());

                // Allow scrolling for Stockpile (Percent) AND Warp Gate (Anchors)
                if ("plusultra:stockpile".equals(active.quirkId) || "plusultra:warp_gate".equals(active.quirkId)) {
                    if (vertical != 0) {
                        int direction = (vertical > 0) ? 1 : -1;
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeInt(direction);
                        ClientPlayNetworking.send(PlusUltraNetwork.ADJUST_PERCENTAGE, buf);
                        return true;
                    }
                }
            }
        }

        return false;
    }
}