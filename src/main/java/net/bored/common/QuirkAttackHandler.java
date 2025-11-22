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
        QuirkSystem.QuirkData.QuirkInstance activeOFA = null;

        // Find either the Bestowal quirk (Gen 0) OR One For All (Gen 1+)
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

        // 1. Stop transferal once it hits the ninth user.
        if (currentGen >= 9) {
            attacker.sendMessage(Text.of("§6The power has reached its peak. One For All cannot be transferred further."), true);
            return;
        }

        // Identify what the "Core" quirk inside OFA is
        String firstUserQuirkId = activeOFA.persistentData.getString("FirstQuirk");
        int newGen = currentGen + 1;

        // --- FUSION LOGIC (Gen 0 -> Gen 1) ---
        if (currentGen == 0) {
            newGen = 1;
            // If Target has a quirk, THAT becomes the fused "FirstQuirk"
            // We merge Bestowal (Attacker) + TargetQuirk (Target) -> One For All (Target)
            if (!targetData.getQuirks().isEmpty()) {
                QuirkSystem.QuirkData.QuirkInstance targetExisting = targetData.getQuirks().get(0);
                firstUserQuirkId = targetExisting.quirkId;

                // Remove the target's existing quirk because it's being merged into OFA
                // We do this by clearing target quirks before adding OFA
                targetData.getQuirks().clear();
                attacker.sendMessage(Text.of("§eFusing Bestowal with " + getFormalName(firstUserQuirkId) + "..."), false);
            } else {
                // Quirkless 1st user scenario (unlikely in lore but possible here)
                firstUserQuirkId = "";
            }
        }

        // --- TRANSFER LOGIC ---
        // Copy all OTHER quirks the attacker has (Past Users)
        List<QuirkSystem.QuirkData.QuirkInstance> quirksToMove = new ArrayList<>();
        for(QuirkSystem.QuirkData.QuirkInstance qi : attackerData.getQuirks()) {
            // Don't copy the old OFA/Bestowal instance directly, we create a NEW one for the target
            if (!qi.quirkId.equals(activeOFA.quirkId)) {
                quirksToMove.add(qi);
            }
        }

        // 1. Clear Attacker
        attackerData.getQuirks().clear();
        attackerData.setSelectedQuirkIndex(0);

        // 2. Create NEW One For All Instance for Target
        // This handles the ID change: It is ALWAYS "one_for_all" on the target.
        QuirkSystem.QuirkData.QuirkInstance newOFA = new QuirkSystem.QuirkData.QuirkInstance("plusultra:one_for_all");
        newOFA.persistentData.putInt("Generation", newGen);
        if (firstUserQuirkId != null && !firstUserQuirkId.isEmpty()) {
            newOFA.persistentData.putString("FirstQuirk", firstUserQuirkId);
        }
        // Transfer Stockpile Data if it existed
        if (activeOFA.persistentData.contains("StockpilePercent")) {
            newOFA.persistentData.putFloat("StockpilePercent", activeOFA.persistentData.getFloat("StockpilePercent"));
        }
        newOFA.isLocked = false;
        targetData.getQuirks().add(newOFA);

        // 3. Add Past User Quirks
        for (QuirkSystem.QuirkData.QuirkInstance qi : quirksToMove) {
            // Clone the instance to prevent reference issues
            QuirkSystem.QuirkData.QuirkInstance clone = new QuirkSystem.QuirkData.QuirkInstance(qi.quirkId);
            clone.innate = false;
            clone.count = qi.count;
            clone.persistentData = qi.persistentData.copy();

            // Lock inherited quirks until Gen 9
            clone.isLocked = true;

            targetData.getQuirks().add(clone);
        }

        // 4. Notify & Sync
        attacker.sendMessage(Text.of("§eYou have passed on the torch."), true);
        if (target instanceof PlayerEntity tp) {
            String mergedName = (firstUserQuirkId != null && !firstUserQuirkId.isEmpty()) ? getFormalName(firstUserQuirkId) : "Power";
            tp.sendMessage(Text.of("§bYou have received One For All! (Gen " + newGen + ")"), false);
            if (newGen == 1) tp.sendMessage(Text.of("§7Bestowal has merged with " + mergedName + "."), false);
        }

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