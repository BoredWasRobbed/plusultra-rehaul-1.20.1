package net.bored.client;

import net.bored.common.PlusUltraNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NewOrderScreen extends Screen {

    private TextFieldWidget phraseField;

    // Drag & Drop State
    private DraggableBlock draggingBlock = null;
    private double dragOffsetX, dragOffsetY;

    private final List<DraggableBlock> paletteBlocks = new ArrayList<>();
    private final List<DraggableBlock> sequenceBlocks = new ArrayList<>();

    private static final int PALETTE_Y = 60;
    private static final int SEQUENCE_Y = 140;

    // Available Effect "Blocks"
    private static final List<String> EFFECT_TYPES = Arrays.asList(
            "POISON", "BURN", "STOP", "FLOAT", "SMITE", "WEAKEN", "BOOST"
    );

    // Colors for blocks
    private static final List<Integer> EFFECT_COLORS = Arrays.asList(
            0xFF00AA00, // POISON
            0xFFFFAA00, // BURN
            0xFF555555, // STOP
            0xFF55FFFF, // FLOAT
            0xFFFFFF55, // SMITE
            0xFF550055, // WEAKEN
            0xFFFF5555  // BOOST
    );

    public NewOrderScreen() {
        super(Text.of("New Order: Drag & Drop Editor"));
    }

    @Override
    protected void init() {
        int centerX = width / 2;

        // 1. Phrase Input (The "If..." Condition)
        phraseField = new TextFieldWidget(textRenderer, centerX - 100, SEQUENCE_Y - 30, 200, 20, Text.of("Phrase"));
        phraseField.setMaxLength(64);
        phraseField.setPlaceholder(Text.of("If you say..."));
        this.addDrawableChild(phraseField);

        // 2. Save Button
        this.addDrawableChild(ButtonWidget.builder(Text.of("Establish Rule"), button -> {
                    saveRule();
                })
                .dimensions(centerX - 50, SEQUENCE_Y + 50, 100, 20)
                .build());

        // Initialize Palette Blocks
        paletteBlocks.clear();
        int startX = centerX - ((EFFECT_TYPES.size() * 55) / 2);
        for (int i = 0; i < EFFECT_TYPES.size(); i++) {
            paletteBlocks.add(new DraggableBlock(EFFECT_TYPES.get(i), EFFECT_COLORS.get(i), startX + (i * 55), PALETTE_Y));
        }
    }

    private void saveRule() {
        String phrase = phraseField.getText().trim();
        if (phrase.isEmpty()) return;
        if (sequenceBlocks.isEmpty()) return; // Must have an effect

        // For now, take the first block in sequence as the effect
        // Future: Iterate list for complex chains
        String effect = sequenceBlocks.get(0).type;
        int cost = getCost(EFFECT_TYPES.indexOf(effect));

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(phrase);
        buf.writeString(effect);
        buf.writeInt(cost);

        ClientPlayNetworking.send(PlusUltraNetwork.SYNC_NEW_ORDER_RULE, buf);

        // Reset
        phraseField.setText("");
        sequenceBlocks.clear();
    }

    private int getCost(int index) {
        switch (index) {
            case 0: return 15; // Poison
            case 1: return 20; // Burn
            case 2: return 25; // Stop
            case 3: return 15; // Float
            case 4: return 30; // Smite
            case 5: return 10; // Weaken
            case 6: return 20; // Boost
            default: return 20;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        int centerX = width / 2;

        // Headers
        context.drawCenteredTextWithShadow(textRenderer, "§e§lNew Order: Reality Construction", centerX, 20, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "§nEffect Palette", centerX - 100, PALETTE_Y - 15, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, "§nRule Sequence", centerX - 100, SEQUENCE_Y - 45, 0xAAAAAA);

        // Draw Palette
        for (DraggableBlock block : paletteBlocks) {
            block.render(context, textRenderer, mouseX, mouseY, false);
        }

        // Draw Sequence Slots (Visual placeholders)
        int slotX = centerX + 110; // To the right of the text field
        int slotY = SEQUENCE_Y - 30;

        // Draw Connector Arrow from phrase
        context.drawText(textRenderer, "then ->", centerX + 105, slotY + 6, 0xFFDDDDDD, false);

        // Draw Drop Slot
        context.fill(slotX + 40, slotY, slotX + 120, slotY + 20, 0x66000000);
        context.drawBorder(slotX + 40, slotY, 80, 20, 0xFF555555);
        if (sequenceBlocks.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "[Drop Effect]", slotX + 80, slotY + 6, 0xFFAAAAAA);
        }

        // Draw Sequence Blocks (Snapped)
        for (int i = 0; i < sequenceBlocks.size(); i++) {
            DraggableBlock block = sequenceBlocks.get(i);
            // Force position to slot
            block.x = slotX + 40 + (i * 85);
            block.y = slotY;
            block.render(context, textRenderer, mouseX, mouseY, true);
        }

        // Draw Dragging Block (On top)
        if (draggingBlock != null) {
            draggingBlock.render(context, textRenderer, mouseX, mouseY, true);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left Click
            // Check Palette
            for (DraggableBlock block : paletteBlocks) {
                if (block.isHovered(mouseX, mouseY)) {
                    // Create a copy to drag
                    draggingBlock = new DraggableBlock(block.type, block.color, (int)mouseX, (int)mouseY);
                    dragOffsetX = mouseX - block.x;
                    dragOffsetY = mouseY - block.y; // Actually center it on mouse for better feel
                    draggingBlock.x = (int)mouseX - 40;
                    draggingBlock.y = (int)mouseY - 10;
                    dragOffsetX = 40;
                    dragOffsetY = 10;
                    return true;
                }
            }

            // Check Sequence (to pick up and move/remove)
            for (int i = 0; i < sequenceBlocks.size(); i++) {
                DraggableBlock block = sequenceBlocks.get(i);
                if (block.isHovered(mouseX, mouseY)) {
                    draggingBlock = block;
                    sequenceBlocks.remove(i);
                    dragOffsetX = mouseX - block.x;
                    dragOffsetY = mouseY - block.y;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingBlock != null) {
            // Check drop zone
            int centerX = width / 2;
            int slotX = centerX + 150; // Approximate drop area center
            int slotY = SEQUENCE_Y - 20;

            // Simple radius check for drop
            if (Math.abs(mouseX - slotX) < 100 && Math.abs(mouseY - slotY) < 50) {
                // Dropped in sequence
                if (sequenceBlocks.size() < 1) { // Limit to 1 effect for now as per backend support
                    sequenceBlocks.add(draggingBlock);
                }
            }
            // If dropped elsewhere, it disappears (deleted)

            draggingBlock = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
    public boolean shouldPause() { return false; }

    // --- Helper Class ---
    private static class DraggableBlock {
        String type;
        int color;
        int x, y;
        int width = 80;
        int height = 20;

        public DraggableBlock(String type, int color, int x, int y) {
            this.type = type;
            this.color = color;
            this.x = x;
            this.y = y;
        }

        public void render(DrawContext context, net.minecraft.client.font.TextRenderer textRenderer, int mouseX, int mouseY, boolean isDragging) {
            // Scale slightly if dragging
            if (isDragging) {
                // context.getMatrices().scale(1.1f, 1.1f, 1f);
            }

            context.fill(x, y, x + width, y + height, color | 0xFF000000);
            context.drawBorder(x, y, width, height, 0xFFFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, type, x + (width/2), y + 6, 0xFFFFFF);
        }

        public boolean isHovered(double mx, double my) {
            return mx >= x && mx <= x + width && my >= y && my <= y + height;
        }
    }
}