package net.bored.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class PlusUltraCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register(PlusUltraCommands::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("plusultra").requires(s -> s.hasPermissionLevel(2))
                // --- QUIRK SUBCOMMANDS ---
                .then(CommandManager.literal("quirk")
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("target", EntityArgumentType.entity())
                                        .then(CommandManager.argument("quirk_id", IdentifierArgumentType.identifier())
                                                .suggests(QUIRK_SUGGESTIONS)
                                                .executes(ctx -> setQuirk(ctx.getSource(), EntityArgumentType.getEntity(ctx, "target"), IdentifierArgumentType.getIdentifier(ctx, "quirk_id"))))))
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("target", EntityArgumentType.entity())
                                        .then(CommandManager.argument("quirk_id", IdentifierArgumentType.identifier())
                                                .suggests(QUIRK_SUGGESTIONS)
                                                .executes(ctx -> addQuirk(ctx.getSource(), EntityArgumentType.getEntity(ctx, "target"), IdentifierArgumentType.getIdentifier(ctx, "quirk_id"))))))
                        .then(CommandManager.literal("remove")
                                .then(CommandManager.argument("target", EntityArgumentType.entity())
                                        .then(CommandManager.argument("quirk_id", IdentifierArgumentType.identifier())
                                                .suggests(QUIRK_SUGGESTIONS)
                                                .executes(ctx -> removeQuirk(ctx.getSource(), EntityArgumentType.getEntity(ctx, "target"), IdentifierArgumentType.getIdentifier(ctx, "quirk_id"))))))
                        .then(CommandManager.literal("clear")
                                .then(CommandManager.argument("target", EntityArgumentType.entity())
                                        .executes(ctx -> clearQuirks(ctx.getSource(), EntityArgumentType.getEntity(ctx, "target")))))
                        .then(CommandManager.literal("get")
                                .then(CommandManager.argument("target", EntityArgumentType.entity())
                                        .executes(ctx -> getQuirks(ctx.getSource(), EntityArgumentType.getEntity(ctx, "target")))))
                        .then(CommandManager.literal("list")
                                .executes(ctx -> listRegistryQuirks(ctx.getSource())))
                        // --- DATA SUBCOMMANDS ---
                        .then(CommandManager.literal("data")
                                .then(CommandManager.literal("stockpileTime")
                                        .then(CommandManager.argument("target", EntityArgumentType.entity())
                                                .then(CommandManager.argument("amount", FloatArgumentType.floatArg(0, 100))
                                                        .executes(ctx -> setStockpileTime(ctx.getSource(), EntityArgumentType.getEntity(ctx, "target"), FloatArgumentType.getFloat(ctx, "amount"))))))
                        )
                )
                // --- POINTS SUBCOMMANDS ---
                .then(CommandManager.literal("points")
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setPoints(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), IntegerArgumentType.getInteger(ctx, "amount"))))))
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer())
                                                .executes(ctx -> addPoints(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), IntegerArgumentType.getInteger(ctx, "amount"))))))
                        .then(CommandManager.literal("get")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .executes(ctx -> getPoints(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target")))))
                )
                // --- LEVEL SUBCOMMANDS ---
                .then(CommandManager.literal("level")
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 100))
                                                .executes(ctx -> setLevel(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), IntegerArgumentType.getInteger(ctx, "amount"))))))
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer())
                                                .executes(ctx -> addLevel(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), IntegerArgumentType.getInteger(ctx, "amount"))))))
                        .then(CommandManager.literal("get")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .executes(ctx -> getLevel(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target")))))
                )
                // --- XP SUBCOMMANDS ---
                .then(CommandManager.literal("xp")
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setXp(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), IntegerArgumentType.getInteger(ctx, "amount"))))))
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer())
                                                .executes(ctx -> addXp(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), IntegerArgumentType.getInteger(ctx, "amount"))))))
                        .then(CommandManager.literal("get")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .executes(ctx -> getXp(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target")))))
                )
                // --- STAT SUBCOMMANDS ---
                .then(CommandManager.literal("stat")
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .then(CommandManager.argument("stat", StringArgumentType.word())
                                                .suggests(STAT_SUGGESTIONS)
                                                .then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 50))
                                                        .executes(ctx -> setStat(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "stat"), IntegerArgumentType.getInteger(ctx, "amount")))))))
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .then(CommandManager.argument("stat", StringArgumentType.word())
                                                .suggests(STAT_SUGGESTIONS)
                                                .then(CommandManager.argument("amount", IntegerArgumentType.integer())
                                                        .executes(ctx -> addStat(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "stat"), IntegerArgumentType.getInteger(ctx, "amount")))))))
                        .then(CommandManager.literal("get")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .then(CommandManager.argument("stat", StringArgumentType.word())
                                                .suggests(STAT_SUGGESTIONS)
                                                .executes(ctx -> getStat(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "stat"))))))
                )
                // --- COOLDOWN COMMAND ---
                .then(CommandManager.literal("cooldowns")
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                .then(CommandManager.argument("disabled", BoolArgumentType.bool())
                                        .executes(ctx -> setCooldowns(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), BoolArgumentType.getBool(ctx, "disabled")))))
                )
        );
    }

    // FIX 3: Cooldown Command Implementation
    private static int setCooldowns(ServerCommandSource source, ServerPlayerEntity target, boolean disabled) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) target).getQuirkData();
        data.cooldownsDisabled = disabled;
        PlusUltraNetwork.sync(target);
        String state = disabled ? "disabled" : "enabled";
        source.sendFeedback(() -> Text.of("Cooldowns " + state + " for " + target.getName().getString()), true);
        return 1;
    }

    // --- QUIRK LOGIC ---
    private static int setQuirk(ServerCommandSource source, Entity target, Identifier quirkId) {
        if (!(target instanceof LivingEntity livingTarget)) return 0;
        if (QuirkRegistry.get(quirkId) == null) return 0;
        clearQuirks(source, target);
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) livingTarget).getQuirkData();
        data.addQuirk(quirkId.toString(), true);
        data.setSelectedQuirkIndex(0);
        if (livingTarget instanceof ServerPlayerEntity player) PlusUltraNetwork.sync(player);
        source.sendFeedback(() -> Text.of("Set quirk " + quirkId + " (Innate)"), true);
        return 1;
    }

    private static int addQuirk(ServerCommandSource source, Entity target, Identifier quirkId) {
        if (!(target instanceof LivingEntity livingTarget)) return 0;
        if (QuirkRegistry.get(quirkId) == null) return 0;
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) livingTarget).getQuirkData();
        data.addQuirk(quirkId.toString(), false);
        if (livingTarget instanceof ServerPlayerEntity player) PlusUltraNetwork.sync(player);
        source.sendFeedback(() -> Text.of("Added/Stacked quirk " + quirkId), true);
        return 1;
    }

    private static int removeQuirk(ServerCommandSource source, Entity target, Identifier quirkId) {
        if (!(target instanceof LivingEntity livingTarget)) return 0;
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) livingTarget).getQuirkData();
        boolean fullyRemoved = data.removeQuirk(quirkId.toString());
        if (fullyRemoved) {
            QuirkSystem.Quirk q = QuirkRegistry.get(quirkId);
            if (q != null) q.onRemove(livingTarget, data);
        }
        if (data.getSelectedQuirkIndex() >= data.getQuirks().size()) data.setSelectedQuirkIndex(Math.max(0, data.getQuirks().size() - 1));
        if (livingTarget instanceof ServerPlayerEntity player) PlusUltraNetwork.sync(player);
        source.sendFeedback(() -> Text.of("Removed/Unstacked quirk " + quirkId), true);
        return 1;
    }

    private static int clearQuirks(ServerCommandSource source, Entity target) {
        if (!(target instanceof LivingEntity livingTarget)) return 0;
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) livingTarget).getQuirkData();
        for (QuirkSystem.QuirkData.QuirkInstance qi : data.getQuirks()) {
            QuirkSystem.Quirk q = QuirkRegistry.get(new Identifier(qi.quirkId));
            if (q != null) q.onRemove(livingTarget, data);
        }
        data.getQuirks().clear();
        data.setSelectedQuirkIndex(0);
        if (livingTarget instanceof ServerPlayerEntity player) PlusUltraNetwork.sync(player);
        source.sendFeedback(() -> Text.of("Cleared quirks."), true);
        return 1;
    }

    private static int getQuirks(ServerCommandSource source, Entity target) {
        if (!(target instanceof LivingEntity livingTarget)) return 0;
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) livingTarget).getQuirkData();
        if (data.getQuirks().isEmpty()) {
            source.sendFeedback(() -> Text.of("No quirks."), false);
            return 1;
        }
        StringBuilder sb = new StringBuilder("Quirks: ");
        for (QuirkSystem.QuirkData.QuirkInstance q : data.getQuirks()) {
            sb.append(q.quirkId);
            if (q.count > 1) sb.append(" (x").append(q.count).append(")");
            if (q.innate) sb.append(" [Innate]");
            sb.append(", ");
        }
        source.sendFeedback(() -> Text.of(sb.substring(0, sb.length() - 2)), false);
        return 1;
    }

    private static int listRegistryQuirks(ServerCommandSource source) {
        StringBuilder sb = new StringBuilder("Registered: ");
        for (Identifier id : QuirkRegistry.getKeys()) sb.append(id.toString()).append(", ");
        source.sendFeedback(() -> Text.of(sb.toString()), false);
        return 1;
    }

    private static int setStockpileTime(ServerCommandSource source, Entity target, float amount) {
        if (!(target instanceof LivingEntity livingTarget)) return 0;
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) livingTarget).getQuirkData();

        boolean found = false;
        for (QuirkSystem.QuirkData.QuirkInstance qi : data.getQuirks()) {
            if ("plusultra:stockpile".equals(qi.quirkId)) {
                qi.persistentData.putFloat("StockpilePercent", amount);
                found = true;
                break;
            }
        }

        if (found) {
            if (livingTarget instanceof ServerPlayerEntity player) PlusUltraNetwork.sync(player);
            source.sendFeedback(() -> Text.of("Set Stockpile % to " + amount + "% for " + target.getName().getString()), true);
            return 1;
        } else {
            source.sendError(Text.of("Target does not have the Stockpile quirk."));
            return 0;
        }
    }

    // --- POINTS LOGIC ---
    private static int setPoints(ServerCommandSource source, ServerPlayerEntity target, int amount) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) target).getQuirkData();
        data.statPoints = amount;
        PlusUltraNetwork.sync(target);
        source.sendFeedback(() -> Text.of("Set points for " + target.getName().getString() + " to " + amount), true);
        return 1;
    }

    private static int addPoints(ServerCommandSource source, ServerPlayerEntity target, int amount) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) target).getQuirkData();
        data.statPoints += amount;
        PlusUltraNetwork.sync(target);
        source.sendFeedback(() -> Text.of("Added " + amount + " points to " + target.getName().getString()), true);
        return 1;
    }

    private static int getPoints(ServerCommandSource source, ServerPlayerEntity target) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) target).getQuirkData();
        source.sendFeedback(() -> Text.of(target.getName().getString() + " has " + data.statPoints + " points."), false);
        return 1;
    }

    // --- LEVEL LOGIC ---
    private static int setLevel(ServerCommandSource source, ServerPlayerEntity target, int amount) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) target).getQuirkData();
        data.level = amount;
        // Optionally recalculate stat points if needed based on new level, but manual set is raw.
        PlusUltraNetwork.sync(target);
        source.sendFeedback(() -> Text.of("Set Level for " + target.getName().getString() + " to " + amount), true);
        return 1;
    }

    private static int addLevel(ServerCommandSource source, ServerPlayerEntity target, int amount) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) target).getQuirkData();
        data.level = Math.min(data.level + amount, 100);
        // Often adding levels adds stat points
        data.statPoints += amount;
        PlusUltraNetwork.sync(target);
        source.sendFeedback(() -> Text.of("Added " + amount + " levels to " + target.getName().getString()), true);
        return 1;
    }

    private static int getLevel(ServerCommandSource source, ServerPlayerEntity target) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) target).getQuirkData();
        source.sendFeedback(() -> Text.of(target.getName().getString() + " is Level " + data.level), false);
        return 1;
    }

    // --- XP LOGIC ---
    private static int setXp(ServerCommandSource source, ServerPlayerEntity target, int amount) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) target).getQuirkData();
        data.experience = amount;
        // Check if this causes level up? For 'set' usually we just set the value.
        // To auto-level, use addXp logic.
        PlusUltraNetwork.sync(target);
        source.sendFeedback(() -> Text.of("Set XP for " + target.getName().getString() + " to " + amount), true);
        return 1;
    }

    private static int addXp(ServerCommandSource source, ServerPlayerEntity target, int amount) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) target).getQuirkData();
        data.addXp(amount); // Uses built-in level up logic
        PlusUltraNetwork.sync(target);
        source.sendFeedback(() -> Text.of("Added " + amount + " XP to " + target.getName().getString()), true);
        return 1;
    }

    private static int getXp(ServerCommandSource source, ServerPlayerEntity target) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) target).getQuirkData();
        source.sendFeedback(() -> Text.of(target.getName().getString() + " has " + data.experience + " XP (Max: " + (int)data.getMaxXp() + ")"), false);
        return 1;
    }

    // --- STAT LOGIC ---
    private static int setStat(ServerCommandSource source, ServerPlayerEntity target, String stat, int amount) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) target).getQuirkData();
        if ("all".equalsIgnoreCase(stat)) {
            data.strength = amount;
            data.endurance = amount;
            data.speed = amount;
            data.staminaMax = amount;
            data.meta = amount;
            PlusUltraNetwork.sync(target);
            source.sendFeedback(() -> Text.of("Set ALL stats to " + amount), true);
            return 1;
        }

        boolean found = applyStatChange(data, stat, amount, true);
        if (found) {
            PlusUltraNetwork.sync(target);
            source.sendFeedback(() -> Text.of("Set " + stat + " to " + amount), true);
            return 1;
        }
        source.sendError(Text.of("Invalid stat: " + stat));
        return 0;
    }

    private static int addStat(ServerCommandSource source, ServerPlayerEntity target, String stat, int amount) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) target).getQuirkData();
        if ("all".equalsIgnoreCase(stat)) {
            data.strength += amount;
            data.endurance += amount;
            data.speed += amount;
            data.staminaMax += amount;
            data.meta += amount;
            PlusUltraNetwork.sync(target);
            source.sendFeedback(() -> Text.of("Added " + amount + " to ALL stats"), true);
            return 1;
        }

        boolean found = applyStatChange(data, stat, amount, false);
        if (found) {
            PlusUltraNetwork.sync(target);
            source.sendFeedback(() -> Text.of("Added " + amount + " to " + stat), true);
            return 1;
        }
        source.sendError(Text.of("Invalid stat: " + stat));
        return 0;
    }

    private static int getStat(ServerCommandSource source, ServerPlayerEntity target, String stat) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) target).getQuirkData();
        if ("strength".equalsIgnoreCase(stat)) source.sendFeedback(() -> Text.of("Strength: " + data.strength), false);
        else if ("endurance".equalsIgnoreCase(stat)) source.sendFeedback(() -> Text.of("Endurance: " + data.endurance), false);
        else if ("speed".equalsIgnoreCase(stat)) source.sendFeedback(() -> Text.of("Speed: " + data.speed), false);
        else if ("stamina".equalsIgnoreCase(stat)) source.sendFeedback(() -> Text.of("Stamina: " + data.staminaMax), false);
        else if ("meta".equalsIgnoreCase(stat)) source.sendFeedback(() -> Text.of("Meta: " + data.meta), false);
        else if ("all".equalsIgnoreCase(stat)) {
            source.sendFeedback(() -> Text.of("STR:" + data.strength + " END:" + data.endurance + " SPD:" + data.speed + " STA:" + data.staminaMax + " META:" + data.meta), false);
        } else {
            source.sendError(Text.of("Invalid stat."));
            return 0;
        }
        return 1;
    }

    private static boolean applyStatChange(QuirkSystem.QuirkData data, String stat, int val, boolean isSet) {
        switch (stat.toLowerCase()) {
            case "strength" -> data.strength = isSet ? val : data.strength + val;
            case "endurance" -> data.endurance = isSet ? val : data.endurance + val;
            case "speed" -> data.speed = isSet ? val : data.speed + val;
            case "stamina" -> data.staminaMax = isSet ? val : data.staminaMax + val;
            case "meta" -> data.meta = isSet ? val : data.meta + val;
            default -> { return false; }
        }
        return true;
    }

    private static final SuggestionProvider<ServerCommandSource> QUIRK_SUGGESTIONS = (ctx, builder) -> {
        QuirkRegistry.getKeys().forEach(id -> builder.suggest(id.toString()));
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> STAT_SUGGESTIONS = (ctx, builder) -> {
        builder.suggest("strength");
        builder.suggest("endurance");
        builder.suggest("speed");
        builder.suggest("stamina");
        builder.suggest("meta");
        builder.suggest("all");
        return builder.buildFuture();
    };
}