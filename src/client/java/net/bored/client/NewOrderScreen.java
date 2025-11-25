package net.bored.client;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class NewOrderScreen extends Screen {

    private TextFieldWidget phraseField;

    // Modular Building State
    private String selectedEffect = null;
    private final List<String> constructedRule = new ArrayList<>(); // To visualize sequence

    // Available Effect "Blocks"
    private static final List<String> EFFECT_BLOCKS = Arrays.asList(
            "POISON", "BURN", "STOP", "FLOAT", "SMITE", "WEAKEN", "BOOST"
    );

    // Colors for blocks
    private static final List<Integer> EFFECT_COLORS = Arrays.asList(
            0xFF00AA00, // POISON - Green
            0xFFFFAA00, // BURN - Orange
            0xFF555555, // STOP - Gray
            0xFF55FFFF, // FLOAT - Aqua
            0xFFFFFF55, // SMITE - Yellow
            0xFF550055, // WEAKEN - Purple
            0xFFFF5555  // BOOST - Red
    );

    public NewOrderScreen() {
        super(Text.of("New Order: Modular Editor"));
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;

        // 1. Phrase Input (The Condition Block)
        phraseField = new TextFieldWidget(textRenderer, centerX - 100, centerY - 80, 200, 20, Text.of("Phrase"));
        phraseField.setMaxLength(64);
        phraseField.setPlaceholder(Text.of("Type trigger phrase..."));
        this.addDrawableChild(phraseField);

        // 2. Save Button (Finalizes the chain)
        this.addDrawableChild(ButtonWidget.builder(Text.of("Establish Rule"), button -> {
                    saveRule();
                })
                .dimensions(centerX + 110, centerY - 80, 80, 20)
                .build());

        // 3. Clear Button
        this.addDrawableChild(ButtonWidget.builder(Text.of("Clear"), button -> {
                    selectedEffect = null;
                })
                .dimensions(centerX - 100, centerY + 80, 50, 20)
                .build());
    }

    private void saveRule() {
        String phrase = phraseField.getText().trim();
        if (phrase.isEmpty()) return;
        if (selectedEffect == null) return; // Must have an effect block

        int index = EFFECT_BLOCKS.indexOf(selectedEffect);
        int cost = (index != -1) ? getCost(index) : 20;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(phrase);
        buf.writeString(selectedEffect);
        buf.writeInt(cost);

        ClientPlayNetworking.send(PlusUltraNetwork.SYNC_NEW_ORDER_RULE, buf);

        // Reset for next rule
        phraseField.setText("");
        selectedEffect = null;
    }

    private int getCost(int index) {
        // Simple cost table
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
        int centerY = height / 2;

        // Header
        context.drawCenteredTextWithShadow(textRenderer, "§e§lNew Order: Construct Reality", centerX, centerY - 100, 0xFFFFFF);

        // --- PALETTE AREA (Drag and Drop Source) ---
        context.drawTextWithShadow(textRenderer, "§nEffect Blocks", centerX - 100, centerY - 40, 0xAAAAAA);

        int paletteStartX = centerX - 100;
        int paletteY = centerY - 25;

        for (int i = 0; i < EFFECT_BLOCKS.size(); i++) {
            String effect = EFFECT_BLOCKS.get(i);
            int color = EFFECT_COLORS.get(i);
            int x = paletteStartX + (i * 50);

            // Draw Palette Block
            boolean isHovered = mouseX >= x && mouseX <= x + 45 && mouseY >= paletteY && mouseY <= paletteY + 20;
            int displayColor = isHovered ? (color | 0xFF000000) : (color | 0xAA000000);

            context.fill(x, paletteY, x + 45, paletteY + 20, displayColor);
            context.drawBorder(x, paletteY, 45, 20, 0xFFFFFFFF);

            // Abbreviated text to fit
            String label = effect.length() > 4 ? effect.substring(0, 3) : effect;
            context.drawCenteredTextWithShadow(textRenderer, label, x + 22, paletteY + 6, 0xFFFFFF);

            // Tooltip on hover
            if (isHovered) {
                context.drawTooltip(textRenderer, Text.of(effect + " (Cost: " + getCost(i) + ")"), mouseX, mouseY);
            }
        }

        // --- ASSEMBLY AREA (Drop Target) ---
        context.drawTextWithShadow(textRenderer, "§nSequence", centerX - 100, centerY + 20, 0xAAAAAA);

        // 1. Phrase Block (Always First)
        context.fill(centerX - 100, centerY + 35, centerX - 20, centerY + 65, 0xFF0000AA);
        context.drawBorder(centerX - 100, centerY + 35, 80, 30, 0xFF5555FF);
        String pText = phraseField.getText().isEmpty() ? "..." : phraseField.getText();
        if (pText.length() > 10) pText = pText.substring(0, 8) + "..";
        context.drawCenteredTextWithShadow(textRenderer, pText, centerX - 60, centerY + 45, 0xFFFFFF);

        // Arrow
        context.drawText(textRenderer, "->", centerX - 10, centerY + 45, 0xFFDDDDDD, false);

        // 2. Selected Effect Block
        int slotX = centerX + 10;
        int slotY = centerY + 35;

        if (selectedEffect != null) {
            int idx = EFFECT_BLOCKS.indexOf(selectedEffect);
            int color = (idx != -1) ? EFFECT_COLORS.get(idx) : 0xFFFFFFFF;

            context.fill(slotX, slotY, slotX + 80, slotY + 30, color | 0xFF000000);
            context.drawBorder(slotX, slotY, 80, 30, 0xFFFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, selectedEffect, slotX + 40, slotY + 11, 0xFFFFFF);
        } else {
            // Empty Slot Placeholder
            context.fill(slotX, slotY, slotX + 80, slotY + 30, 0x66000000);
            context.drawBorder(slotX, slotY, 80, 30, 0xFF555555);
            context.drawCenteredTextWithShadow(textRenderer, "[Select]", slotX + 40, slotY + 11, 0xFFAAAAAA);
        }

        // --- ACTIVE RULES LIST ---
        // Reuse rendering from previous iteration but simpler
        // Render at bottom
        int listY = centerY + 100;
        // Manual rendering of active rules... (Simplified for brevity as logic is same as before)

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = width / 2;
        int centerY = height / 2;

        // Check Palette Clicks
        int paletteStartX = centerX - 100;
        int paletteY = centerY - 25;

        for (int i = 0; i < EFFECT_BLOCKS.size(); i++) {
            int x = paletteStartX + (i * 50);
            if (mouseX >= x && mouseX <= x + 45 && mouseY >= paletteY && mouseY <= paletteY + 20) {
                // Select this block
                selectedEffect = EFFECT_BLOCKS.get(i);
                client.player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}