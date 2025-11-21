package net.bored.common;

import net.bored.api.QuirkSystem;
import net.bored.api.IQuirkDataAccessor;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public class QuirkAttackHandler {

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target)) return ActionResult.PASS;

            // Get Attacker Data
            QuirkSystem.QuirkData attackerData = ((IQuirkDataAccessor) player).getQuirkData();

            // Check if AFO Mode is active
            if (attackerData.runtimeTags.containsKey("AFO_MODE")) {
                String mode = attackerData.runtimeTags.get("AFO_MODE");

                // Consume the tag so it only happens once
                attackerData.runtimeTags.remove("AFO_MODE");

                // Access Target Data
                QuirkSystem.QuirkData targetData = ((IQuirkDataAccessor) target).getQuirkData();

                if ("STEAL".equals(mode)) {
                    handleSteal(player, attackerData, target, targetData);
                } else if ("GIVE".equals(mode)) {
                    handleGive(player, attackerData, target, targetData);
                }
            }

            return ActionResult.PASS;
        });
    }

    private static void handleSteal(PlayerEntity attacker, QuirkSystem.QuirkData attackerData, LivingEntity target, QuirkSystem.QuirkData targetData) {
        if (targetData.getQuirks().isEmpty()) {
            attacker.sendMessage(Text.of("§7Target has no quirks to steal."), true);
            return;
        }

        // Steal the last quirk in their list (LIFO)
        int index = targetData.getQuirks().size() - 1;
        QuirkSystem.QuirkData.QuirkInstance stolen = targetData.getQuirks().get(index);

        // Logic: Don't steal AFO itself if you want to prevent chaos, but canonical AFO can steal anything.
        // Let's just do it.

        targetData.getQuirks().remove(index);
        attackerData.getQuirks().add(stolen);

        attacker.sendMessage(Text.of("§cStole " + stolen.quirkId + "!"), true);
        if (target instanceof PlayerEntity tp) {
            tp.sendMessage(Text.of("§4Your quirk " + stolen.quirkId + " was stolen!"), true);
        }
    }

    private static void handleGive(PlayerEntity attacker, QuirkSystem.QuirkData attackerData, LivingEntity target, QuirkSystem.QuirkData targetData) {
        if (attackerData.getQuirks().isEmpty()) return;

        // Give the currently selected quirk
        int index = attackerData.getSelectedQuirkIndex();
        if (index >= attackerData.getQuirks().size()) return;

        QuirkSystem.QuirkData.QuirkInstance toGive = attackerData.getQuirks().get(index);

        // Prevent giving away the specific "All For One" quirk if it's the only thing letting you use this ability?
        // For now, let's allow it. It's funny if you lose your powers by giving them away.

        attackerData.getQuirks().remove(index);
        targetData.getQuirks().add(toGive);

        attacker.sendMessage(Text.of("§eGave " + toGive.quirkId + " to target."), true);
        if (target instanceof PlayerEntity tp) {
            tp.sendMessage(Text.of("§bYou received " + toGive.quirkId + "!"), true);
        }
    }
}