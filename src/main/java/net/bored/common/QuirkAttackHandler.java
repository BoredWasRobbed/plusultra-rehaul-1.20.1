package net.bored.common;

import net.bored.api.QuirkSystem;
import net.bored.api.IQuirkDataAccessor;
import net.bored.common.quirks.CopyQuirk;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
                    // Instead of immediate transfer, we offer it
                    handleOFAOffer(player, target);
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

            // --- BLOODCURDLE LOGIC (Passive on Attack) ---
            checkBloodcurdleDraw(player, target);

            return ActionResult.PASS;
        });
    }

    public static void checkBloodcurdleDraw(LivingEntity attacker, LivingEntity target) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor)attacker).getQuirkData();
        boolean hasBloodcurdle = false;
        for(QuirkSystem.QuirkData.QuirkInstance q : data.getQuirks()) {
            if (q.quirkId.equals("plusultra:bloodcurdle")) {
                hasBloodcurdle = true;
                break;
            }
        }

        if (hasBloodcurdle) {
            // Check weapon
            ItemStack stack = attacker.getMainHandStack();
            boolean isSharp = stack.getItem() instanceof SwordItem ||
                    stack.getItem() instanceof AxeItem ||
                    stack.getItem().getTranslationKey().contains("scythe") ||
                    stack.getItem().getTranslationKey().contains("knife") ||
                    stack.getItem().getTranslationKey().contains("dagger");

            if (isSharp) {
                // Chance: 10% base + (Meta * 2%)
                float chance = 0.10f + (data.meta * 0.02f);

                if (attacker.getWorld().random.nextFloat() < chance) {
                    QuirkSystem.QuirkData targetData = ((IQuirkDataAccessor) target).getQuirkData();
                    String bloodType = targetData.bloodType;
                    if (bloodType == null || bloodType.isEmpty()) bloodType = "O+";

                    data.runtimeTags.put("BLOOD_STOLEN_FROM", target.getUuid().toString());
                    data.runtimeTags.put("BLOOD_STOLEN_TYPE", bloodType);
                    // Set dry timer to 60 seconds (1200 ticks)
                    data.runtimeTags.put("BLOOD_DRY_TIMER", "1200");

                    if (attacker instanceof PlayerEntity p) {
                        // Obscured feedback
                        p.sendMessage(Text.of("§cBlade bloodied!"), true);
                    }
                }
            }
        }
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
            // Can't copy "Empty", "Copy", or "Quirkless"
            if (q.quirkId.equals("plusultra:copy")) continue;
            if (q.quirkId.equals("plusultra:quirkless")) continue;
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
            String quirkName = QuirkSystem.getFormalName(targetQuirk.quirkId);

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

    private static void handleOFAOffer(PlayerEntity attacker, LivingEntity target) {
        QuirkSystem.QuirkData targetData = ((IQuirkDataAccessor) target).getQuirkData();
        // Tag target with the sender's UUID
        targetData.runtimeTags.put("OFA_OFFER_SENDER", attacker.getUuidAsString());

        if (target instanceof PlayerEntity tp) {
            tp.sendMessage(Text.of("§e§lONE FOR ALL OFFERED!"), false);
            tp.sendMessage(Text.of("§e" + attacker.getName().getString() + " wishes to transfer One For All to you."), false);
            tp.sendMessage(Text.of("§eType §6/plusultra accept §eto receive this power."), false);
        }
        attacker.sendMessage(Text.of("§eOne For All offered to " + target.getName().getString() + "."), true);
    }

    public static int acceptOneForAll(ServerPlayerEntity target) {
        QuirkSystem.QuirkData targetData = ((IQuirkDataAccessor) target).getQuirkData();
        if (!targetData.runtimeTags.containsKey("OFA_OFFER_SENDER")) {
            target.sendMessage(Text.of("§cYou have no pending offers."), true);
            return 0;
        }

        String senderUUIDStr = targetData.runtimeTags.get("OFA_OFFER_SENDER");
        targetData.runtimeTags.remove("OFA_OFFER_SENDER");

        ServerPlayerEntity sender = target.getServer().getPlayerManager().getPlayer(UUID.fromString(senderUUIDStr));
        if (sender == null) {
            target.sendMessage(Text.of("§cThe sender is no longer online."), true);
            return 0;
        }

        // Verify sender still has OFA
        QuirkSystem.QuirkData senderData = ((IQuirkDataAccessor) sender).getQuirkData();
        QuirkSystem.QuirkData.QuirkInstance activeOFA = null;

        for (QuirkSystem.QuirkData.QuirkInstance qi : senderData.getQuirks()) {
            if ("plusultra:one_for_all".equals(qi.quirkId) || "plusultra:quirk_bestowal".equals(qi.quirkId)) {
                activeOFA = qi;
                break;
            }
        }

        if (activeOFA == null) {
            target.sendMessage(Text.of("§cThe sender no longer possesses the quirk."), true);
            sender.sendMessage(Text.of("§cTransfer failed: Quirk lost."), true);
            return 0;
        }

        // PERFORM TRANSFER LOGIC
        int currentGen = activeOFA.persistentData.getInt("Generation");
        if (currentGen >= 9) {
            sender.sendMessage(Text.of("§6The power has reached its peak. One For All cannot be transferred further."), true);
            target.sendMessage(Text.of("§6The power is too great to be accepted."), true);
            return 0;
        }

        String firstUserQuirkId = activeOFA.persistentData.getString("FirstQuirk");
        int newGen = currentGen + 1;

        if (currentGen == 0) {
            newGen = 1;
            if (!targetData.getQuirks().isEmpty()) {
                QuirkSystem.QuirkData.QuirkInstance targetExisting = targetData.getQuirks().get(0);
                firstUserQuirkId = targetExisting.quirkId;
                targetData.getQuirks().clear();
                sender.sendMessage(Text.of("§eFusing Bestowal with " + QuirkSystem.getFormalName(firstUserQuirkId) + "..."), false);
            } else {
                firstUserQuirkId = "";
            }
        }

        List<QuirkSystem.QuirkData.QuirkInstance> quirksToMove = new ArrayList<>();
        for(QuirkSystem.QuirkData.QuirkInstance qi : senderData.getQuirks()) {
            if (!qi.quirkId.equals(activeOFA.quirkId)) {
                quirksToMove.add(qi);
            }
        }

        senderData.getQuirks().clear();
        senderData.setSelectedQuirkIndex(0);

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

        sender.sendMessage(Text.of("§eYou have passed on the torch."), true);

        String mergedName = (firstUserQuirkId != null && !firstUserQuirkId.isEmpty()) ? QuirkSystem.getFormalName(firstUserQuirkId) : "Power";
        target.sendMessage(Text.of("§bYou have accepted One For All! (Gen " + newGen + ")"), false);
        if (newGen == 1) target.sendMessage(Text.of("§7Bestowal has merged with " + mergedName + "."), false);

        PlusUltraNetwork.sync(sender);
        PlusUltraNetwork.sync(target);
        return 1;
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
        String quirkName = QuirkSystem.getFormalName(quirkId);

        // Tag the quirk with the original owner's name if stolen from a player
        NbtCompound dataToTransfer = stolen.persistentData.copy();
        if (target instanceof PlayerEntity playerTarget) {
            dataToTransfer.putString("OriginalOwner", playerTarget.getName().getString());
        }

        attackerData.addQuirk(quirkId, false, dataToTransfer);

        boolean fullyRemoved = targetData.removeQuirk(quirkId);
        if (fullyRemoved) {
            QuirkSystem.Quirk q = QuirkRegistry.get(new Identifier(quirkId));
            if (q != null) q.onRemove(target, targetData);
        }

        attacker.sendMessage(Text.of("§cStole " + quirkName + "!"), true);
        if (target instanceof PlayerEntity tp) {
            tp.sendMessage(Text.of("§4Your quirk " + quirkName + " was stolen!"), true);
        }

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
        String quirkName = QuirkSystem.getFormalName(quirkId);

        targetData.addQuirk(quirkId, false, toGive.persistentData);

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

        PlusUltraNetwork.sync(attacker);
        PlusUltraNetwork.sync(target);
    }
}