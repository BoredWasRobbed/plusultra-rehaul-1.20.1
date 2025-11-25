package net.bored.client;

import net.bored.common.PlusUltraNetwork;
import net.bored.api.QuirkSystem;
import net.bored.api.IQuirkDataAccessor;
import net.bored.common.quirks.ErasureQuirk;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class PlusUltraClientHandlers implements ClientModInitializer {

    public static KeyBinding activateKey;
    public static KeyBinding switchKey;
    public static KeyBinding menuKey;
    public static KeyBinding statsKey;
    public static KeyBinding specialKey;

    public static boolean afoSightActive = false;
    private static boolean isHoldingActivate = false; // Track key state

    @Override
    public void onInitializeClient() {
        activateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.plusultra.activate", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, "category.plusultra.main"));
        switchKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.plusultra.switch", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.plusultra.main"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.plusultra.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.plusultra.main"));
        statsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.plusultra.stats", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.plusultra.main"));
        specialKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.plusultra.special", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.plusultra.main"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            QuirkSystem.QuirkData pData = ((IQuirkDataAccessor)client.player).getQuirkData();

            // --- DANGER SENSE VISUALS (Client Side Detection) ---
            if (!pData.getQuirks().isEmpty()) {
                boolean hasDangerSense = false;
                for (var q : pData.getQuirks()) {
                    if ("plusultra:danger_sense".equals(q.quirkId)) {
                        hasDangerSense = true;
                        break;
                    }
                }

                if (hasDangerSense) {
                    double range = 15.0 + (pData.meta * 1.5);
                    Box box = client.player.getBoundingBox().expand(range);

                    // Scan for dangerous entities within range
                    List<Entity> nearbyEntities = client.world.getOtherEntities(client.player, box);

                    for (Entity e : nearbyEntities) {
                        boolean isDangerous = false;

                        if (e instanceof ProjectileEntity p) {
                            if (!p.isOnGround() && p.getOwner() != client.player) {
                                isDangerous = true;
                            }
                        } else if (e instanceof HostileEntity m) {
                            if (m.getTarget() == client.player) {
                                isDangerous = true;
                            }
                        }

                        // Apply Glow if dangerous, Remove Glow if safe (but only if we set it?
                        // Simplified: we toggle based on danger status. This might flicker if server overrides, but works for client visual)
                        if (isDangerous) {
                            e.setGlowing(true);
                        } else {
                            // Optional: Reset glow if it was set by us.
                            // Safe to set false here as Hostile Mobs don't usually glow naturally.
                            // We check distance to avoid clearing glow from far away things we didn't touch.
                            if (e.isGlowing() && e.squaredDistanceTo(client.player) < (range * range)) {
                                e.setGlowing(false);
                            }
                        }
                    }
                }
            }

            // --- ERASURE INDICATOR LOGIC ---
            if (!pData.getQuirks().isEmpty()) {
                QuirkSystem.QuirkData.QuirkInstance activeQ = pData.getQuirks().get(pData.getSelectedQuirkIndex());
                if ("plusultra:erasure".equals(activeQ.quirkId)) {
                    if (pData.getSelectedAbilityIndex() == 0 && !pData.runtimeTags.containsKey("ERASURE_ACTIVE")) {
                        LivingEntity potential = ErasureQuirk.findTargetInLineOfSight(client.player, 30.0);
                        if (potential != null) {
                            client.world.addParticle(ParticleTypes.CRIT,
                                    potential.getX(),
                                    potential.getY() + potential.getHeight() + 0.5,
                                    potential.getZ(),
                                    0, 0, 0);
                        }
                    }
                }
            }

            // --- AUTO-DISABLE AFO SIGHT IF QUIRK SWITCHED/LOST ---
            if (afoSightActive) {
                boolean shouldDisable = true;
                if (!pData.getQuirks().isEmpty()) {
                    int index = pData.getSelectedQuirkIndex();
                    if (index >= 0 && index < pData.getQuirks().size()) {
                        QuirkSystem.QuirkData.QuirkInstance active = pData.getQuirks().get(index);
                        if ("plusultra:all_for_one".equals(active.quirkId)) {
                            shouldDisable = false;
                        }
                    }
                }

                if (shouldDisable) {
                    afoSightActive = false;
                    client.player.sendMessage(Text.of("§5[All For One] §7Quirk Sight: §cDisabled"), true);
                }
            }

            // HANDLE HOLD/RELEASE FOR ACTIVATE KEY
            if (activateKey.isPressed()) {
                if (!isHoldingActivate) {
                    ClientPlayNetworking.send(PlusUltraNetwork.ACTIVATE_ABILITY, PacketByteBufs.create());
                    isHoldingActivate = true;
                }
            } else {
                if (isHoldingActivate) {
                    ClientPlayNetworking.send(PlusUltraNetwork.STOP_ABILITY, PacketByteBufs.create());
                    isHoldingActivate = false;
                }
            }

            if (menuKey.wasPressed()) {
                if (pData.getQuirks().size() >= 2) {
                    client.setScreen(new QuirkSelectionScreen());
                } else {
                    client.player.sendMessage(Text.of("§cYou need at least 2 Quirks to open the Switcher!"), true);
                }
            }
            if (statsKey.wasPressed()) {
                client.setScreen(new StatMenuScreen());
            }

            if (switchKey.wasPressed()) {
                if (client.options.sneakKey.isPressed()) {
                    ClientPlayNetworking.send(PlusUltraNetwork.TOGGLE_DESTRUCTION, PacketByteBufs.create());
                }
            }

            // AFO Sight Toggle
            if (specialKey.wasPressed()) {
                boolean isAFOSelected = false;
                if (!pData.getQuirks().isEmpty()) {
                    int index = pData.getSelectedQuirkIndex();
                    if (index >= 0 && index < pData.getQuirks().size()) {
                        if ("plusultra:all_for_one".equals(pData.getQuirks().get(index).quirkId)) {
                            isAFOSelected = true;
                        }
                    }
                }

                if (isAFOSelected) {
                    afoSightActive = !afoSightActive;
                    String status = afoSightActive ? "§aEnabled" : "§cDisabled";
                    client.player.sendMessage(Text.of("§5[All For One] §7Quirk Sight: " + status), true);
                }
            }
        });
    }

    // --- STEAL SELECTION SCREEN ---
    public static class StealSelectionScreen extends Screen {
        private final int targetId;
        private final List<String> availableQuirks;
        private double scrollOffset = 0;

        public StealSelectionScreen(int targetId, List<String> quirks) {
            super(Text.of("Steal Quirk"));
            this.targetId = targetId;
            this.availableQuirks = quirks;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context);
            int windowWidth = 200;
            int windowHeight = 150;
            int startX = (width - windowWidth) / 2;
            int startY = (height - windowHeight) / 2;
            int endX = startX + windowWidth;
            int endY = startY + windowHeight;

            context.fill(startX, startY, endX, endY, 0xAA000000);
            context.drawBorder(startX, startY, windowWidth, windowHeight, 0xFFFF0000);
            context.drawCenteredTextWithShadow(textRenderer, "Steal Which Quirk?", width / 2, startY + 10, 0xFF5555);

            int listStart = startY + 30;
            int listHeight = windowHeight - 40;
            context.enableScissor(startX, listStart, endX, listStart + listHeight);

            int currentY = listStart - (int)scrollOffset;
            for (String qId : availableQuirks) {
                if (currentY + 20 >= listStart && currentY <= listStart + listHeight) {
                    boolean isHovered = (mouseX >= startX + 10 && mouseX <= endX - 10 && mouseY >= currentY && mouseY <= currentY + 20);
                    int color = isHovered ? 0xFFFF5555 : 0xFFFFFFFF;
                    if(isHovered) context.fill(startX + 10, currentY, endX - 10, currentY + 20, 0x33FF0000);
                    context.drawText(textRenderer, QuirkSystem.getFormalName(qId), startX + 20, currentY + 6, color, true);
                }
                currentY += 25;
            }
            context.disableScissor();
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int windowWidth = 200;
            int windowHeight = 150;
            int startX = (width - windowWidth) / 2;
            int startY = (height - windowHeight) / 2;
            int listStart = startY + 30;

            if (mouseY < listStart || mouseY > startY + windowHeight - 10) return super.mouseClicked(mouseX, mouseY, button);
            int relativeY = (int)mouseY - listStart + (int)scrollOffset;
            int index = relativeY / 25;

            if (index >= 0 && index < availableQuirks.size()) {
                String chosen = availableQuirks.get(index);
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(targetId);
                buf.writeString(chosen);
                ClientPlayNetworking.send(PlusUltraNetwork.PERFORM_STEAL, buf);
                client.setScreen(null);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            int totalContentHeight = availableQuirks.size() * 25;
            int visibleHeight = 110;
            if (totalContentHeight > visibleHeight) {
                scrollOffset = MathHelper.clamp(scrollOffset - (amount * 20), 0, totalContentHeight - visibleHeight);
            }
            return true;
        }
        @Override public boolean shouldPause() { return false; }
    }

    // --- QUIRK SELECTION SCREEN ---
    public static class QuirkSelectionScreen extends Screen {
        private final long openedTime;
        private double scrollOffset = 0;

        public QuirkSelectionScreen() {
            super(Text.of("Quirk Selection"));
            this.openedTime = System.currentTimeMillis();
        }

        private List<QuirkSystem.QuirkData.QuirkInstance> getQuirks(QuirkSystem.QuirkData data) {
            List<QuirkSystem.QuirkData.QuirkInstance> visibleQuirks = new ArrayList<>();
            for(QuirkSystem.QuirkData.QuirkInstance q : data.getQuirks()) {
                if(!q.isLocked) {
                    visibleQuirks.add(q);
                }
            }
            return visibleQuirks;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (client == null || client.player == null) return;
            QuirkSystem.QuirkData data = ((IQuirkDataAccessor)client.player).getQuirkData();
            List<QuirkSystem.QuirkData.QuirkInstance> quirks = getQuirks(data);

            float animTime = (System.currentTimeMillis() - openedTime) / 200f;
            animTime = Math.min(animTime, 1.0f);
            float easeOut = 1.0f - (1.0f - animTime) * (1.0f - animTime);

            int windowWidth = 200;
            int windowHeight = Math.min((quirks.size() * 25) + 40, 190);
            int centerX = width / 2;
            int centerY = height / 2;
            int animatedY = centerY - (int)((1.0f - easeOut) * 20);

            int startX = centerX - (windowWidth / 2);
            int startY = animatedY - (windowHeight / 2);
            int endX = startX + windowWidth;
            int endY = startY + windowHeight;

            context.fill(startX, startY, endX, endY, 0xAA000000);
            context.drawBorder(startX, startY, windowWidth, windowHeight, 0xFF00E5FF);
            context.drawCenteredTextWithShadow(textRenderer, "Select Active Quirk", centerX, startY + 10, 0xFFFFFF);

            int listStart = startY + 30;
            int listHeight = windowHeight - 40;
            context.enableScissor(startX, listStart, endX, listStart + listHeight);

            int currentY = listStart - (int)scrollOffset;

            for (int i = 0; i < quirks.size(); i++) {
                QuirkSystem.QuirkData.QuirkInstance qi = quirks.get(i);
                if (currentY + 20 >= listStart && currentY <= listStart + listHeight) {
                    boolean isHovered = (mouseX >= startX + 10 && mouseX <= endX - 10 && mouseY >= currentY && mouseY <= currentY + 20);

                    int realIndex = data.getQuirks().indexOf(qi);
                    boolean isSelected = (realIndex == data.getSelectedQuirkIndex());

                    int itemColor = 0xFFFFFF;
                    int backgroundColor = 0x00000000;

                    if (isSelected) {
                        backgroundColor = 0x55FFD700;
                        itemColor = 0xFFD700;
                    } else if (isHovered) {
                        backgroundColor = 0x33FFFFFF;
                    }

                    context.fill(startX + 10, currentY, endX - 10, currentY + 20, backgroundColor);

                    String formalName = QuirkSystem.getFormalName(qi);
                    if (qi.count > 1) formalName += " x" + qi.count;
                    if (qi.innate) formalName += " (Innate)";
                    if (isSelected) formalName = "▶ " + formalName;

                    context.drawText(textRenderer, formalName, startX + 20, currentY + 6, itemColor, true);
                }
                currentY += 25;
            }
            context.disableScissor();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (client == null || client.player == null) return false;
            QuirkSystem.QuirkData data = ((IQuirkDataAccessor)client.player).getQuirkData();
            List<QuirkSystem.QuirkData.QuirkInstance> quirks = getQuirks(data);

            int windowWidth = 200;
            int windowHeight = Math.min((quirks.size() * 25) + 40, 190);
            int startX = (width - windowWidth) / 2;
            int startY = (height - windowHeight) / 2;
            int listStart = startY + 30;

            if (mouseY < listStart || mouseY > listStart + (windowHeight - 40)) return super.mouseClicked(mouseX, mouseY, button);
            int relativeY = (int)mouseY - listStart + (int)scrollOffset;
            int visualIndex = relativeY / 25;

            if (visualIndex >= 0 && visualIndex < quirks.size()) {
                QuirkSystem.QuirkData.QuirkInstance selectedInstance = quirks.get(visualIndex);
                int realIndex = data.getQuirks().indexOf(selectedInstance);

                if (realIndex != -1) {
                    if (button == 0) {
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeInt(realIndex);
                        ClientPlayNetworking.send(PlusUltraNetwork.SWITCH_QUIRK, buf);
                        client.setScreen(null);
                        return true;
                    }
                    else if (button == 1) {
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeInt(realIndex);
                        ClientPlayNetworking.send(PlusUltraNetwork.TOGGLE_ABILITY, buf);
                        client.setScreen(null);
                        return true;
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            if (client == null || client.player == null) return false;
            QuirkSystem.QuirkData data = ((IQuirkDataAccessor)client.player).getQuirkData();
            List<QuirkSystem.QuirkData.QuirkInstance> quirks = getQuirks(data);

            int totalContentHeight = quirks.size() * 25;
            int windowHeight = Math.min((quirks.size() * 25) + 40, 190);
            int visibleHeight = windowHeight - 40;

            if (totalContentHeight > visibleHeight) {
                scrollOffset = MathHelper.clamp(scrollOffset - (amount * 20), 0, totalContentHeight - visibleHeight);
            }
            return true;
        }
        @Override public boolean shouldPause() { return false; }
    }
}