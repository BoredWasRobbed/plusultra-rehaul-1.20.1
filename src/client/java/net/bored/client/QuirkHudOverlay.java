package net.bored.client;

import net.bored.common.QuirkRegistry;
import net.bored.api.QuirkSystem;
import net.bored.api.IQuirkDataAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.List;

public class QuirkHudOverlay implements HudRenderCallback {

    // --- COLORS & CONSTANTS ---
    private static final int STAMINA_COLOR = 0xFF00E5FF;
    private static final int AWAKENED_STAMINA_COLOR = 0xFFFF0000;
    private static final int BACKGROUND_COLOR = 0x80000000;
    private static final int SELECTED_COLOR = 0xFFD700;
    private static final int PASSIVE_COLOR = 0xAAAAAA;
    private static final int LOCKED_COLOR = 0xFF5555;
    private static final int XP_COLOR = 0xFF00FF00;

    private static final int BAR_WIDTH = 100;
    private static final int PADDING = 10;

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.player.isSpectator()) return;

        if (!(client.player instanceof IQuirkDataAccessor accessor)) return;
        QuirkSystem.QuirkData data = accessor.getQuirkData();

        // 1. Check if player has any quirks. If empty, do not render HUD.
        if (data.getQuirks().isEmpty()) return;

        // 2. Validate Index
        int index = data.getSelectedQuirkIndex();
        if (index < 0 || index >= data.getQuirks().size()) {
            index = 0; // Fallback
        }

        QuirkSystem.QuirkData.QuirkInstance currentQuirkInstance = data.getQuirks().get(index);

        // 3. Fetch Real Quirk from Registry
        QuirkSystem.Quirk realQuirk = QuirkRegistry.get(new Identifier(currentQuirkInstance.quirkId));

        // If quirk is invalid (e.g. typo or removed mod), stop rendering to avoid crash
        if (realQuirk == null) return;

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        TextRenderer font = client.textRenderer;

        int x = width - BAR_WIDTH - PADDING;
        int y = height - 20;
        int rightEdge = x + BAR_WIDTH;

        // --- STAMINA BAR ---
        double maxStamina = data.getMaxStaminaPool();
        double currentStamina = data.currentStamina;
        if (maxStamina <= 0) maxStamina = 1;

        int fillWidth = (int) ((currentStamina / maxStamina) * BAR_WIDTH);
        if (fillWidth > BAR_WIDTH) fillWidth = BAR_WIDTH;

        context.fill(x, y, rightEdge, y + 5, BACKGROUND_COLOR);
        context.fill(x, y, x + fillWidth, y + 5, currentQuirkInstance.awakened ? AWAKENED_STAMINA_COLOR : STAMINA_COLOR);

        context.drawText(font, "Stamina", x, y - 10, 0xFFFFFF, true);
        String staminaNum = String.format("%d/%d", (int)currentStamina, (int)maxStamina);
        context.drawText(font, staminaNum, rightEdge - font.getWidth(staminaNum), y - 10, PASSIVE_COLOR, true);

        // --- ABILITY LIST (Real Data) ---
        int currentY = y - 25;
        int selectedSlot = data.getSelectedAbilityIndex();

        // Fetch real abilities from the Registry Object
        List<QuirkSystem.Ability> abilities = realQuirk.getAbilities();

        for (int i = abilities.size() - 1; i >= 0; i--) {
            QuirkSystem.Ability ability = abilities.get(i);
            boolean isSelected = (i == selectedSlot);
            boolean isLocked = data.level < ability.getRequiredLevel();

            String displayText = ability.getName();
            int textColor;

            if (isLocked) {
                displayText += " (Lvl " + ability.getRequiredLevel() + ")";
                textColor = LOCKED_COLOR;
            } else {
                if (isSelected) {
                    displayText = "> " + displayText + " <";
                    textColor = SELECTED_COLOR;
                } else {
                    textColor = PASSIVE_COLOR;
                }
            }

            // Draw text aligned to the right
            context.drawText(font, displayText, rightEdge - font.getWidth(displayText), currentY, textColor, true);
            currentY -= 12;
        }

        int headerY = currentY - 5;

        // --- QUIRK NAME ---
        String quirkDisplayName = realQuirk.getId().getPath();
        // Beautify name: "all_for_one" -> "All For One"
        quirkDisplayName = quirkDisplayName.replace("_", " ");
        StringBuilder sb = new StringBuilder();
        for (String s : quirkDisplayName.split(" ")) {
            if (!s.isEmpty()) sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1).toLowerCase()).append(" ");
        }
        quirkDisplayName = sb.toString().trim();

        int nameColor = 0xFFFFFF;
        if (currentQuirkInstance.awakened) {
            quirkDisplayName = "§k||§r " + quirkDisplayName + " §k||";
            nameColor = AWAKENED_STAMINA_COLOR;
        }

        context.drawText(font, quirkDisplayName, rightEdge - font.getWidth(quirkDisplayName), headerY, nameColor, true);

        // --- LEVEL & XP ---
        int levelY = headerY - 15;
        int xpBarWidth = 40;
        int xpBarX = rightEdge - xpBarWidth;

        context.fill(xpBarX, levelY + 10, rightEdge, levelY + 11, BACKGROUND_COLOR);

        float maxXp = data.level * 100f;
        float currentXp = data.experience;
        int xpFill = (int) ((currentXp / maxXp) * xpBarWidth);

        context.fill(xpBarX, levelY + 10, xpBarX + xpFill, levelY + 11, XP_COLOR);

        String lvlText = "Lvl " + data.level;
        context.drawText(font, lvlText, rightEdge - font.getWidth(lvlText), levelY, SELECTED_COLOR, true);
    }
}