package net.bored.client;

import net.bored.common.PlusUltraNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

public class MouseScrollHandler {

    public static boolean onScroll(double horizontal, double vertical) {
        if (PlusUltraClientHandlers.switchKey.isPressed()) {
            if (vertical != 0) {
                int direction = (vertical > 0) ? -1 : 1;
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(direction);
                ClientPlayNetworking.send(PlusUltraNetwork.SWITCH_ABILITY, buf);
                return true;
            }
        }
        return false;
    }
}