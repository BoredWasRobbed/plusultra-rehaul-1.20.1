package net.bored.client;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class StatMenuScreen extends Screen {
    private final long openedTime;
    private float animationProgress = 0.0f;

    // UI Colors
    private static final int STAT_COLOR = 0xFF00E5FF; // Cyan
    private static final int STAT_BG_COLOR = 0x40000000; // Transparent Black
    private static final int LABEL_COLOR = 0xFFDDDDDD; // Light Gray
    private static final int ACCENT_COLOR = 0xFF00E5FF; // Cyan accent

    // Widgets
    private CustomStatButton[] upgradeButtons = new CustomStatButton[5];

    // Layout
    private int statsStartX;
    private int statsStartY;
    private int baseButtonX;
    private int[] baseButtonYs = new int[5];

    public StatMenuScreen() {
        super(Text.of("Stats"));
        this.openedTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // MOVED: Further right to avoid overlap
        this.statsStartX = centerX + 60;
        this.statsStartY = centerY - 60;
        int spacing = 30;

        // Position Buttons next to the bars
        this.baseButtonX = this.statsStartX + 140;

        // --- Custom Buttons ---
        for (int i = 0; i < 5; i++) {
            final int statIndex = i;
            this.baseButtonYs[i] = this.statsStartY + (i * spacing) - 6;

            upgradeButtons[i] = new CustomStatButton(baseButtonX, baseButtonYs[i], 20, 20, Text.literal("+"), button -> {
                int amount = 1;
                if(Screen.hasShiftDown()) amount = 5;

                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(statIndex);
                buf.writeInt(amount);
                ClientPlayNetworking.send(PlusUltraNetwork.UPGRADE_STAT, buf);
            });
            this.addDrawableChild(upgradeButtons[i]);
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

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (client == null || client.player == null) return;

        // Smooth animation from 0.0 to 1.0
        animationProgress = MathHelper.lerp(delta * 0.1f, animationProgress, 1.0f);
        if (Math.abs(1.0f - animationProgress) < 0.001f) animationProgress = 1.0f;

        // Background Gradient (Open Design)
        context.fillGradient(0, 0, this.width, this.height, 0xCC000000, 0xAA000000);

        int centerX = width / 2;
        int centerY = height / 2;

        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) client.player).getQuirkData();

        // --- Player Model & Info (MOVED LEFT) ---
        float leftSlideOffset = (1.0f - animationProgress) * -150.0f;
        int playerX = centerX - 120 + (int)leftSlideOffset;
        int playerY = centerY + 50;

        // Render entity follows mouse
        InventoryScreen.drawEntity(context, playerX, playerY, 70, (float)playerX - mouseX, (float)playerY - mouseY - 100, client.player);

        // Display Innate Quirk
        String innateName = "None";
        for (QuirkSystem.QuirkData.QuirkInstance qi : data.getQuirks()) {
            if (qi.innate) {
                innateName = getFormalName(qi.quirkId);
                break;
            }
        }
        context.drawCenteredTextWithShadow(textRenderer, "Innate Quirk", playerX, playerY + 20, ACCENT_COLOR);
        context.drawCenteredTextWithShadow(textRenderer, innateName, playerX, playerY + 30, 0xFFDDDDDD);

        // Level / XP Bar (Above Player) - MOVED UP FURTHER
        String levelText = "LVL " + data.level;
        // Moved Y up to -185 (was -160)
        context.drawCenteredTextWithShadow(this.textRenderer, levelText, playerX, playerY - 185, 0xFFD700);

        int xpBarWidth = 100;
        int xpBarX = playerX - (xpBarWidth / 2);
        // Moved Y up to -173 (was -148)
        int xpBarY = playerY - 173;
        float xpRatio = data.experience / data.getMaxXp();
        context.fill(xpBarX, xpBarY, xpBarX + xpBarWidth, xpBarY + 2, 0x80000000);
        context.fill(xpBarX, xpBarY, xpBarX + (int)(xpBarWidth * xpRatio), xpBarY + 2, 0xFF00E5FF);


        // --- Stats List (Right Side) ---
        int points = data.statPoints;
        int spacing = 30;

        // Points Header
        if (points > 0) {
            float pulse = (float)Math.sin(System.currentTimeMillis() / 500.0) * 0.05f + 1.0f;
            int pointsAlpha = (int)(animationProgress * 255) << 24;
            context.getMatrices().push();
            context.getMatrices().translate(this.statsStartX + 65, this.statsStartY - 30, 0);
            context.getMatrices().scale(pulse, pulse, 1f);
            context.drawCenteredTextWithShadow(this.textRenderer, points + " PTS AVAILABLE", 0, 0, 0xFFFF55 | pointsAlpha);
            context.getMatrices().pop();
        }

        // Animate stats
        drawAnimatedStatRow(context, "Strength", data.strength, this.statsStartX, this.statsStartY, 0, mouseX, mouseY, points);
        drawAnimatedStatRow(context, "Endurance", data.endurance, this.statsStartX, this.statsStartY + spacing, 1, mouseX, mouseY, points);
        drawAnimatedStatRow(context, "Speed", data.speed, this.statsStartX, this.statsStartY + (spacing * 2), 2, mouseX, mouseY, points);
        drawAnimatedStatRow(context, "Stamina", data.staminaMax, this.statsStartX, this.statsStartY + (spacing * 3), 3, mouseX, mouseY, points);
        drawAnimatedStatRow(context, "Meta", data.meta, this.statsStartX, this.statsStartY + (spacing * 4), 4, mouseX, mouseY, points);

        // --- Animate Buttons ---
        float controlProgress = MathHelper.clamp((animationProgress * 1.5f), 0.0f, 1.0f);
        controlProgress = 1.0f - (float)Math.pow(1.0f - controlProgress, 3);
        float xOffset = (1.0f - controlProgress) * 100.0f;

        boolean hasPoints = points > 0;

        int[] currentStats = {data.strength, data.endurance, data.speed, data.staminaMax, data.meta};
        for (int i = 0; i < 5; i++) {
            if (upgradeButtons[i] != null) {
                boolean notMaxed = currentStats[i] < 50;
                upgradeButtons[i].visible = hasPoints && notMaxed;
                upgradeButtons[i].setX(this.baseButtonX + (int)xOffset);
            }
        }

        // Render Children
        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.Drawable d) {
                d.render(context, mouseX, mouseY, delta);
            }
        }
    }

    private void drawAnimatedStatRow(DrawContext context, String name, int value, int x, int y, int index, int mouseX, int mouseY, int pointsAvailable) {
        float rowDelay = index * 0.1f;
        float rowProgress = MathHelper.clamp((animationProgress * 1.5f) - rowDelay, 0.0f, 1.0f);
        rowProgress = 1.0f - (float)Math.pow(1.0f - rowProgress, 3);

        float xOffset = (1.0f - rowProgress) * 100.0f;
        int currentX = x + (int)xOffset;

        int alpha = (int)(rowProgress * 255);
        if (alpha < 5) return;
        int colorAlpha = alpha << 24;

        context.drawTextWithShadow(this.textRenderer, name, currentX, y, LABEL_COLOR | colorAlpha);

        String valText = String.valueOf(value);
        int valColor = (value >= 50) ? 0xFFA500 : STAT_COLOR;
        context.drawTextWithShadow(this.textRenderer, valText, currentX + 110, y, valColor | colorAlpha);

        int barX = currentX;
        int barY = y + 12;
        int barWidth = 130;
        int barHeight = 4;

        context.fill(barX, barY, barX + barWidth, barY + barHeight, STAT_BG_COLOR);

        float fillRatio = MathHelper.clamp(value / 50.0f, 0f, 1f);
        int fillWidth = (int)(barWidth * fillRatio * rowProgress);

        int barColor = STAT_COLOR;
        if (value >= 50) barColor = 0xFFA500;
        else if (pointsAvailable > 0) barColor = 0xFFFFFF;

        context.fill(barX, barY, barX + fillWidth, barY + barHeight, barColor | 0xFF000000);

        if (fillWidth > 0) {
            context.fill(barX + fillWidth - 1, barY - 1, barX + fillWidth + 1, barY + barHeight + 1, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private class CustomStatButton extends ButtonWidget {
        public CustomStatButton(int x, int y, int width, int height, Text message, PressAction onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (!this.visible) return;

            int alpha = (int)(StatMenuScreen.this.animationProgress * 255);
            if (alpha < 5) return;

            this.hovered = mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width && mouseY < this.getY() + this.height;

            int baseColor = this.isHovered() ? 0xFFFFFF : 0x00E5FF;
            int borderColor = baseColor | (alpha << 24);

            int bgBase = this.isHovered() ? 0x4000E5FF : 0x20000000;
            int bgAlpha = (bgBase >> 24) & 0xFF;
            int scaledBgAlpha = (int)(bgAlpha * StatMenuScreen.this.animationProgress);
            int bgColor = (scaledBgAlpha << 24) | (bgBase & 0x00FFFFFF);

            context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
            context.drawBorder(this.getX(), this.getY(), this.width, this.height, borderColor);

            int textX = this.getX() + (this.width / 2) - (MinecraftClient.getInstance().textRenderer.getWidth(this.getMessage()) / 2);
            int textY = this.getY() + (this.height / 2) - 4;
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, this.getMessage(), textX, textY, borderColor);
        }
    }
}