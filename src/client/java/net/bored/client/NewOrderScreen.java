package net.bored.client;

import net.bored.common.PlusUltraNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NewOrderScreen extends Screen {

    private TextFieldWidget phraseField;

    // UI Colors
    private static final int ACCENT_COLOR = 0xFF00E5FF;
    private static final int ACCENT_HOVER = 0xFFFFFFFF;
    private static final int GOLD_COLOR = 0xFFD700;
    private static final int BG_COLOR = 0xCC000000;
    private static final int SLOT_COLOR = 0x40000000;
    private static final int PALETTE_BG = 0x99000000;

    // Drag & Drop State
    private DraggableBlock draggingBlock = null;
    private double dragOffsetX, dragOffsetY;

    // Scrolling
    private double scrollAmount = 0;
    private boolean isScrolling = false;

    // Categories
    private final List<DraggableBlock> targetPalette = new ArrayList<>();
    private final List<DraggableBlock> effectPalette = new ArrayList<>();

    // Slots
    private DraggableBlock targetSlot = null;
    private DraggableBlock effectSlot = null;

    // Definitions
    private static final List<String> TARGET_TYPES = Arrays.asList(
            "FOCUSED", "TOUCH"
    );
    private static final List<Integer> TARGET_COLORS = Arrays.asList(
            0xFF00AAAA, // FOCUSED (Cyan)
            0xFFFFAA00  // TOUCH (Orange)
    );

    private static final List<String> EFFECT_TYPES = Arrays.asList(
            "POISON", "BURN", "STOP", "FLOAT", "SMITE", "WEAKEN", "BOOST",
            "SHIELD", "HEAL", "LAUNCH", "GLOW", "WITHER"
    );
    private static final List<Integer> EFFECT_COLORS = Arrays.asList(
            0xFF00AA00, 0xFFFFAA00, 0xFF555555, 0xFF55FFFF, 0xFFFFFF55, 0xFF550055, 0xFFFF5555,
            0xFF5555FF, 0xFFFF55FF, 0xFF888888, 0xFFDDDDDD, 0xFF333333
    );

    public NewOrderScreen() {
        super(Text.of("New Order: Drag & Drop Editor"));
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;

        // Phrase Input
        phraseField = new TextFieldWidget(textRenderer, centerX - 60, centerY - 70, 160, 20, Text.of("Phrase"));
        phraseField.setMaxLength(64);
        phraseField.setPlaceholder(Text.of("If I say..."));
        phraseField.setDrawsBackground(true);
        phraseField.setEditableColor(ACCENT_COLOR);
        this.addDrawableChild(phraseField);

        // Save Button
        this.addDrawableChild(new CustomButton(centerX - 40, centerY + 80, 120, 20, Text.of("Establish Rule"), button -> saveRule()));

        // Initialize Palettes
        targetPalette.clear();
        for (int i = 0; i < TARGET_TYPES.size(); i++) {
            targetPalette.add(new DraggableBlock(TARGET_TYPES.get(i), TARGET_COLORS.get(i), true, 0, 0));
        }

        effectPalette.clear();
        for (int i = 0; i < EFFECT_TYPES.size(); i++) {
            effectPalette.add(new DraggableBlock(EFFECT_TYPES.get(i), EFFECT_COLORS.get(i), false, 0, 0));
        }
    }

    private void saveRule() {
        String phrase = phraseField.getText().trim();
        if (phrase.isEmpty()) return;
        if (targetSlot == null || effectSlot == null) return;

        String target = targetSlot.type;
        String effect = effectSlot.type;
        int cost = getCost(target, effect);

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(phrase);
        buf.writeString(target);
        buf.writeString(effect);
        buf.writeInt(cost);

        ClientPlayNetworking.send(PlusUltraNetwork.SYNC_NEW_ORDER_RULE, buf);

        // Reset
        phraseField.setText("");
        targetSlot = null;
        effectSlot = null;
    }

    private int getCost(String target, String effect) {
        int base = 20;
        // Effect Cost
        switch (effect) {
            case "SMITE": base += 20; break;
            case "HEAL": base += 15; break;
            case "SHIELD": base += 5; break;
            case "GLOW": base -= 10; break;
            case "WEAKEN": base -= 5; break;
        }
        // Target Multiplier
        // Touch is generally cheaper/standard because it requires a hit
        if ("FOCUSED".equals(target)) base += 10;

        return Math.max(5, base);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        // --- LEFT PALETTE BACKGROUND ---
        int paletteWidth = 100;
        int paletteX = 10;
        int paletteY = 20;
        int paletteHeight = height - 40;

        context.fill(paletteX, paletteY, paletteX + paletteWidth, paletteY + paletteHeight, PALETTE_BG);
        context.drawBorder(paletteX, paletteY, paletteWidth, paletteHeight, 0xFF555555);
        context.drawCenteredTextWithShadow(textRenderer, "§nConstructs", paletteX + (paletteWidth/2), paletteY + 5, ACCENT_COLOR);

        // --- SCROLLING CONTENT ---
        int contentX = paletteX + 10;
        int startY = paletteY + 20 - (int)scrollAmount;
        int blockHeight = 25;
        int spacing = 5;

        context.enableScissor(paletteX, paletteY + 20, paletteX + paletteWidth, paletteY + paletteHeight - 5);

        // Render Targets Header
        context.drawTextWithShadow(textRenderer, "Targets", contentX, startY, GOLD_COLOR);
        int currentY = startY + 15;

        for (DraggableBlock block : targetPalette) {
            block.x = contentX;
            block.y = currentY;
            block.render(context, textRenderer, mouseX, mouseY, false);
            currentY += blockHeight + spacing;
        }

        // Render Effects Header
        currentY += 10;
        context.drawTextWithShadow(textRenderer, "Effects", contentX, currentY, ACCENT_COLOR);
        currentY += 15;

        for (DraggableBlock block : effectPalette) {
            block.x = contentX;
            block.y = currentY;
            block.render(context, textRenderer, mouseX, mouseY, false);
            currentY += blockHeight + spacing;
        }

        context.disableScissor();

        // Scrollbar
        int totalContentHeight = (targetPalette.size() + effectPalette.size()) * (blockHeight + spacing) + 60;
        if (totalContentHeight > paletteHeight - 20) {
            int barHeight = (int)((float)(paletteHeight - 20) / totalContentHeight * (paletteHeight - 20));
            int barY = paletteY + 20 + (int)((scrollAmount / (totalContentHeight - (paletteHeight - 20))) * (paletteHeight - 20 - barHeight));
            context.fill(paletteX + paletteWidth - 6, barY, paletteX + paletteWidth - 2, barY + barHeight, 0xFF888888);
        }

        // --- MAIN SEQUENCE AREA ---
        int centerX = width / 2;
        int centerY = height / 2;

        context.drawCenteredTextWithShadow(textRenderer, "§e§lNew Order", centerX + 20, 20, 0xFFFFFF);

        // 1. Phrase Input Label
        context.drawText(textRenderer, "Condition:", centerX - 110, centerY - 65, 0xFFAAAAAA, true);

        // Arrow
        context.drawCenteredTextWithShadow(textRenderer, "⬇", centerX + 20, centerY - 40, 0xFFFFFFFF);

        // 2. Target Slot
        int slotW = 120;
        int slotH = 25;
        int targetSlotX = centerX - 40;
        int targetSlotY = centerY - 20;

        drawSlot(context, targetSlotX, targetSlotY, slotW, slotH, "Target", targetSlot);

        // Arrow
        context.drawCenteredTextWithShadow(textRenderer, "⬇", centerX + 20, centerY + 10, 0xFFFFFFFF);

        // 3. Effect Slot
        int effectSlotX = centerX - 40;
        int effectSlotY = centerY + 30;

        drawSlot(context, effectSlotX, effectSlotY, slotW, slotH, "Effect", effectSlot);

        // Cost Display
        if (targetSlot != null && effectSlot != null) {
            int cost = getCost(targetSlot.type, effectSlot.type);
            context.drawTextWithShadow(textRenderer, "Stamina Cost: " + cost, centerX + 90, centerY + 35, cost > 50 ? 0xFFFF5555 : 0xFF55FF55);
        }

        // Trash Bin
        int trashX = width - 60;
        int trashY = height - 60;
        boolean hoveringTrash = draggingBlock != null && mouseX >= trashX && mouseX <= trashX + 40 && mouseY >= trashY && mouseY <= trashY + 40;
        int trashColor = hoveringTrash ? 0xFFFF0000 : 0xFF550000;
        context.fill(trashX, trashY, trashX + 40, trashY + 40, trashColor | 0x80000000);
        context.drawBorder(trashX, trashY, 40, 40, hoveringTrash ? 0xFFFFFFFF : 0xFFFF0000);
        context.drawCenteredTextWithShadow(textRenderer, "X", trashX + 20, trashY + 15, 0xFFFFFFFF);

        // Dragging Block
        if (draggingBlock != null) {
            draggingBlock.render(context, textRenderer, mouseX, mouseY, true);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawSlot(DrawContext context, int x, int y, int w, int h, String label, DraggableBlock content) {
        context.fill(x, y, x + w, y + h, SLOT_COLOR);
        int borderColor = (content != null) ? (content.isTarget ? 0xFF5555FF : 0xFF00AA00) : 0xFF555555;
        context.drawBorder(x, y, w, h, borderColor);

        if (content != null) {
            content.x = x + 2;
            content.y = y + 2;
            content.width = w - 4; // Expand to fill
            content.render(context, textRenderer, 0, 0, true); // Render as "dragging" style (full brightness)
        } else {
            context.drawCenteredTextWithShadow(textRenderer, "[" + label + "]", x + (w/2), y + 8, 0xFF777777);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check Palette (Targets)
            if (checkPaletteClick(targetPalette, mouseX, mouseY)) return true;
            // Check Palette (Effects)
            if (checkPaletteClick(effectPalette, mouseX, mouseY)) return true;

            // Check Slots (Pickup)
            if (targetSlot != null && targetSlot.isHovered(mouseX, mouseY)) {
                draggingBlock = targetSlot;
                targetSlot = null;
                startDrag(mouseX, mouseY);
                return true;
            }
            if (effectSlot != null && effectSlot.isHovered(mouseX, mouseY)) {
                draggingBlock = effectSlot;
                effectSlot = null;
                startDrag(mouseX, mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean checkPaletteClick(List<DraggableBlock> list, double mx, double my) {
        // Only check if inside palette scissor area
        if (mx < 10 || mx > 110 || my < 20 || my > height - 20) return false;

        for (DraggableBlock b : list) {
            if (b.isHovered(mx, my)) {
                // Clone for dragging
                draggingBlock = new DraggableBlock(b.type, b.color, b.isTarget, (int)mx, (int)my);
                startDrag(mx, my);
                return true;
            }
        }
        return false;
    }

    private void startDrag(double mx, double my) {
        draggingBlock.width = 80; // Set standard width for dragging
        dragOffsetX = 40;
        dragOffsetY = 10;
        draggingBlock.x = (int)(mx - dragOffsetX);
        draggingBlock.y = (int)(my - dragOffsetY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingBlock != null) {
            int centerX = width / 2;
            int centerY = height / 2;

            // Target Slot Hitbox
            if (isOverSlot(mouseX, mouseY, centerX - 40, centerY - 20, 120, 25)) {
                if (draggingBlock.isTarget) {
                    targetSlot = draggingBlock;
                }
            }
            // Effect Slot Hitbox
            else if (isOverSlot(mouseX, mouseY, centerX - 40, centerY + 30, 120, 25)) {
                if (!draggingBlock.isTarget) {
                    effectSlot = draggingBlock;
                }
            }

            draggingBlock = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isOverSlot(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingBlock != null) {
            draggingBlock.x = (int)(mouseX - dragOffsetX);
            draggingBlock.y = (int)(mouseY - dragOffsetY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int paletteHeight = height - 40;
        int totalContentHeight = (targetPalette.size() + effectPalette.size()) * 30 + 60;

        if (mouseX >= 10 && mouseX <= 110 && mouseY >= 20 && mouseY <= height - 20) {
            double maxScroll = Math.max(0, totalContentHeight - paletteHeight);
            scrollAmount = MathHelper.clamp(scrollAmount - (amount * 20), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    // --- Helper Classes ---
    private static class DraggableBlock {
        String type;
        int color;
        boolean isTarget;
        int x, y;
        int width = 80;
        int height = 20;

        public DraggableBlock(String type, int color, boolean isTarget, int x, int y) {
            this.type = type;
            this.color = color;
            this.isTarget = isTarget;
            this.x = x;
            this.y = y;
        }

        public void render(DrawContext context, net.minecraft.client.font.TextRenderer textRenderer, int mouseX, int mouseY, boolean isDragging) {
            // If in palette (not dragging), check hover
            boolean hover = !isDragging && isHovered(mouseX, mouseY);
            int renderColor = color | 0xFF000000;

            context.fill(x, y, x + width, y + height, renderColor);
            context.drawBorder(x, y, width, height, hover ? 0xFFFFFFFF : 0xFF000000);

            context.drawCenteredTextWithShadow(textRenderer, type, x + (width/2), y + 6, 0xFFFFFFFF);
        }

        public boolean isHovered(double mx, double my) {
            return mx >= x && mx <= x + width && my >= y && my <= y + height;
        }
    }

    private class CustomButton extends ButtonWidget {
        public CustomButton(int x, int y, int width, int height, Text message, PressAction onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            this.hovered = mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width && mouseY < this.getY() + this.height;
            int borderColor = this.isHovered() ? ACCENT_HOVER : ACCENT_COLOR;
            int bgBase = this.isHovered() ? 0x4000E5FF : 0x20000000;

            context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgBase);
            context.drawBorder(this.getX(), this.getY(), this.width, this.height, borderColor);

            int textX = this.getX() + (this.width / 2) - (MinecraftClient.getInstance().textRenderer.getWidth(this.getMessage()) / 2);
            int textY = this.getY() + (this.height / 2) - 4;
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, this.getMessage(), textX, textY, borderColor);
        }
    }
}