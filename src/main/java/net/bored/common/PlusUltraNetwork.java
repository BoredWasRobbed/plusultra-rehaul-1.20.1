package net.bored.common;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.bored.common.quirks.NewOrderQuirk;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public class PlusUltraNetwork {
    public static final Identifier ACTIVATE_ABILITY = new Identifier("plusultra", "activate_ability");
    public static final Identifier STOP_ABILITY = new Identifier("plusultra", "stop_ability");
    public static final Identifier SWITCH_ABILITY = new Identifier("plusultra", "switch_ability");
    public static final Identifier SWITCH_QUIRK = new Identifier("plusultra", "switch_quirk");
    public static final Identifier SYNC_DATA = new Identifier("plusultra", "sync_data");
    public static final Identifier OPEN_STEAL_SELECTION = new Identifier("plusultra", "open_steal_selection");
    public static final Identifier PERFORM_STEAL = new Identifier("plusultra", "perform_steal");
    public static final Identifier TOGGLE_ABILITY = new Identifier("plusultra", "toggle_ability");
    public static final Identifier UPGRADE_STAT = new Identifier("plusultra", "upgrade_stat");
    public static final Identifier ADJUST_PERCENTAGE = new Identifier("plusultra", "adjust_percentage");
    public static final Identifier TOGGLE_DESTRUCTION = new Identifier("plusultra", "toggle_destruction");

    // NEW ORDER PACKETS
    public static final Identifier SYNC_NEW_ORDER_RULE = new Identifier("plusultra", "sync_new_order_rule");
    public static final Identifier REMOVE_NEW_ORDER_RULE = new Identifier("plusultra", "remove_new_order_rule");

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ACTIVATE_ABILITY, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                QuirkSystem.QuirkData data = ((IQuirkDataAccessor)player).getQuirkData();
                if (data.getQuirks().isEmpty()) return;

                int qIndex = data.getSelectedQuirkIndex();
                if (qIndex < 0 || qIndex >= data.getQuirks().size()) return;

                QuirkSystem.QuirkData.QuirkInstance instance = data.getQuirks().get(qIndex);
                QuirkSystem.Quirk quirk = QuirkRegistry.get(new Identifier(instance.quirkId));

                if (quirk != null) {
                    int aIndex = data.getSelectedAbilityIndex();
                    List<QuirkSystem.Ability> abilities = quirk.getAbilities(instance);

                    if (aIndex >= 0 && aIndex < abilities.size()) {
                        QuirkSystem.Ability ability = abilities.get(aIndex);

                        if (ability.canUse(data, instance)) {
                            ability.onActivate(player, data, instance);
                            sync(player);
                        } else {
                            if (instance.isLocked) player.sendMessage(Text.of("§cThis Quirk is currently locked!"), true);
                            else if (data.level < ability.getRequiredLevel()) player.sendMessage(Text.of("§cLevel too low!"), true);
                            else if (!ability.isReady(instance)) player.sendMessage(Text.of("§cCooldown!"), true);
                            else player.sendMessage(Text.of("§cNot enough Stamina!"), true);
                        }
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(STOP_ABILITY, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                QuirkSystem.QuirkData data = ((IQuirkDataAccessor)player).getQuirkData();
                if (data.getQuirks().isEmpty()) return;

                int qIndex = data.getSelectedQuirkIndex();
                if (qIndex < 0 || qIndex >= data.getQuirks().size()) return;

                QuirkSystem.QuirkData.QuirkInstance instance = data.getQuirks().get(qIndex);
                QuirkSystem.Quirk quirk = QuirkRegistry.get(new Identifier(instance.quirkId));

                if (quirk != null) {
                    int aIndex = data.getSelectedAbilityIndex();
                    List<QuirkSystem.Ability> abilities = quirk.getAbilities(instance);

                    if (aIndex >= 0 && aIndex < abilities.size()) {
                        QuirkSystem.Ability ability = abilities.get(aIndex);
                        ability.onRelease(player, data, instance);
                        sync(player);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_ABILITY, (server, player, handler, buf, responseSender) -> {
            int qIndex = buf.readInt();
            server.execute(() -> {
                QuirkSystem.QuirkData data = ((IQuirkDataAccessor)player).getQuirkData();
                if (qIndex < 0 || qIndex >= data.getQuirks().size()) return;

                QuirkSystem.QuirkData.QuirkInstance instance = data.getQuirks().get(qIndex);
                QuirkSystem.Quirk quirk = QuirkRegistry.get(new Identifier(instance.quirkId));

                if (quirk != null) {
                    for(QuirkSystem.Ability ability : quirk.getAbilities(instance)) {
                        if(ability.getType() == QuirkSystem.AbilityType.TOGGLE) {
                            if (ability.canUse(data, instance)) {
                                ability.onActivate(player, data, instance);
                                sync(player);
                            } else if (instance.isLocked) {
                                player.sendMessage(Text.of("§cLocked!"), true);
                            }
                            return;
                        }
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SWITCH_ABILITY, (server, player, handler, buf, responseSender) -> {
            int direction = buf.readInt();
            server.execute(() -> {
                QuirkSystem.QuirkData data = ((IQuirkDataAccessor)player).getQuirkData();
                if (data.getQuirks().isEmpty()) return;

                QuirkSystem.QuirkData.QuirkInstance instance = data.getQuirks().get(data.getSelectedQuirkIndex());
                QuirkSystem.Quirk quirk = QuirkRegistry.get(new Identifier(instance.quirkId));

                if (quirk != null) {
                    List<QuirkSystem.Ability> abilities = quirk.getAbilities(instance);
                    data.cycleAbility(direction, abilities, instance);
                    sync(player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SWITCH_QUIRK, (server, player, handler, buf, responseSender) -> {
            int index = buf.readInt();
            server.execute(() -> {
                ((IQuirkDataAccessor)player).getQuirkData().setSelectedQuirkIndex(index);
                ((IQuirkDataAccessor)player).getQuirkData().setSelectedAbilityIndex(0);
                sync(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PERFORM_STEAL, (server, player, handler, buf, responseSender) -> {
            int targetId = buf.readInt();
            String quirkToSteal = buf.readString();
            server.execute(() -> {
                Entity entity = player.getWorld().getEntityById(targetId);
                if (!(entity instanceof LivingEntity target)) return;
                if (player.distanceTo(target) > 10) return;
                QuirkSystem.QuirkData attackerData = ((IQuirkDataAccessor)player).getQuirkData();
                QuirkSystem.QuirkData targetData = ((IQuirkDataAccessor)target).getQuirkData();
                attackerData.addQuirk(quirkToSteal);
                boolean fullyRemoved = targetData.removeQuirk(quirkToSteal);
                if (fullyRemoved) {
                    QuirkSystem.Quirk q = QuirkRegistry.get(new Identifier(quirkToSteal));
                    if (q != null) q.onRemove(target, targetData);
                }
                player.sendMessage(Text.of("§cStole " + quirkToSteal + "!"), true);
                if (target instanceof ServerPlayerEntity tp) {
                    tp.sendMessage(Text.of("§4Your quirk " + quirkToSteal + " was stolen!"), true);
                    sync(tp);
                }
                sync(player);
                sync(target);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UPGRADE_STAT, (server, player, handler, buf, responseSender) -> {
            int statIndex = buf.readInt();
            int amount = buf.readInt();
            server.execute(() -> {
                QuirkSystem.QuirkData data = ((IQuirkDataAccessor) player).getQuirkData();
                if (amount <= 0 || data.statPoints < amount) return;
                switch (statIndex) {
                    case 0 -> data.strength += amount;
                    case 1 -> data.endurance += amount;
                    case 2 -> data.speed += amount;
                    case 3 -> data.staminaMax += amount;
                    case 4 -> data.meta += amount;
                }
                data.statPoints -= amount;
                sync(player);
            });
        });

        // --- NEW ORDER RULE SYNC ---
        ServerPlayNetworking.registerGlobalReceiver(SYNC_NEW_ORDER_RULE, (server, player, handler, buf, responseSender) -> {
            String phrase = buf.readString();
            String targetType = buf.readString(); // New Field
            String effect = buf.readString();
            int cost = buf.readInt();

            server.execute(() -> {
                QuirkSystem.QuirkData data = ((IQuirkDataAccessor) player).getQuirkData();

                // Find New Order Instance
                QuirkSystem.QuirkData.QuirkInstance newOrder = null;
                for (QuirkSystem.QuirkData.QuirkInstance q : data.getQuirks()) {
                    if (q.quirkId.equals("plusultra:new_order")) {
                        newOrder = q;
                        break;
                    }
                }

                if (newOrder != null) {
                    // Ensure "SavedRules" list exists
                    if (!newOrder.persistentData.contains("SavedRules")) {
                        newOrder.persistentData.put("SavedRules", new NbtList());
                    }

                    NbtList savedRules = newOrder.persistentData.getList("SavedRules", NbtElement.COMPOUND_TYPE);

                    // Add new rule
                    NbtCompound ruleTag = new NbtCompound();
                    ruleTag.putString("Phrase", phrase);
                    ruleTag.putString("TargetType", targetType); // Save new field
                    ruleTag.putString("Effect", effect);
                    ruleTag.putInt("Cost", cost);
                    savedRules.add(ruleTag);

                    newOrder.persistentData.put("SavedRules", savedRules);

                    player.sendMessage(Text.of("§a[New Order] Rule: \"" + phrase + "\" -> " + targetType + " -> " + effect), true);
                    sync(player);
                } else {
                    player.sendMessage(Text.of("§cError: You do not have New Order."), true);
                }
            });
        });

        // --- NEW ORDER REMOVE RULE ---
        ServerPlayNetworking.registerGlobalReceiver(REMOVE_NEW_ORDER_RULE, (server, player, handler, buf, responseSender) -> {
            int index = buf.readInt();
            server.execute(() -> {
                QuirkSystem.QuirkData data = ((IQuirkDataAccessor) player).getQuirkData();
                QuirkSystem.QuirkData.QuirkInstance newOrder = null;
                for (QuirkSystem.QuirkData.QuirkInstance q : data.getQuirks()) {
                    if (q.quirkId.equals("plusultra:new_order")) {
                        newOrder = q;
                        break;
                    }
                }
                if (newOrder != null) {
                    NewOrderQuirk quirk = (NewOrderQuirk) QuirkRegistry.get(new Identifier("plusultra:new_order"));
                    if (quirk != null) {
                        quirk.removeRule(player, newOrder, index);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ADJUST_PERCENTAGE, (server, player, handler, buf, responseSender) -> {
            int direction = buf.readInt();
            server.execute(() -> {
                QuirkSystem.QuirkData data = ((IQuirkDataAccessor) player).getQuirkData();
                if (data.getQuirks().isEmpty()) return;
                QuirkSystem.QuirkData.QuirkInstance instance = data.getQuirks().get(data.getSelectedQuirkIndex());

                NbtCompound targetData = instance.persistentData;
                String targetQuirkId = instance.quirkId;

                if ("plusultra:copy".equals(instance.quirkId)) {
                    if (instance.persistentData.contains("ActiveSlot")) {
                        int slot = instance.persistentData.getInt("ActiveSlot");
                        if (slot != -1) {
                            String key = "Slot_" + slot;
                            if (instance.persistentData.contains(key)) {
                                NbtCompound slotCompound = instance.persistentData.getCompound(key);
                                targetData = slotCompound.getCompound("Data");
                                targetQuirkId = slotCompound.getString("QuirkId");
                            }
                        }
                    }
                }

                boolean isStockpile = "plusultra:stockpile".equals(targetQuirkId);
                if (!isStockpile && instance.persistentData.contains("FirstQuirk")) {
                    if ("plusultra:stockpile".equals(instance.persistentData.getString("FirstQuirk"))) {
                        isStockpile = true;
                        targetData = instance.persistentData;
                    }
                }

                if (isStockpile) {
                    float maxPercent = targetData.getFloat("StockpilePercent");
                    float currentSelected = targetData.contains("SelectedPercent") ?
                            targetData.getFloat("SelectedPercent") : maxPercent;
                    float change = direction * 5.0f;
                    float newSelected = currentSelected + change;
                    if (newSelected > maxPercent) newSelected = maxPercent;
                    if (newSelected < 0.0f) newSelected = 0.0f;
                    targetData.putFloat("SelectedPercent", newSelected);

                    if ("plusultra:copy".equals(instance.quirkId) && targetData != instance.persistentData) {
                        int slot = instance.persistentData.getInt("ActiveSlot");
                        String key = "Slot_" + slot;
                        NbtCompound slotCompound = instance.persistentData.getCompound(key);
                        slotCompound.put("Data", targetData);
                        instance.persistentData.put(key, slotCompound);
                    }

                    sync(player);
                }
                else if ("plusultra:warp_gate".equals(targetQuirkId)) {
                    if (targetData.contains("Anchors")) {
                        NbtList anchors = targetData.getList("Anchors", NbtElement.COMPOUND_TYPE);
                        if (!anchors.isEmpty()) {
                            int current = targetData.getInt("SelectedAnchor");
                            int next = (current + direction) % anchors.size();
                            if (next < 0) next += anchors.size();
                            targetData.putInt("SelectedAnchor", next);

                            NbtCompound tag = anchors.getCompound(next);
                            player.sendMessage(Text.of("§5Selected Anchor: " + tag.getString("Name")), true);

                            if ("plusultra:copy".equals(instance.quirkId) && targetData != instance.persistentData) {
                                int slot = instance.persistentData.getInt("ActiveSlot");
                                String key = "Slot_" + slot;
                                NbtCompound slotCompound = instance.persistentData.getCompound(key);
                                slotCompound.put("Data", targetData);
                                instance.persistentData.put(key, slotCompound);
                            }

                            sync(player);
                        }
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_DESTRUCTION, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                QuirkSystem.QuirkData data = ((IQuirkDataAccessor) player).getQuirkData();
                if (data.runtimeTags.containsKey("DESTRUCTION_DISABLED")) {
                    data.runtimeTags.remove("DESTRUCTION_DISABLED");
                    player.sendMessage(Text.of("§aDestruction Enabled"), true);
                } else {
                    data.runtimeTags.put("DESTRUCTION_DISABLED", "true");
                    player.sendMessage(Text.of("§cDestruction Disabled"), true);
                }
            });
        });
    }

    public static void sync(LivingEntity entity) {
        if (entity.getWorld().isClient) return;

        QuirkSystem.QuirkData data = ((IQuirkDataAccessor)entity).getQuirkData();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entity.getId());
        NbtCompound nbt = new NbtCompound();
        data.writeToNbt(nbt);
        buf.writeNbt(nbt);

        for (ServerPlayerEntity player : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(player, SYNC_DATA, buf);
        }

        if (entity instanceof ServerPlayerEntity self) {
            ServerPlayNetworking.send(self, SYNC_DATA, buf);
        }
    }

    public static void syncToPlayer(LivingEntity entity, ServerPlayerEntity target) {
        if (entity.getWorld().isClient) return;

        QuirkSystem.QuirkData data = ((IQuirkDataAccessor)entity).getQuirkData();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entity.getId());
        NbtCompound nbt = new NbtCompound();
        data.writeToNbt(nbt);
        buf.writeNbt(nbt);

        ServerPlayNetworking.send(target, SYNC_DATA, buf);
    }
}