package net.bored;

import net.bored.api.IQuirkDataAccessor;
import net.bored.client.PlusUltraClientHandlers;
import net.bored.client.QuirkHudOverlay;
import net.bored.common.PlusUltraNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.List;

public class PlusUltraClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		new PlusUltraClientHandlers().onInitializeClient();
		HudRenderCallback.EVENT.register(new QuirkHudOverlay());

		ClientPlayNetworking.registerGlobalReceiver(PlusUltraNetwork.SYNC_DATA, (client, handler, buf, responseSender) -> {
			NbtCompound nbt = buf.readNbt();
			client.execute(() -> {
				if (client.player != null) {
					((IQuirkDataAccessor)client.player).getQuirkData().readFromNbt(nbt);
				}
			});
		});

		// NEW: Receiver to open the Steal Selection Screen
		ClientPlayNetworking.registerGlobalReceiver(PlusUltraNetwork.OPEN_STEAL_SELECTION, (client, handler, buf, responseSender) -> {
			int targetId = buf.readInt();
			int size = buf.readInt();
			List<String> quirks = new ArrayList<>();
			for (int i = 0; i < size; i++) {
				quirks.add(buf.readString());
			}

			client.execute(() -> {
				client.setScreen(new PlusUltraClientHandlers.StealSelectionScreen(targetId, quirks));
			});
		});
	}
}