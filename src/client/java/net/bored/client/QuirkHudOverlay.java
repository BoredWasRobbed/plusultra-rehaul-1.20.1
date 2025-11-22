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

    private static final int STOCKPILE_BG = 0xFF000000;
    private static final int STOCKPILE_FILL = 0xFFFFFFFF;

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

        // --- RENDER STOCKPILE BAR (Left Side) ---
        // Support Stockpile via direct ID OR via merged OFA
        boolean showStockpile = "plusultra:stockpile".equals(currentQuirkInstance.quirkId);

        // If it's OFA and merged with Stockpile, show the bar
        if ("plusultra:one_for_all".equals(currentQuirkInstance.quirkId)) {
            if (currentQuirkInstance.persistentData.contains("FirstQuirk") &&
                    "plusultra:stockpile".equals(currentQuirkInstance.persistentData.getString("FirstQuirk"))) {
                showStockpile = true;
            }
        }

        if (showStockpile) {
            renderStockpileBar(context, font, height, currentQuirkInstance);
        }

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

        // UPDATED: Fetch abilities dynamically based on instance (needed for OFA)
        List<QuirkSystem.Ability> abilities = realQuirk.getAbilities(currentQuirkInstance);
        int selectedSlot = data.getSelectedAbilityIndex();

        for (int i = abilities.size() - 1; i >= 0; i--) {
            if (i >= abilities.size()) continue; // Safety

            QuirkSystem.Ability ability = abilities.get(i);
            boolean isSelected = (i == selectedSlot);
            boolean isLocked = instanceIsLocked(currentQuirkInstance) || data.level < ability.getRequiredLevel();
            boolean onCooldown = ability.getCurrentCooldown() > 0;

            String displayText = ability.getName();
            int textColor;

            if (isLocked) {
                if (instanceIsLocked(currentQuirkInstance)) displayText += " (LOCKED)";
                else displayText += " (Lvl " + ability.getRequiredLevel() + ")";
                textColor = LOCKED_COLOR;
            } else {
                if (onCooldown) {
                    float seconds = ability.getCurrentCooldown() / 20.0f;
                    displayText += String.format(" [%.1fs]", seconds);
                    textColor = LOCKED_COLOR;
                } else {
                    if (ability.getCost() > 0) displayText += " [" + (int)ability.getCost() + "]";
                    textColor = isSelected ? SELECTED_COLOR : PASSIVE_COLOR;
                }
                if (isSelected) displayText = "> " + displayText + " <";
            }

            context.drawText(font, displayText, rightEdge - font.getWidth(displayText), currentY, textColor, true);
            currentY -= LINE_HEIGHT;
        }

        int headerY = currentY - 3;
        String quirkDisplayName = realQuirk.getId().getPath().replace("_", " ");

        // Rename display if Merged
        if ("plusultra:one_for_all".equals(currentQuirkInstance.quirkId)) {
            quirkDisplayName = "One For All";
        } else {
            StringBuilder sb = new StringBuilder();
            for (String s : quirkDisplayName.split(" ")) {
                if (!s.isEmpty()) sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1).toLowerCase()).append(" ");
            }
            quirkDisplayName = sb.toString().trim();
        }

        int nameColor = 0xFFFFFF;
        if (instanceIsLocked(currentQuirkInstance)) {
            quirkDisplayName = "§7[LOCKED] " + quirkDisplayName;
            nameColor = 0xFFAAAAAA;
        } else if (currentQuirkInstance.awakened) {
            quirkDisplayName = "§k||§r " + quirkDisplayName + " §k||";
            nameColor = AWAKENED_STAMINA_COLOR;
        }

        context.drawText(font, quirkDisplayName, rightEdge - font.getWidth(quirkDisplayName), headerY, nameColor, true);

        if ("plusultra:one_for_all".equals(currentQuirkInstance.quirkId)) {
            int gen = currentQuirkInstance.persistentData.getInt("Generation");
            if (gen > 0) {
                String genText = "Generation " + gen;
                context.drawText(font, genText, rightEdge - font.getWidth(genText), headerY - 24, 0xFFFFD700, true);
            }
        }

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

    private boolean instanceIsLocked(QuirkSystem.QuirkData.QuirkInstance instance) {
        return instance.isLocked;
    }

    private void renderStockpileBar(DrawContext context, TextRenderer font, int screenHeight, QuirkSystem.QuirkData.QuirkInstance instance) {
        float maxPercent = instance.persistentData.getFloat("StockpilePercent");
        float selectedPercent = instance.persistentData.contains("SelectedPercent") ?
                instance.persistentData.getFloat("SelectedPercent") : maxPercent;
        if (selectedPercent > maxPercent) selectedPercent = maxPercent;

        int barWidth = 6;
        int barHeight = 100;
        int x = 10;
        int y = (screenHeight / 2) - (barHeight / 2);

        context.fill(x, y, x + barWidth, y + barHeight, STOCKPILE_BG);
        context.drawBorder(x, y, barWidth, barHeight, 0xFF555555);

        int innerHeight = barHeight - 2;
        int fillHeight = (int) ((selectedPercent / 100.0f) * innerHeight);
        int fillTopY = (y + barHeight - 1) - fillHeight;

        context.fill(x + 1, fillTopY, x + barWidth - 1, y + barHeight - 1, STOCKPILE_FILL);
        String percentText = String.format("%.0f%%", selectedPercent);
        int textWidth = font.getWidth(percentText);
        int textX = x + (barWidth / 2) - (textWidth / 2);
        context.drawTextWithShadow(font, percentText, textX, y - 10, 0xFFFFFFFF);

        int powerWidth = font.getWidth("PWR");
        int powerX = x + (barWidth / 2) - (powerWidth / 2);
        context.drawTextWithShadow(font, "PWR", powerX, y + barHeight + 4, 0xFFFFAA00);
    }
}