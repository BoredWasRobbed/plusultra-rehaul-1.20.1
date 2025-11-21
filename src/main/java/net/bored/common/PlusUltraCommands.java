package net.bored.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
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
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                .then(CommandManager.argument("quirk_id", IdentifierArgumentType.identifier())
                                        .suggests(QUIRK_SUGGESTIONS)
                                        .executes(ctx -> setQuirk(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), IdentifierArgumentType.getIdentifier(ctx, "quirk_id"))))))
        );
    }

    private static int setQuirk(ServerCommandSource source, ServerPlayerEntity target, Identifier quirkId) {
        if (QuirkRegistry.get(quirkId) == null) {
            source.sendError(Text.of("Quirk not found."));
            return 0;
        }
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) target).getQuirkData();
        data.getQuirks().clear();
        data.getQuirks().add(new QuirkSystem.QuirkData.QuirkInstance(quirkId.toString()));
        data.setSelectedQuirkIndex(0);

        // CRITICAL: Sync to Client so HUD updates
        PlusUltraNetwork.sync(target);

        source.sendFeedback(() -> Text.of("Quirk Set!"), true);
        return 1;
    }

    private static final SuggestionProvider<ServerCommandSource> QUIRK_SUGGESTIONS = (ctx, builder) -> {
        QuirkRegistry.getKeys().forEach(id -> builder.suggest(id.toString()));
        return builder.buildFuture();
    };
}