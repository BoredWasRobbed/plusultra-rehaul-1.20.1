package net.bored.client;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

public class AFOHudOverlay implements HudRenderCallback {

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        // Only render if AFO sight is toggled ON
        if (!PlusUltraClientHandlers.afoSightActive) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // Check the entity currently under the crosshair
        Entity target = client.targetedEntity;

        if (target instanceof LivingEntity living && target instanceof IQuirkDataAccessor accessor) {
            QuirkSystem.QuirkData data = accessor.getQuirkData();

            // Do not render anything if they have no quirks
            if (data.getQuirks().isEmpty()) return;

            int centerX = context.getScaledWindowWidth() / 2;
            int centerY = context.getScaledWindowHeight() / 2;
            TextRenderer font = client.textRenderer;

            // Render Header
            int yOffset = 10;
            String header = "§5§l[Quirks]";
            context.drawTextWithShadow(font, header, centerX + 10, centerY + yOffset, 0xFFFFFF);
            yOffset += 10;

            // Render List
            for (QuirkSystem.QuirkData.QuirkInstance instance : data.getQuirks()) {
                String name = getFormalName(instance.quirkId);
                int color = 0xFFAA00; // Gold for quirk names

                if (instance.innate) {
                    name += " (Innate)";
                    color = 0xFF55FF; // Pinkish for Innate
                }

                context.drawTextWithShadow(font, name, centerX + 10, centerY + yOffset, color);
                yOffset += 10;
            }
        }
    }

    private String getFormalName(String quirkId) {
        try {
            Identifier id = new Identifier(quirkId);
            String path = id.getPath().replace("_", " ");
            StringBuilder sb = new StringBuilder();
            for (String s : path.split(" ")) {
                if (!s.isEmpty()) {
                    sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1).toLowerCase()).append(" ");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return quirkId;
        }
    }
}