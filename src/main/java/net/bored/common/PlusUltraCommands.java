package net.bored.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.bored.config.PlusUltraConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class PlusUltraCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register(PlusUltraCommands::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        var progressCommand = CommandManager.literal("progress")
                // SET
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                .then(CommandManager.argument("type", StringArgumentType.word())
                                        .suggests(PROGRESS_TYPES)
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setProgress(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "type"), IntegerArgumentType.getInteger(ctx, "amount")))))))
                // ADD
                .then(CommandManager.literal("add")
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                .then(CommandManager.argument("type", StringArgumentType.word())
                                        .suggests(PROGRESS_TYPES)
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer())
                                                .executes(ctx -> addProgress(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "type"), IntegerArgumentType.getInteger(ctx, "amount")))))))
                // GET
                .then(CommandManager.literal("get")
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                .then(CommandManager.argument("type", StringArgumentType.word())
                                        .suggests(PROGRESS_TYPES)
                                        .executes(ctx -> getProgress(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "type"))))));

        dispatcher.register(CommandManager.literal("plusultra")
                // --- PUBLIC COMMANDS ---
                .then(CommandManager.literal("accept")
                        .executes(ctx -> QuirkAttackHandler.acceptOneForAll(ctx.getSource().getPlayerOrThrow())))

                // --- OP COMMANDS (Level 2) ---
                .then(CommandManager.literal("quirk").requires(s -> s.hasPermissionLevel(2))
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
                        .then(CommandManager.literal("awaken")
                                .then(CommandManager.argument("target", EntityArgumentType.entity())
                                        .executes(ctx -> toggleAwaken(ctx.getSource(), EntityArgumentType.getEntity(ctx, "target")))))
                        .then(CommandManager.literal("list")
                                .executes(ctx -> listRegistryQuirks(ctx.getSource())))
                )
                // --- DATA SUBCOMMANDS ---
                .then(CommandManager.literal("data").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.literal("stockpilePercent")
                                .then(CommandManager.argument("target", EntityArgumentType.entity())
                                        .then(CommandManager.argument("amount", FloatArgumentType.floatArg(0, 100))
                                                .executes(ctx -> setStockpileTime(ctx.getSource(), EntityArgumentType.getEntity(ctx, "target"), FloatArgumentType.getFloat(ctx, "amount"))))))
                )
                // --- PROGRESS COMMAND (Replaces level, xp, stat, points) ---
                .then(progressCommand.requires(s -> s.hasPermissionLevel(2)))
                // --- COOLDOWN COMMAND ---
                .then(CommandManager.literal("cooldowns").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                .then(CommandManager.argument("disabled", BoolArgumentType.bool())
                                        .executes(ctx -> setCooldowns(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), BoolArgumentType.getBool(ctx, "disabled")))))
                )
        );
    }

    // --- PROGRESS HANDLERS ---

    private static int setProgress(ServerCommandSource source, ServerPlayerEntity target, String type, int amount) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) target).getQuirkData();
        switch (type.toLowerCase()) {
            case "level" -> {
                int oldLevel = data.level;
                data.level = Math.min(Math.max(amount, 1), 100); // Cap 1-100
                int diff = data.level - oldLevel;
                data.statPoints += diff; // Give/Take points based on level difference
                source.sendFeedback(() -> Text.of("Set Level to " + data.level + " (Points adjusted by " + diff + ")"), true);
            }
            case "exp" -> {
                data.experience = amount;
                source.sendFeedback(() -> Text.of("Set XP to " + amount), true);
            }
            case "points" -> {
                data.statPoints = amount;
                source.sendFeedback(() -> Text.of("Set Stat Points to " + amount), true);
            }
            case "strength" -> { data.strength = amount; source.sendFeedback(() -> Text.of("Set Strength to " + amount), true); }
            case "endurance" -> { data.endurance = amount; source.sendFeedback(() -> Text.of("Set Endurance to " + amount), true); }
            case "speed" -> { data.speed = amount; source.sendFeedback(() -> Text.of("Set Speed to " + amount), true); }
            case "stamina" -> { data.staminaMax = amount; source.sendFeedback(() -> Text.of("Set Stamina to " + amount), true); }
            case "meta" -> { data.meta = amount; source.sendFeedback(() -> Text.of("Set Meta to " + amount), true); }
            case "all_stats" -> {
                data.strength = amount;
                data.endurance = amount;
                data.speed = amount;
                data.staminaMax = amount;
                data.meta = amount;
                source.sendFeedback(() -> Text.of("Set ALL stats to " + amount), true);
            }
            default -> { source.sendError(Text.of("Invalid type: " + type)); return 0; }
        }
        PlusUltraNetwork.sync(target);
        return 1;
    }

    private static int addProgress(ServerCommandSource source, ServerPlayerEntity target, String type, int amount) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) target).getQuirkData();
        switch (type.toLowerCase()) {
            case "level" -> {
                int oldLevel = data.level;
                data.level = Math.min(data.level + amount, 100);
                int diff = data.level - oldLevel;
                data.statPoints += diff; // Add points for added levels
                source.sendFeedback(() -> Text.of("Added " + amount + " Levels (Points +" + diff + ")"), true);
            }
            case "exp" -> {
                data.addXp(amount); // Uses built-in logic which might add levels/points
                source.sendFeedback(() -> Text.of("Added " + amount + " XP"), true);
            }
            case "points" -> {
                data.statPoints += amount;
                source.sendFeedback(() -> Text.of("Added " + amount + " Stat Points"), true);
            }
            case "strength" -> { data.strength += amount; source.sendFeedback(() -> Text.of("Added " + amount + " to Strength"), true); }
            case "endurance" -> { data.endurance += amount; source.sendFeedback(() -> Text.of("Added " + amount + " to Endurance"), true); }
            case "speed" -> { data.speed += amount; source.sendFeedback(() -> Text.of("Added " + amount + " to Speed"), true); }
            case "stamina" -> { data.staminaMax += amount; source.sendFeedback(() -> Text.of("Added " + amount + " to Stamina"), true); }
            case "meta" -> { data.meta += amount; source.sendFeedback(() -> Text.of("Added " + amount + " to Meta"), true); }
            case "all_stats" -> {
                data.strength += amount;
                data.endurance += amount;
                data.speed += amount;
                data.staminaMax += amount;
                data.meta += amount;
                source.sendFeedback(() -> Text.of("Added " + amount + " to ALL stats"), true);
            }
            default -> { source.sendError(Text.of("Invalid type: " + type)); return 0; }
        }
        PlusUltraNetwork.sync(target);
        return 1;
    }

    private static int getProgress(ServerCommandSource source, ServerPlayerEntity target, String type) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) target).getQuirkData();
        String msg;
        switch (type.toLowerCase()) {
            case "level" -> msg = "Level: " + data.level;
            case "exp" -> msg = "XP: " + data.experience + "/" + (int)data.getMaxXp();
            case "points" -> msg = "Points: " + data.statPoints;
            case "strength" -> msg = "Strength: " + data.strength;
            case "endurance" -> msg = "Endurance: " + data.endurance;
            case "speed" -> msg = "Speed: " + data.speed;
            case "stamina" -> msg = "Stamina: " + data.staminaMax;
            case "meta" -> msg = "Meta: " + data.meta;
            case "all_stats" -> msg = String.format("STR:%d END:%d SPD:%d STA:%d META:%d",
                    data.strength, data.endurance, data.speed, data.staminaMax, data.meta);
            default -> { source.sendError(Text.of("Invalid type: " + type)); return 0; }
        }
        source.sendFeedback(() -> Text.of(target.getName().getString() + " - " + msg), false);
        return 1;
    }

    // --- OTHER HANDLERS ---

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

        PlusUltraConfig config = PlusUltraConfig.get();
        if (!config.isQuirkEnabled(quirkId.toString())) {
            source.sendError(Text.of("That quirk is currently disabled in the config."));
            return 0;
        }

        // Uniqueness Check
        if (config.limitUniqueQuirks && !source.hasPermissionLevel(4)) { // Op Lvl 4 overrides logic
            if (quirkId.toString().equals("plusultra:one_for_all") || quirkId.toString().equals("plusultra:all_for_one")) {
                UniqueQuirkState state = UniqueQuirkState.getServerState((ServerWorld) source.getWorld());
                if (state.isQuirkTaken(quirkId.toString())) {
                    source.sendError(Text.of("That quirk is already taken by another player in this world."));
                    return 0;
                }
            }
        }

        clearQuirks(source, target);
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) livingTarget).getQuirkData();
        data.addQuirk(quirkId.toString(), true);
        data.setSelectedQuirkIndex(0);
        if (livingTarget instanceof ServerPlayerEntity player) PlusUltraNetwork.sync(player);

        String formalName = QuirkSystem.getFormalName(quirkId.toString());
        source.sendFeedback(() -> Text.of("Set quirk " + formalName + " (Innate)"), true);
        return 1;
    }

    private static int addQuirk(ServerCommandSource source, Entity target, Identifier quirkId) {
        if (!(target instanceof LivingEntity livingTarget)) return 0;
        if (QuirkRegistry.get(quirkId) == null) return 0;

        PlusUltraConfig config = PlusUltraConfig.get();
        if (!config.isQuirkEnabled(quirkId.toString())) {
            source.sendError(Text.of("That quirk is currently disabled in the config."));
            return 0;
        }

        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) livingTarget).getQuirkData();
        data.addQuirk(quirkId.toString(), false);
        if (livingTarget instanceof ServerPlayerEntity player) PlusUltraNetwork.sync(player);

        String formalName = QuirkSystem.getFormalName(quirkId.toString());
        source.sendFeedback(() -> Text.of("Added/Stacked quirk " + formalName), true);
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

        String formalName = QuirkSystem.getFormalName(quirkId.toString());
        source.sendFeedback(() -> Text.of("Removed/Unstacked quirk " + formalName), true);
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
            sb.append(QuirkSystem.getFormalName(q)); // Use formal name
            if (q.count > 1) sb.append(" (x").append(q.count).append(")");
            if (q.innate) sb.append(" [Innate]");
            sb.append(", ");
        }
        source.sendFeedback(() -> Text.of(sb.substring(0, sb.length() - 2)), false);
        return 1;
    }

    private static int toggleAwaken(ServerCommandSource source, Entity target) {
        if (!(target instanceof LivingEntity livingTarget)) return 0;
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) livingTarget).getQuirkData();

        if (data.getQuirks().isEmpty()) {
            source.sendError(Text.of("Target has no quirks to awaken."));
            return 0;
        }

        // Awaken the currently selected quirk
        int index = data.getSelectedQuirkIndex();
        if (index >= 0 && index < data.getQuirks().size()) {
            QuirkSystem.QuirkData.QuirkInstance instance = data.getQuirks().get(index);
            instance.awakened = !instance.awakened;

            String state = instance.awakened ? "Awakened" : "Dormant";
            String quirkName = QuirkSystem.getFormalName(instance);

            source.sendFeedback(() -> Text.of("Set " + quirkName + " to " + state + " for " + target.getName().getString()), true);

            if (livingTarget instanceof ServerPlayerEntity player) {
                PlusUltraNetwork.sync(player);
                String color = instance.awakened ? "§d" : "§7";
                player.sendMessage(Text.of(color + "Your quirk has " + state.toLowerCase() + "!"), true);
            }
        }
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
                // Also update SelectedPercent so it doesn't look weird in GUI if user had it lower
                if (qi.persistentData.contains("SelectedPercent")) {
                    float selected = qi.persistentData.getFloat("SelectedPercent");
                    if (selected > amount) {
                        qi.persistentData.putFloat("SelectedPercent", amount);
                    }
                } else {
                    qi.persistentData.putFloat("SelectedPercent", amount);
                }
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

    private static final SuggestionProvider<ServerCommandSource> QUIRK_SUGGESTIONS = (ctx, builder) -> {
        QuirkRegistry.getKeys().forEach(id -> builder.suggest(id.toString()));
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> PROGRESS_TYPES = (ctx, builder) -> {
        builder.suggest("level");
        builder.suggest("exp");
        builder.suggest("points");
        builder.suggest("strength");
        builder.suggest("endurance");
        builder.suggest("speed");
        builder.suggest("stamina");
        builder.suggest("meta");
        builder.suggest("all_stats");
        return builder.buildFuture();
    };
}