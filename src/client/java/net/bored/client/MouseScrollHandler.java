package net.bored.client;

import net.bored.common.PlusUltraNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

public class MouseScrollHandler {

    // Register this in your Client Initializer using ScreenMouseEvents or a standard MouseScrollCallback
    public static boolean onScroll(double horizontal, double vertical) {
        // Check if R key is held down
        if (PlusUltraClientHandlers.switchKey.isPressed()) {
            if (vertical != 0) {
                int direction = (vertical > 0) ? -1 : 1; // Up = Prev, Down = Next

                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(direction);
                ClientPlayNetworking.send(PlusUltraNetwork.SWITCH_ABILITY, buf);

                return true; // Cancel default scrolling (hotbar switch)
            }
        }
        return false;
    }
}