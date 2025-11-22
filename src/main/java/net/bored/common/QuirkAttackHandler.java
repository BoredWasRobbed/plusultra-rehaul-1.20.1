package net.bored.common;

import net.bored.api.QuirkSystem;
import net.bored.api.IQuirkDataAccessor;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
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

            // Bestowal Transfer Logic
            if (attackerData.runtimeTags.containsKey("OFA_MODE")) {
                String mode = attackerData.runtimeTags.get("OFA_MODE");
                attackerData.runtimeTags.remove("OFA_MODE");

                if ("TRANSFER".equals(mode)) {
                    handleOFATransfer(player, attackerData, target, ((IQuirkDataAccessor) target).getQuirkData());
                }
                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });
    }

    private static void handleOFATransfer(PlayerEntity attacker, QuirkSystem.QuirkData attackerData, LivingEntity target, QuirkSystem.QuirkData targetData) {
        // Find Bestowal Quirk on Attacker
        QuirkSystem.QuirkData.QuirkInstance bestowalInstance = null;
        for (QuirkSystem.QuirkData.QuirkInstance qi : attackerData.getQuirks()) {
            if ("plusultra:quirk_bestowal".equals(qi.quirkId)) {
                bestowalInstance = qi;
                break;
            }
        }

        if (bestowalInstance == null) {
            attacker.sendMessage(Text.of("§cTransfer failed: You lost the quirk?"), true);
            return;
        }

        int currentGen = bestowalInstance.persistentData.getInt("Generation");
        String firstUserQuirkId = bestowalInstance.persistentData.getString("FirstQuirk");

        int newGen = currentGen + 1;

        // GEN 1 LOGIC (Origin)
        if (currentGen == 0) {
            newGen = 1;
            // Check if target has a main quirk to fuse
            if (!targetData.getQuirks().isEmpty()) {
                firstUserQuirkId = targetData.getQuirks().get(0).quirkId;
            }
        }

        // --- PERFORM TRANSFER ---
        List<QuirkSystem.QuirkData.QuirkInstance> quirksToMove = new ArrayList<>(attackerData.getQuirks());

        // 1. Clear Attacker
        attackerData.getQuirks().clear();
        attackerData.setSelectedQuirkIndex(0);

        // 2. Add to Target
        for (QuirkSystem.QuirkData.QuirkInstance qi : quirksToMove) {
            QuirkSystem.QuirkData.QuirkInstance existing = null;

            // Check if target already has this quirk
            for(QuirkSystem.QuirkData.QuirkInstance tqi : targetData.getQuirks()) {
                if(tqi.quirkId.equals(qi.quirkId)) {
                    existing = tqi;
                    break;
                }
            }

            if (existing != null) {
                // STACKING LOGIC: Add counts together
                existing.count += qi.count;

                // If it's the Bestowal quirk, update generation on the existing instance
                if ("plusultra:quirk_bestowal".equals(qi.quirkId)) {
                    existing.persistentData.putInt("Generation", newGen);
                    existing.persistentData.putString("FirstQuirk", firstUserQuirkId);
                    existing.isLocked = false;
                }
            } else {
                // New Quirk Addition
                qi.innate = false; // It was transferred, so not innate (unless fused? but logic simplifies to false for transferred instances)

                // LOCKING LOGIC
                if ("plusultra:quirk_bestowal".equals(qi.quirkId)) {
                    qi.isLocked = false;
                    qi.persistentData.putInt("Generation", newGen);
                    qi.persistentData.putString("FirstQuirk", firstUserQuirkId);
                } else if (qi.quirkId.equals(firstUserQuirkId)) {
                    qi.isLocked = false;
                } else {
                    // Lock inherited quirks until Gen 9
                    qi.isLocked = true;
                }

                targetData.getQuirks().add(qi);
            }
        }

        // 3. Notify
        attacker.sendMessage(Text.of("§eYou have passed on Quirk Bestowal."), true);
        if (target instanceof PlayerEntity tp) {
            tp.sendMessage(Text.of("§bYou have received Quirk Bestowal! (Generation " + newGen + ")"), false);
            if (newGen < 9) {
                tp.sendMessage(Text.of("§7Past users' quirks are currently locked."), false);
            }
        }

        // 4. Sync
        if (attacker instanceof ServerPlayerEntity sa) PlusUltraNetwork.sync(sa);
        if (target instanceof ServerPlayerEntity st) PlusUltraNetwork.sync(st);
    }

    private static void handleSteal(PlayerEntity attacker, QuirkSystem.QuirkData attackerData, LivingEntity target, QuirkSystem.QuirkData targetData) {
        if (targetData.getQuirks().isEmpty()) {
            attacker.sendMessage(Text.of("§7Target has no quirks to steal."), true);
            return;
        }

        if (targetData.getQuirks().size() > 1) {
            if (attacker instanceof ServerPlayerEntity serverAttacker) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(target.getId());
                buf.writeInt(targetData.getQuirks().size());
                for (QuirkSystem.QuirkData.QuirkInstance qi : targetData.getQuirks()) {
                    buf.writeString(qi.quirkId);
                }
                ServerPlayNetworking.send(serverAttacker, PlusUltraNetwork.OPEN_STEAL_SELECTION, buf);
            }
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

        if (attacker instanceof ServerPlayerEntity serverAttacker) PlusUltraNetwork.sync(serverAttacker);
        if (target instanceof ServerPlayerEntity serverTarget) PlusUltraNetwork.sync(serverTarget);
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

        if (attacker instanceof ServerPlayerEntity serverAttacker) PlusUltraNetwork.sync(serverAttacker);
        if (target instanceof ServerPlayerEntity serverTarget) PlusUltraNetwork.sync(serverTarget);
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