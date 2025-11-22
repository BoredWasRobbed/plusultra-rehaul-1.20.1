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

    private static final int STAMINA_COLOR = 0xFF00E5FF;
    private static final int AWAKENED_STAMINA_COLOR = 0xFFFF5555;
    private static final int BACKGROUND_COLOR = 0x80000000;
    private static final int SELECTED_COLOR = 0xFFD700;
    private static final int PASSIVE_COLOR = 0xAAAAAA;
    private static final int LOCKED_COLOR = 0xFF5555;
    private static final int XP_COLOR = 0xFF00FF00;

    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 6;
    private static final int PADDING = 10;
    private static final int LINE_HEIGHT = 12;

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.player.isSpectator()) return;

        if (!(client.player instanceof IQuirkDataAccessor accessor)) return;
        QuirkSystem.QuirkData data = accessor.getQuirkData();

        if (data.getQuirks().isEmpty()) return;

        int index = data.getSelectedQuirkIndex();
        if (index < 0 || index >= data.getQuirks().size()) index = 0;

        QuirkSystem.QuirkData.QuirkInstance currentQuirkInstance = data.getQuirks().get(index);
        QuirkSystem.Quirk realQuirk = QuirkRegistry.get(new Identifier(currentQuirkInstance.quirkId));
        if (realQuirk == null) return;

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        TextRenderer font = client.textRenderer;

        int x = width - BAR_WIDTH - PADDING;
        int y = height - 20;
        int rightEdge = x + BAR_WIDTH;

        double maxStamina = data.getMaxStaminaPool();
        double currentStamina = data.currentStamina;
        if (maxStamina <= 0) maxStamina = 1;

        // --- STAMINA BAR ---
        context.fill(x, y, rightEdge, y + BAR_HEIGHT, BACKGROUND_COLOR);
        int totalFillWidth = (int) ((currentStamina / maxStamina) * BAR_WIDTH);
        if (totalFillWidth > BAR_WIDTH) totalFillWidth = BAR_WIDTH;
        int barColor = currentQuirkInstance.awakened ? AWAKENED_STAMINA_COLOR : STAMINA_COLOR;
        context.fill(x, y, x + totalFillWidth, y + BAR_HEIGHT, barColor);

        context.drawText(font, "Stamina", x, y - 10, 0xFFFFFF, true);
        String staminaNum = String.format("%d/%d", (int)currentStamina, (int)maxStamina);
        context.drawText(font, staminaNum, rightEdge - font.getWidth(staminaNum), y - 10, PASSIVE_COLOR, true);

        // --- ABILITIES ---
        int currentY = y - 25;
        List<QuirkSystem.Ability> abilities = realQuirk.getAbilities();
        int selectedSlot = data.getSelectedAbilityIndex();

        for (int i = abilities.size() - 1; i >= 0; i--) {
            QuirkSystem.Ability ability = abilities.get(i);
            boolean isSelected = (i == selectedSlot);
            boolean isLocked = data.level < ability.getRequiredLevel();
            boolean onCooldown = ability.getCurrentCooldown() > 0;

            String displayText = ability.getName();
            int textColor;

            if (isLocked) {
                displayText += " (Lvl " + ability.getRequiredLevel() + ")";
                textColor = LOCKED_COLOR;
            } else {
                String suffix = "";
                if (onCooldown) {
                    float seconds = ability.getCurrentCooldown() / 20.0f;
                    suffix = String.format(" [%.1fs]", seconds);
                } else if (ability.getCost() > 0) {
                    suffix = " [" + (int)ability.getCost() + "]";
                }
                displayText += suffix;

                // PRIORITY LOGIC:
                // 1. Cooldown (Red) - Highest Priority, overrides selection color
                // 2. Selected (Gold)
                // 3. Passive (Gray)
                if (onCooldown) {
                    textColor = LOCKED_COLOR;
                } else if (isSelected) {
                    textColor = SELECTED_COLOR;
                } else {
                    textColor = PASSIVE_COLOR;
                }

                if (isSelected) {
                    displayText = "> " + displayText + " <";
                }
            }

            context.drawText(font, displayText, rightEdge - font.getWidth(displayText), currentY, textColor, true);
            currentY -= LINE_HEIGHT;
        }

        int headerY = currentY - 3;
        String quirkDisplayName = realQuirk.getId().getPath().replace("_", " ");
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

        int levelY = headerY - 12;
        String lvlText = "Lvl " + data.level;
        int lvlWidth = font.getWidth(lvlText);
        context.drawText(font, lvlText, rightEdge - lvlWidth, levelY, SELECTED_COLOR, true);

        int xpBarWidth = 40;
        int xpBarX = rightEdge - lvlWidth - 5 - xpBarWidth;
        context.fill(xpBarX, levelY + 4, xpBarX + xpBarWidth, levelY + 5, BACKGROUND_COLOR);
        float maxXp = data.level * 100f;
        float currentXp = data.experience;
        int xpFill = (int) ((currentXp / maxXp) * xpBarWidth);
        context.fill(xpBarX, levelY + 4, xpBarX + xpFill, levelY + 5, XP_COLOR);
    }
}