package net.bored;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraCommands;
import net.bored.common.PlusUltraNetwork;
import net.bored.common.QuirkAttackHandler;
import net.bored.common.QuirkRegistry;
import net.bored.common.entities.QuirkProjectileEntity;
import net.bored.config.PlusUltraConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PlusUltra implements ModInitializer {
	public static final String MOD_ID = "plusultra";

	public static final EntityType<QuirkProjectileEntity> QUIRK_PROJECTILE = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier(MOD_ID, "flick_projectile"),
			FabricEntityTypeBuilder.<QuirkProjectileEntity>create(SpawnGroup.MISC, QuirkProjectileEntity::new)
					.dimensions(EntityDimensions.fixed(0.5f, 0.5f))
					.trackRangeBlocks(64).trackedUpdateRate(10)
					.build()
	);

	@Override
	public void onInitialize() {
		PlusUltraConfig.load();

		PlusUltraNetwork.registerServerReceivers();
		QuirkRegistry.registerAll();
		PlusUltraConfig.get().populateQuirkDefaults();

		PlusUltraCommands.register();
		QuirkAttackHandler.register();

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			IQuirkDataAccessor accessor = (IQuirkDataAccessor) player;
			QuirkSystem.QuirkData data = accessor.getQuirkData();

			// Ensure blood type is set immediately upon joining if missing (e.g. old saves)
			if (data.bloodType == null || data.bloodType.isEmpty()) {
				data.assignRandomBloodType();
			}

			if (!data.persistentData.contains("PlusUltraJoined")) {
				data.persistentData.putBoolean("PlusUltraJoined", true);
				assignRandomQuirk(player, data);
				PlusUltraNetwork.sync(player);
			} else {
				PlusUltraNetwork.sync(player);
			}
		});

		// COPY_FROM handles data transfer on Death or Dimension Switch
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			IQuirkDataAccessor oldAccessor = (IQuirkDataAccessor) oldPlayer;
			IQuirkDataAccessor newAccessor = (IQuirkDataAccessor) newPlayer;

			// Get the data reference
			QuirkSystem.QuirkData oldData = oldAccessor.getQuirkData();

			// --- HARDCORE LOGIC START ---
			if (!alive) { // If player DIED (not dimension change)
				PlusUltraConfig config = PlusUltraConfig.get();

				// Increment death counter
				oldData.deaths++;

				if (config.hardcoreMode) {
					// Check if lives are used up
					if (oldData.deaths >= config.lives) {

						// Reset counter (New cycle)
						oldData.deaths = 0;

						newPlayer.sendMessage(Text.of("§c§lHARDCORE RESET TRIGGERED!"), false);

						// 1. Level & Stats Reset
						if (config.levelReset) {
							oldData.level = 1;
							oldData.experience = 0;
							oldData.statPoints = 1;
							oldData.strength = 0;
							oldData.endurance = 0;
							oldData.speed = 0;
							oldData.staminaMax = 0;
							oldData.meta = 0;
						} else {
							// Partial resets if full level reset isn't on
							if (config.expReset) oldData.experience = 0;

							if (config.pointsReset) oldData.statPoints = 1; // Default is 1

							if (config.statsReset) {
								oldData.strength = 0;
								oldData.endurance = 0;
								oldData.speed = 0;
								oldData.staminaMax = 0;
								oldData.meta = 0;
							}
						}

						// 2. Quirk Reset & Reroll
						if (config.quirkReset) {
							// Remove all quirks
							oldData.getQuirks().clear();
							oldData.setSelectedQuirkIndex(0);

							// Reroll new quirk based on config rules
							assignRandomQuirk(newPlayer, oldData);
						}
						// 3. Awakening Reset (Only if quirks weren't wiped entirely)
						else if (config.awakeningReset) {
							for (QuirkSystem.QuirkData.QuirkInstance qi : oldData.getQuirks()) {
								qi.awakened = false;
							}
						}
					}
				}
			}
			// --- HARDCORE LOGIC END ---

			NbtCompound nbt = new NbtCompound();
			oldData.writeToNbt(nbt);
			newAccessor.getQuirkData().readFromNbt(nbt);
		});

		// FIX: Use AFTER_RESPAWN to sync data.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			PlusUltraNetwork.sync(newPlayer);
		});

		EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
			if (trackedEntity instanceof LivingEntity living) {
				PlusUltraNetwork.syncToPlayer(living, player);
			}
		});
	}

	private void assignRandomQuirk(ServerPlayerEntity player, QuirkSystem.QuirkData data) {
		List<Identifier> validQuirks = new ArrayList<>();
		PlusUltraConfig config = PlusUltraConfig.get();

		for (Identifier id : QuirkRegistry.getKeys()) {
			String idStr = id.toString();

			if (!config.isQuirkEnabled(idStr)) continue;

			// Filter out special unique quirks
			if (idStr.equals("plusultra:one_for_all") || idStr.equals("plusultra:all_for_one")) continue;
			if (idStr.equals("plusultra:quirk_bestowal")) continue;

			// Filter out MOB ONLY quirks for default player spawn
			if (idStr.equals("plusultra:antigen_swap")) continue;
			if (idStr.equals("plusultra:bloodlet")) continue;
			if (idStr.equals("plusultra:luminescence")) continue;

			validQuirks.add(id);
		}

		if (!validQuirks.isEmpty()) {
			Identifier randomQuirk = validQuirks.get(new Random().nextInt(validQuirks.size()));
			data.addQuirk(randomQuirk.toString(), true);
			player.sendMessage(Text.of("§eYou awakened with the quirk: §6" + getFormalName(randomQuirk.toString())), false);
		} else {
			player.sendMessage(Text.of("§7You were born Quirkless..."), false);
		}
	}

	private String getFormalName(String quirkId) {
		try {
			String path = new Identifier(quirkId).getPath().replace("_", " ");
			StringBuilder sb = new StringBuilder();
			for (String s : path.split(" ")) {
				if (!s.isEmpty()) sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1).toLowerCase()).append(" ");
			}
			return sb.toString().trim();
		} catch (Exception e) { return quirkId; }
	}
}