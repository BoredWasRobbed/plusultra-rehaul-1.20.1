package net.bored.common;

import net.bored.api.QuirkSystem;
import net.bored.api.IQuirkDataAccessor;
import net.bored.common.quirks.CopyQuirk;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class QuirkAttackHandler {

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target)) return ActionResult.PASS;

            QuirkSystem.QuirkData attackerData = ((IQuirkDataAccessor) player).getQuirkData();

            // --- AFO LOGIC ---
            if (attackerData.runtimeTags.containsKey("AFO_MODE")) {
                String mode = attackerData.runtimeTags.get("AFO_MODE");
                attackerData.runtimeTags.remove("AFO_MODE");

                QuirkSystem.QuirkData targetData = ((IQuirkDataAccessor) target).getQuirkData();

                if ("STEAL".equals(mode)) {
                    handleSteal(player, attackerData, target, targetData);
                } else if ("GIVE".equals(mode)) {
                    handleGive(player, attackerData, target, targetData);
                }

                return ActionResult.SUCCESS;
            }

            // --- OFA LOGIC ---
            if (attackerData.runtimeTags.containsKey("OFA_MODE")) {
                String mode = attackerData.runtimeTags.get("OFA_MODE");
                attackerData.runtimeTags.remove("OFA_MODE");

                if ("TRANSFER".equals(mode)) {
                    handleOFATransfer(player, attackerData, target, ((IQuirkDataAccessor) target).getQuirkData());
                }
                return ActionResult.SUCCESS;
            }

            // --- COPY LOGIC ---
            if (attackerData.runtimeTags.containsKey("COPY_MODE_SLOT")) {
                int slotIndex = Integer.parseInt(attackerData.runtimeTags.get("COPY_MODE_SLOT"));
                attackerData.runtimeTags.remove("COPY_MODE_SLOT");

                QuirkSystem.QuirkData targetData = ((IQuirkDataAccessor) target).getQuirkData();
                handleCopy(player, attackerData, target, targetData, slotIndex);

                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });
    }

    private static void handleCopy(PlayerEntity attacker, QuirkSystem.QuirkData attackerData, LivingEntity target, QuirkSystem.QuirkData targetData, int slotIndex) {
        if (targetData.getQuirks().isEmpty()) {
            attacker.sendMessage(Text.of("§cTarget has no quirks to copy."), true);
            return;
        }

        // Logic: Copy the last added quirk (usually the 'main' one or most recent)
        // Usually index 0 is innate.

        QuirkSystem.QuirkData.QuirkInstance targetQuirk = null;
        for(QuirkSystem.QuirkData.QuirkInstance q : targetData.getQuirks()) {
            // Can't copy "Empty" or "Copy" itself to avoid recursion issues
            if (q.quirkId.equals("plusultra:copy")) continue;
            targetQuirk = q;
            break; // Grab first valid
        }

        if (targetQuirk == null) {
            attacker.sendMessage(Text.of("§cCannot copy this target's quirk."), true);
            return;
        }

        // Find the Copy Quirk Instance in the attacker
        QuirkSystem.QuirkData.QuirkInstance copyInstance = null;
        for(QuirkSystem.QuirkData.QuirkInstance q : attackerData.getQuirks()) {
            if (q.quirkId.equals("plusultra:copy")) {
                copyInstance = q;
                break;
            }
        }

        if (copyInstance != null) {
            String quirkName = getFormalName(targetQuirk.quirkId);

            // 1. DUPLICATE CHECK
            for (int i = 0; i < 5; i++) {
                String key = "Slot_" + i;
                if (copyInstance.persistentData.contains(key)) {
                    NbtCompound existingSlot = copyInstance.persistentData.getCompound(key);
                    if (existingSlot.getString("QuirkId").equals(targetQuirk.quirkId)) {
                        attacker.sendMessage(Text.of("§cYou already have " + quirkName + " copied!"), true);
                        return;
                    }
                }
            }

            // 2. BLOCK ACCUMULATION/UNIQUE QUIRKS
            if (targetQuirk.quirkId.equals("plusultra:stockpile") ||
                    targetQuirk.quirkId.equals("plusultra:one_for_all") ||
                    targetQuirk.quirkId.equals("plusultra:all_for_one")) {
                attacker.sendMessage(Text.of("§cCannot copy accumulation type quirks!"), true);
                return;
            }

            // Calculate Duration: Min 5 mins (6000 ticks) + scaling
            long baseDuration = 6000;
            long bonus = attackerData.meta * 200L; // 10 seconds per Meta level
            long totalDuration = baseDuration + bonus;

            NbtCompound slotData = new NbtCompound();
            slotData.putString("QuirkId", targetQuirk.quirkId);
            slotData.putLong("TimeLeft", totalDuration);
            slotData.putLong("MaxTime", totalDuration);
            slotData.put("Data", new NbtCompound()); // Start with fresh data

            copyInstance.persistentData.put("Slot_" + slotIndex, slotData);

            attacker.sendMessage(Text.of("§aCopied " + quirkName + "! (" + (totalDuration/1200) + "m)"), true);
            PlusUltraNetwork.sync(attacker);
        }
    }

    private static void handleOFATransfer(PlayerEntity attacker, QuirkSystem.QuirkData attackerData, LivingEntity target, QuirkSystem.QuirkData targetData) {
        QuirkSystem.QuirkData.QuirkInstance activeOFA = null;

        for (QuirkSystem.QuirkData.QuirkInstance qi : attackerData.getQuirks()) {
            if ("plusultra:one_for_all".equals(qi.quirkId) || "plusultra:quirk_bestowal".equals(qi.quirkId)) {
                activeOFA = qi;
                break;
            }
        }

        if (activeOFA == null) {
            attacker.sendMessage(Text.of("§cTransfer failed: You lost the quirk?"), true);
            return;
        }

        int currentGen = activeOFA.persistentData.getInt("Generation");

        if (currentGen >= 9) {
            attacker.sendMessage(Text.of("§6The power has reached its peak. One For All cannot be transferred further."), true);
            return;
        }

        String firstUserQuirkId = activeOFA.persistentData.getString("FirstQuirk");
        int newGen = currentGen + 1;

        if (currentGen == 0) {
            newGen = 1;
            if (!targetData.getQuirks().isEmpty()) {
                QuirkSystem.QuirkData.QuirkInstance targetExisting = targetData.getQuirks().get(0);
                firstUserQuirkId = targetExisting.quirkId;
                targetData.getQuirks().clear();
                attacker.sendMessage(Text.of("§eFusing Bestowal with " + getFormalName(firstUserQuirkId) + "..."), false);
            } else {
                firstUserQuirkId = "";
            }
        }

        List<QuirkSystem.QuirkData.QuirkInstance> quirksToMove = new ArrayList<>();
        for(QuirkSystem.QuirkData.QuirkInstance qi : attackerData.getQuirks()) {
            if (!qi.quirkId.equals(activeOFA.quirkId)) {
                quirksToMove.add(qi);
            }
        }

        attackerData.getQuirks().clear();
        attackerData.setSelectedQuirkIndex(0);

        QuirkSystem.QuirkData.QuirkInstance newOFA = new QuirkSystem.QuirkData.QuirkInstance("plusultra:one_for_all");
        newOFA.persistentData.putInt("Generation", newGen);
        if (firstUserQuirkId != null && !firstUserQuirkId.isEmpty()) {
            newOFA.persistentData.putString("FirstQuirk", firstUserQuirkId);
        }
        if (activeOFA.persistentData.contains("StockpilePercent")) {
            newOFA.persistentData.putFloat("StockpilePercent", activeOFA.persistentData.getFloat("StockpilePercent"));
        }
        newOFA.isLocked = false;
        targetData.getQuirks().add(newOFA);

        for (QuirkSystem.QuirkData.QuirkInstance qi : quirksToMove) {
            QuirkSystem.QuirkData.QuirkInstance clone = new QuirkSystem.QuirkData.QuirkInstance(qi.quirkId);
            clone.innate = false;
            clone.count = qi.count;
            clone.persistentData = qi.persistentData.copy();
            clone.isLocked = true;
            targetData.getQuirks().add(clone);
        }

        attacker.sendMessage(Text.of("§eYou have passed on the torch."), true);
        if (target instanceof PlayerEntity tp) {
            String mergedName = (firstUserQuirkId != null && !firstUserQuirkId.isEmpty()) ? getFormalName(firstUserQuirkId) : "Power";
            tp.sendMessage(Text.of("§bYou have received One For All! (Gen " + newGen + ")"), false);
            if (newGen == 1) tp.sendMessage(Text.of("§7Bestowal has merged with " + mergedName + "."), false);
        }

        // FIXED: Now syncs properly regardless of if target is player or mob
        PlusUltraNetwork.sync(attacker);
        PlusUltraNetwork.sync(target);
    }

    private static void handleSteal(PlayerEntity attacker, QuirkSystem.QuirkData attackerData, LivingEntity target, QuirkSystem.QuirkData targetData) {
        if (targetData.getQuirks().isEmpty()) {
            attacker.sendMessage(Text.of("§7Target has no quirks to steal."), true);
            return;
        }

        if (targetData.getQuirks().size() > 1 && attacker instanceof ServerPlayerEntity serverAttacker) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeInt(target.getId());
            buf.writeInt(targetData.getQuirks().size());
            for (QuirkSystem.QuirkData.QuirkInstance qi : targetData.getQuirks()) {
                buf.writeString(qi.quirkId);
            }
            ServerPlayNetworking.send(serverAttacker, PlusUltraNetwork.OPEN_STEAL_SELECTION, buf);
            return;
        }

        int index = targetData.getQuirks().size() - 1;
        QuirkSystem.QuirkData.QuirkInstance stolen = targetData.getQuirks().get(index);
        String quirkId = stolen.quirkId;
        String quirkName = getFormalName(quirkId);

        attackerData.addQuirk(quirkId);

        boolean fullyRemoved = targetData.removeQuirk(quirkId);
        if (fullyRemoved) {
            QuirkSystem.Quirk q = QuirkRegistry.get(new Identifier(quirkId));
            if (q != null) q.onRemove(target, targetData);
        }

        attacker.sendMessage(Text.of("§cStole " + quirkName + "!"), true);
        if (target instanceof PlayerEntity tp) {
            tp.sendMessage(Text.of("§4Your quirk " + quirkName + " was stolen!"), true);
        }

        // FIXED: Now syncs properly regardless of if target is player or mob
        PlusUltraNetwork.sync(attacker);
        PlusUltraNetwork.sync(target);
    }

    private static void handleGive(PlayerEntity attacker, QuirkSystem.QuirkData attackerData, LivingEntity target, QuirkSystem.QuirkData targetData) {
        if (attackerData.getQuirks().isEmpty()) return;

        int index = attackerData.getSelectedQuirkIndex();
        if (index >= attackerData.getQuirks().size()) return;

        QuirkSystem.QuirkData.QuirkInstance toGive = attackerData.getQuirks().get(index);

        if (toGive.quirkId.equals("plusultra:all_for_one") && toGive.innate) {
            attacker.sendMessage(Text.of("§cYou cannot give away your original Quirk!"), true);
            return;
        }

        String quirkId = toGive.quirkId;
        String quirkName = getFormalName(quirkId);

        targetData.addQuirk(quirkId);
        boolean fullyRemoved = attackerData.removeQuirk(quirkId);
        if (fullyRemoved) {
            QuirkSystem.Quirk q = QuirkRegistry.get(new Identifier(quirkId));
            if (q != null) q.onRemove(attacker, attackerData);
        }

        if (attackerData.getSelectedQuirkIndex() >= attackerData.getQuirks().size()) {
            attackerData.setSelectedQuirkIndex(Math.max(0, attackerData.getQuirks().size() - 1));
        }

        attacker.sendMessage(Text.of("§eGave " + quirkName + " to target."), true);
        if (target instanceof PlayerEntity tp) {
            tp.sendMessage(Text.of("§bYou received " + quirkName + "!"), true);
        }

        // FIXED: Now syncs properly regardless of if target is player or mob
        PlusUltraNetwork.sync(attacker);
        PlusUltraNetwork.sync(target);
    }

    private static String getFormalName(String quirkId) {
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
}