package net.bored.common;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.bored.common.quirks.NewOrderQuirk;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public class NewOrderHandlers {

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (sender == null) return true;

            // Check if player has New Order
            QuirkSystem.QuirkData data = ((IQuirkDataAccessor)sender).getQuirkData();
            QuirkSystem.QuirkData.QuirkInstance newOrderInstance = null;

            for (QuirkSystem.QuirkData.QuirkInstance q : data.getQuirks()) {
                if (q.quirkId.equals("plusultra:new_order")) {
                    newOrderInstance = q;
                    break;
                }
            }

            if (newOrderInstance == null) return true; // Pass through normal chat

            String content = message.getContent().getString().trim();

            // 1. CHECK FOR TARGET NAMING
            if (tryTargeting(sender, data, content)) {
                return true; // It was a targeting command, but let it show in chat
            }

            // 2. CHECK FOR RULE ACTIVATION
            if (tryActivateRule(sender, data, newOrderInstance, content)) {
                return true;
            }

            return true;
        });
    }

    private static boolean tryTargeting(ServerPlayerEntity player, QuirkSystem.QuirkData data, String message) {
        // Simple heuristic: Is the message an entity type name?
        Entity foundTarget = null;

        // A. Check Player Names first
        ServerPlayerEntity targetPlayer = player.getServer().getPlayerManager().getPlayer(message);
        if (targetPlayer != null) {
            // Allow self-targeting by name
            if (targetPlayer == player || canSee(player, targetPlayer)) {
                foundTarget = targetPlayer;
            } else {
                // Radius check if not looking directly
                if (player.distanceTo(targetPlayer) < 50) {
                    foundTarget = targetPlayer;
                }
            }
        }

        // B. Check Entity Types
        if (foundTarget == null) {
            Optional<EntityType<?>> type = Registries.ENTITY_TYPE.getOrEmpty(new Identifier("minecraft", message.toLowerCase().replace(" ", "_")));
            if (type.isPresent()) {
                foundTarget = findClosestEntityOfType(player, type.get(), 50.0);
            }
        }

        if (foundTarget != null) {
            data.runtimeTags.put("NEW_ORDER_TARGET", foundTarget.getUuid().toString());
            data.runtimeTags.put("NEW_ORDER_TARGET_NAME", foundTarget.getName().getString());
            player.sendMessage(Text.of("§e[New Order] Locked onto: " + foundTarget.getName().getString()), true);
            player.playSound(net.minecraft.sound.SoundEvents.ITEM_SPYGLASS_USE, 1.0f, 1.0f);
            return true;
        }

        return false;
    }

    private static boolean tryActivateRule(ServerPlayerEntity player, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance, String message) {
        // Retrieve saved rules from NBT
        if (!instance.persistentData.contains("SavedRules")) return false;

        NbtList savedRules = instance.persistentData.getList("SavedRules", NbtElement.COMPOUND_TYPE);

        for (int i = 0; i < savedRules.size(); i++) {
            NbtCompound ruleData = savedRules.getCompound(i);
            String savedPhrase = ruleData.getString("Phrase");
            String effect = ruleData.getString("Effect");
            String targetType = ruleData.getString("TargetType");
            int cost = ruleData.getInt("Cost");

            if (savedPhrase.equalsIgnoreCase(message)) {
                // MATCH FOUND!

                // 1. Check Stamina
                if (data.currentStamina < cost) {
                    player.sendMessage(Text.of("§cNot enough stamina! (" + cost + ")"), true);
                    return true;
                }

                // 2. Handle Logic based on Target Type
                if ("TOUCH".equals(targetType)) {
                    // "Toggle" mode: Prime the next hit

                    // Determine who gets the "Primed" status
                    // If a specific target is locked, prime THEM (so when THEY hit someone, it triggers?)
                    // OR, does it mean "I prime myself to hit them?"
                    // User said: "say your own name... next time you... hit someone"
                    // User said: "say someone else's... next time them hit someone"

                    Entity primedEntity = player; // Default to priming self

                    if (data.runtimeTags.containsKey("NEW_ORDER_TARGET")) {
                        String targetUuidStr = data.runtimeTags.get("NEW_ORDER_TARGET");
                        UUID targetUuid = UUID.fromString(targetUuidStr);
                        Entity target = ((ServerWorld)player.getWorld()).getEntity(targetUuid);
                        if (target != null && target.isAlive()) {
                            primedEntity = target;
                        }
                    }

                    if (primedEntity instanceof LivingEntity livingPrimed && livingPrimed instanceof IQuirkDataAccessor accessor) {
                        QuirkSystem.QuirkData primedData = accessor.getQuirkData();
                        primedData.runtimeTags.put("NEW_ORDER_TOUCH_READY", "true");
                        primedData.runtimeTags.put("NEW_ORDER_TOUCH_EFFECT", effect);
                        // Store cost to deduct on trigger? Or deduct now?
                        // Deducting now is simpler.
                        data.currentStamina -= cost;

                        player.sendMessage(Text.of("§e[New Order] " + primedEntity.getName().getString() + " is ready to strike (" + effect + ")"), true);
                        player.playSound(net.minecraft.sound.SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
                    }

                } else {
                    // FOCUSED (Immediate)
                    if (!data.runtimeTags.containsKey("NEW_ORDER_TARGET")) {
                        // If no target selected, try targeting SELF implicitly if phrase implies it?
                        // No, user removed SELF button, so FOCUSED requires selection.
                        // Fallback: If phrase spoken, check if it matches user name? No, separate step.
                        player.sendMessage(Text.of("§cNo target selected! Say a name first."), true);
                        return true;
                    }

                    String targetUuidStr = data.runtimeTags.get("NEW_ORDER_TARGET");
                    UUID targetUuid = UUID.fromString(targetUuidStr);
                    Entity target = ((ServerWorld)player.getWorld()).getEntity(targetUuid);

                    if (target == null || !target.isAlive() || player.distanceTo(target) > 64.0) {
                        player.sendMessage(Text.of("§cTarget lost or out of range."), true);
                        return true;
                    }

                    // Activate
                    NewOrderQuirk quirk = (NewOrderQuirk) net.bored.common.QuirkRegistry.get(NewOrderQuirk.ID);
                    if (quirk != null) {
                        data.currentStamina -= cost;
                        quirk.activateRule(player, data, instance, savedPhrase, effect, target);
                    }
                }

                return true;
            }
        }

        return false;
    }

    private static boolean canSee(LivingEntity user, Entity target) {
        Vec3d start = user.getCameraPosVec(1.0f);
        Vec3d end = target.getCameraPosVec(1.0f);
        HitResult result = user.getWorld().raycast(new net.minecraft.world.RaycastContext(start, end, net.minecraft.world.RaycastContext.ShapeType.VISUAL, net.minecraft.world.RaycastContext.FluidHandling.NONE, user));
        return result.getType() == HitResult.Type.MISS || (result.getType() == HitResult.Type.ENTITY && ((EntityHitResult)result).getEntity() == target);
    }

    private static Entity findClosestEntityOfType(ServerPlayerEntity player, EntityType<?> type, double range) {
        Box searchBox = player.getBoundingBox().expand(range);
        Entity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity e : player.getWorld().getOtherEntities(player, searchBox)) {
            if (e.getType() == type) {
                double d = player.squaredDistanceTo(e);
                if (d < closestDist) {
                    closestDist = d;
                    closest = e;
                }
            }
        }
        return closest;
    }
}