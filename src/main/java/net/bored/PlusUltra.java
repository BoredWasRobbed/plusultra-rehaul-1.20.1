package net.bored;

import net.bored.common.PlusUltraCommands;
import net.bored.common.PlusUltraNetwork;
import net.bored.common.QuirkAttackHandler;
import net.bored.common.QuirkRegistry;
import net.bored.common.entities.FlickProjectileEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class PlusUltra implements ModInitializer {
	public static final String MOD_ID = "plusultra";

	// Register Flick Entity
	public static final EntityType<FlickProjectileEntity> FLICK_PROJECTILE = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier(MOD_ID, "flick_projectile"),
			FabricEntityTypeBuilder.<FlickProjectileEntity>create(SpawnGroup.MISC, FlickProjectileEntity::new)
					.dimensions(EntityDimensions.fixed(0.5f, 0.5f))
					.trackRangeBlocks(64).trackedUpdateRate(10)
					.build()
	);

	@Override
	public void onInitialize() {
		PlusUltraNetwork.registerServerReceivers();
		QuirkRegistry.registerAll();
		PlusUltraCommands.register();
		QuirkAttackHandler.register();

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			PlusUltraNetwork.sync(handler.player);
		});
	}
}