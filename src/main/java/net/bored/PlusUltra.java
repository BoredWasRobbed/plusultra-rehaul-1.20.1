package net.bored;

import net.bored.common.PlusUltraCommands;
import net.bored.common.PlusUltraNetwork;
import net.bored.common.QuirkRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class PlusUltra implements ModInitializer {
	public static final String MOD_ID = "plusultra";

	@Override
	public void onInitialize() {
		PlusUltraNetwork.registerServerReceivers();
		QuirkRegistry.registerAll();
		PlusUltraCommands.register();

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			PlusUltraNetwork.sync(handler.player);
		});
	}
}