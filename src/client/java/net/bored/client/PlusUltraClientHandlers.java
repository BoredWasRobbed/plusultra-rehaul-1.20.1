package net.bored.client;

import net.bored.common.PlusUltraNetwork;
import net.bored.api.QuirkSystem;
import net.bored.api.IQuirkDataAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class PlusUltraClientHandlers implements ClientModInitializer {

    public static KeyBinding activateKey;
    public static KeyBinding switchKey;
    public static KeyBinding menuKey;

    @Override
    public void onInitializeClient() {
        activateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.plusultra.activate",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                "category.plusultra.main"
        ));

        switchKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.plusultra.switch",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.plusultra.main"
        ));

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.plusultra.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.plusultra.main"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (activateKey.wasPressed()) {
                ClientPlayNetworking.send(PlusUltraNetwork.ACTIVATE_ABILITY, PacketByteBufs.create());
            }
            if (menuKey.wasPressed()) {
                QuirkSystem.QuirkData data = ((IQuirkDataAccessor)client.player).getQuirkData();
                if (data.getQuirks().size() >= 2) {
                    client.setScreen(new QuirkSelectionScreen());
                } else {
                    client.player.sendMessage(Text.of("§cYou need at least 2 Quirks to open the Switcher!"), true);
                }
            }
        });
    }

    public static class QuirkSelectionScreen extends Screen {
        public QuirkSelectionScreen() { super(Text.of("Quirk Selection")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context);
            context.drawCenteredTextWithShadow(textRenderer, "Quirk Switcher", width / 2, 20, 0xFFFFFF);
            if (client == null || client.player == null) return;
            QuirkSystem.QuirkData data = ((IQuirkDataAccessor)client.player).getQuirkData();

            int y = 50;
            for (int i = 0; i < data.getQuirks().size(); i++) {
                QuirkSystem.QuirkData.QuirkInstance qi = data.getQuirks().get(i);
                int color = (i == data.getSelectedQuirkIndex()) ? 0x00FF00 : 0xAAAAAA;
                context.drawText(textRenderer, qi.quirkId, width / 2 - 50, y, color, true);
                y += 20;
            }
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (client != null && client.player != null) {
                QuirkSystem.QuirkData data = ((IQuirkDataAccessor)client.player).getQuirkData();
                int y = 50;
                for (int i = 0; i < data.getQuirks().size(); i++) {
                    if (mouseY >= y && mouseY <= y + 10) {
                        PacketByteBufs.create().writeInt(i);
                        ClientPlayNetworking.send(PlusUltraNetwork.SWITCH_QUIRK, PacketByteBufs.create().writeVarInt(i)); // Logic placeholder
                        client.setScreen(null);
                        return true;
                    }
                    y += 20;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }
}