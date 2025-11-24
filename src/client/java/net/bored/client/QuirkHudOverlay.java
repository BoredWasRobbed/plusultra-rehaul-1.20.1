package net.bored.client;

import net.bored.common.QuirkRegistry;
import net.bored.api.QuirkSystem;
import net.bored.api.IQuirkDataAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtElement;
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

        // --- COPY QUIRK PROXY LOGIC ---
        // If current is Copy and has an active slot, pretend we are the copied quirk for HUD purposes
        QuirkSystem.QuirkData.QuirkInstance renderInstance = currentQuirkInstance;
        if ("plusultra:copy".equals(currentQuirkInstance.quirkId)) {
            if (currentQuirkInstance.persistentData.contains("ActiveSlot")) {
                int activeSlot = currentQuirkInstance.persistentData.getInt("ActiveSlot");
                if (activeSlot != -1) {
                    String key = "Slot_" + activeSlot;
                    if (currentQuirkInstance.persistentData.contains(key)) {
                        NbtCompound slotData = currentQuirkInstance.persistentData.getCompound(key);
                        String copiedId = slotData.getString("QuirkId");

                        // Create a fake instance for rendering context
                        QuirkSystem.QuirkData.QuirkInstance fake = new QuirkSystem.QuirkData.QuirkInstance(copiedId);
                        fake.count = currentQuirkInstance.count;
                        fake.innate = false;
                        fake.awakened = false;
                        fake.persistentData = slotData.getCompound("Data");
                        // We don't need full cooldown map for HUD usually, or we'd need to filter it.
                        // But for "Stockpile Bar" or "Warp Anchors" which read persistentData, this is enough.
                        renderInstance = fake;
                    }
                }
            }
        }

        QuirkSystem.Quirk realQuirk = QuirkRegistry.get(new Identifier(renderInstance.quirkId));
        if (realQuirk == null) return;

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        TextRenderer font = client.textRenderer;

        // Stockpile Bar
        boolean showStockpile = "plusultra:stockpile".equals(renderInstance.quirkId);
        if ("plusultra:one_for_all".equals(renderInstance.quirkId)) {
            if (renderInstance.persistentData.contains("FirstQuirk") &&
                    "plusultra:stockpile".equals(renderInstance.persistentData.getString("FirstQuirk"))) {
                showStockpile = true;
            }
        }
        if (showStockpile) {
            renderStockpileBar(context, font, height, renderInstance);
        }

        // Warp Gate Info
        if ("plusultra:warp_gate".equals(renderInstance.quirkId)) {
            renderWarpInfo(context, font, height, renderInstance);
        }

        int x = width - BAR_WIDTH - PADDING;
        int y = height - 20;
        int rightEdge = x + BAR_WIDTH;

        double maxStamina = data.getMaxStaminaPool();
        double currentStamina = data.currentStamina;
        if (maxStamina <= 0) maxStamina = 1;

        context.fill(x, y, rightEdge, y + BAR_HEIGHT, BACKGROUND_COLOR);
        int totalFillWidth = (int) ((currentStamina / maxStamina) * BAR_WIDTH);
        if (totalFillWidth > BAR_WIDTH) totalFillWidth = BAR_WIDTH;
        int barColor = renderInstance.awakened ? AWAKENED_STAMINA_COLOR : realQuirk.getIconColor();
        context.fill(x, y, x + totalFillWidth, y + BAR_HEIGHT, barColor);

        context.drawText(font, "Stamina", x, y - 10, 0xFFFFFF, true);
        String staminaNum = String.format("%d/%d", (int)currentStamina, (int)maxStamina);
        context.drawText(font, staminaNum, rightEdge - font.getWidth(staminaNum), y - 10, PASSIVE_COLOR, true);

        int currentY = y - 25;

        // Use the original instance to get abilities list because CopyQuirk handles the dynamic list generation
        // But if we are masquerading, CopyQuirk.getAbilities returns the wrapped abilities which is correct.
        // However, we passed 'renderInstance' (the fake one) to 'realQuirk'.
        // If we use realQuirk.getAbilities(renderInstance), we get the raw abilities of the copied quirk.
        // If we use CopyQuirk.getAbilities(currentQuirkInstance), we get the WrappedAbilities.
        // The HUD needs to match what the player sees.
        // CopyQuirk.getAbilities handles the "Active Slot" logic to return the correct list.
        // So we should use the ORIGINAL instance for the list, but the FAKE instance for data checks (like Stockpile bar).

        // Re-fetch quirk for the list logic
        QuirkSystem.Quirk listQuirk = QuirkRegistry.get(new Identifier(currentQuirkInstance.quirkId));
        if (listQuirk == null) return;

        List<QuirkSystem.Ability> abilities = listQuirk.getAbilities(currentQuirkInstance);
        int selectedSlot = data.getSelectedAbilityIndex();

        for (int i = abilities.size() - 1; i >= 0; i--) {
            if (i >= abilities.size()) continue;

            QuirkSystem.Ability ability = abilities.get(i);
            if (ability.isHidden(data, currentQuirkInstance)) continue;

            boolean isSelected = (i == selectedSlot);
            boolean isLocked = currentQuirkInstance.isLocked || data.level < ability.getRequiredLevel();
            boolean onCooldown = ability.getCurrentCooldown(currentQuirkInstance) > 0;

            String displayText = ability.getName();
            int textColor;

            if (isLocked) {
                if (currentQuirkInstance.isLocked) displayText += " (LOCKED)";
                else displayText += " (Lvl " + ability.getRequiredLevel() + ")";
                textColor = LOCKED_COLOR;
            } else {
                if (onCooldown) {
                    float seconds = ability.getCurrentCooldown(currentQuirkInstance) / 20.0f;
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

        // UPDATED: Use QuirkSystem to get name (handles Original Owner tag)
        String quirkDisplayName = QuirkSystem.getFormalName(renderInstance);

        int nameColor = 0xFFFFFF;
        if (currentQuirkInstance.isLocked) {
            quirkDisplayName = "§7[LOCKED] " + quirkDisplayName;
            nameColor = 0xFFAAAAAA;
        } else if (currentQuirkInstance.awakened) {
            quirkDisplayName = "§k||§r " + quirkDisplayName + " §k||";
            nameColor = AWAKENED_STAMINA_COLOR;
        }

        context.drawText(font, quirkDisplayName, rightEdge - font.getWidth(quirkDisplayName), headerY, nameColor, true);

        // RESTORED LEVEL AND XP BAR
        if ("plusultra:one_for_all".equals(renderInstance.quirkId)) {
            int gen = renderInstance.persistentData.getInt("Generation");
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

        // RESTORED PWR TEXT
        int powerWidth = font.getWidth("PWR");
        int powerX = x + (barWidth / 2) - (powerWidth / 2);
        context.drawTextWithShadow(font, "PWR", powerX, y + barHeight + 4, 0xFFFFAA00);
    }

    private void renderWarpInfo(DrawContext context, TextRenderer font, int screenHeight, QuirkSystem.QuirkData.QuirkInstance instance) {
        if (!instance.persistentData.contains("Anchors")) return;
        NbtList anchors = instance.persistentData.getList("Anchors", NbtElement.COMPOUND_TYPE);
        if (anchors.isEmpty()) return;

        int selected = instance.persistentData.getInt("SelectedAnchor");
        if (selected < 0 || selected >= anchors.size()) selected = 0;

        NbtCompound tag = anchors.getCompound(selected);
        String name = tag.getString("Name");
        String coords = String.format("[%.0f, %.0f, %.0f]", tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));

        int y = (screenHeight / 2) + 20;
        int x = 10;

        context.drawTextWithShadow(font, "Selected Anchor:", x, y, 0xFFAA00);
        context.drawTextWithShadow(font, name, x, y + 10, 0xFF55FF);
        context.drawTextWithShadow(font, coords, x, y + 20, 0xAAAAAA);
        context.drawTextWithShadow(font, "(G + Scroll to Cycle)", x, y + 35, 0x777777);
    }
}