package net.bored;

import net.bored.api.IQuirkDataAccessor;
import net.bored.client.AFOHudOverlay; // Added Import
import net.bored.client.PlusUltraClientHandlers;
import net.bored.client.QuirkHudOverlay;
import net.bored.common.PlusUltraNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

public class PlusUltraClient implements ClientModInitializer {

	private static final Map<Integer, NbtCompound> pendingSyncs = new HashMap<>();
	private static final Map<Integer, Long> pendingSyncTimestamps = new HashMap<>();

	@Override
	public void onInitializeClient() {
		new PlusUltraClientHandlers().onInitializeClient();

		// Register Standard Overlay
		HudRenderCallback.EVENT.register(new QuirkHudOverlay());

		// Register NEW AFO Sight Overlay
		HudRenderCallback.EVENT.register(new AFOHudOverlay());

		EntityRendererRegistry.register(PlusUltra.FLICK_PROJECTILE, EmptyEntityRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(PlusUltraNetwork.SYNC_DATA, (client, handler, buf, responseSender) -> {
			int entityId = buf.readInt();
			NbtCompound nbt = buf.readNbt();

			client.execute(() -> {
				if (client.world != null) {
					Entity entity = client.world.getEntityById(entityId);
					if (entity instanceof LivingEntity living) {
						((IQuirkDataAccessor)living).getQuirkData().readFromNbt(nbt);
					} else {
						pendingSyncs.put(entityId, nbt);
						pendingSyncTimestamps.put(entityId, System.currentTimeMillis());
					}
				}
			});
		});

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

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.world == null) {
				pendingSyncs.clear();
				pendingSyncTimestamps.clear();
				return;
			}

			if (!pendingSyncs.isEmpty()) {
				Iterator<Map.Entry<Integer, NbtCompound>> it = pendingSyncs.entrySet().iterator();
				long now = System.currentTimeMillis();

				while (it.hasNext()) {
					Map.Entry<Integer, NbtCompound> entry = it.next();
					int id = entry.getKey();
					Entity entity = client.world.getEntityById(id);

					if (entity instanceof LivingEntity living) {
						((IQuirkDataAccessor)living).getQuirkData().readFromNbt(entry.getValue());
						it.remove();
						pendingSyncTimestamps.remove(id);
					} else {
						if (now - pendingSyncTimestamps.getOrDefault(id, 0L) > 5000) {
							it.remove();
							pendingSyncTimestamps.remove(id);
						}
					}
				}
			}
		});

		ClientTickEvents.END_WORLD_TICK.register(world -> {
			if (!PlusUltraClientHandlers.afoSightActive) return;

			for (Entity entity : world.getEntities()) {
				if (entity instanceof LivingEntity living) {
					if (entity == net.minecraft.client.MinecraftClient.getInstance().player) continue;

					if (((IQuirkDataAccessor)living).getQuirkData().getQuirks().size() > 0) {
						if (world.getRandom().nextFloat() < 0.15f) {
							double x = entity.getX() + (world.getRandom().nextDouble() - 0.5);
							double y = entity.getY() + entity.getHeight() + (world.getRandom().nextDouble() * 0.5);
							double z = entity.getZ() + (world.getRandom().nextDouble() - 0.5);

							world.addParticle(ParticleTypes.WITCH, x, y, z, 0, 0.05, 0);
						}
					}
				}
			}
		});
	}
}