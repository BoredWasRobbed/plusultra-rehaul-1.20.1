package net.bored;

import net.bored.api.IQuirkDataAccessor;
import net.bored.client.PlusUltraClientHandlers;
import net.bored.client.QuirkHudOverlay;
import net.bored.common.PlusUltraNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.nbt.NbtCompound;

public class PlusUltraClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		new PlusUltraClientHandlers().onInitializeClient();
		HudRenderCallback.EVENT.register(new QuirkHudOverlay());

		// CLIENT RECEIVER: Update data when server sends Sync Packet
		ClientPlayNetworking.registerGlobalReceiver(PlusUltraNetwork.SYNC_DATA, (client, handler, buf, responseSender) -> {
			NbtCompound nbt = buf.readNbt();
			client.execute(() -> {
				if (client.player != null) {
					((IQuirkDataAccessor)client.player).getQuirkData().readFromNbt(nbt);
				}
			});
		});
	}
}