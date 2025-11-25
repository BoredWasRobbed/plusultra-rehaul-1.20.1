package net.bored.common.quirks;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public class CompressQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "compress");

    public CompressQuirk() { super(ID); }

    @Override
    public int getIconColor() { return 0xFF0000AA; } // Dark Blue

    @Override
    public void registerAbilities() {
        // Ability 1: Compress
        this.addAbility(new QuirkSystem.Ability("Compress", QuirkSystem.AbilityType.INSTANT, 60, 1, 25.0) {
            @Override
            public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                return target != null && distanceSquared < 9.0;
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                entity.swingHand(Hand.MAIN_HAND, true);

                // Find Target using Erasure logic
                LivingEntity target = ErasureQuirk.findTargetInLineOfSight(entity, 3.0);

                if (target == null) {
                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cNo target to compress."), true);
                    return;
                }

                if (target instanceof PlayerEntity && ((PlayerEntity)target).isCreative()) {
                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cCannot compress Creative players."), true);
                    return;
                }

                data.currentStamina -= this.getCost();

                ItemStack marble = new ItemStack(Items.HEART_OF_THE_SEA);
                NbtCompound tag = marble.getOrCreateNbt();
                tag.putString("CompressedName", target.getName().getString());
                marble.setCustomName(Text.of("§bCompressed: " + target.getName().getString()));

                ServerWorld world = (ServerWorld) entity.getWorld();

                if (target instanceof PlayerEntity) {
                    // --- PLAYER LOGIC: SEND TO JAIL ---
                    tag.putBoolean("IsPlayer", true);
                    tag.putUuid("CompressedUUID", target.getUuid());

                    // Store return location
                    tag.putDouble("ReturnX", target.getX());
                    tag.putDouble("ReturnY", target.getY());
                    tag.putDouble("ReturnZ", target.getZ());

                    // Calculate Unique "Area" Coordinates
                    long hash = target.getUuid().hashCode();
                    int jailX = (int)(hash % 1000000);
                    int jailZ = (int)((hash * 31) % 1000000);
                    int jailY = 250;

                    BlockPos jailCenter = new BlockPos(jailX, jailY, jailZ);

                    // Build Jail (Barrier Box)
                    for (int x = -1; x <= 1; x++) {
                        for (int y = -1; y <= 2; y++) {
                            for (int z = -1; z <= 1; z++) {
                                BlockPos p = jailCenter.add(x, y, z);
                                if (x == 0 && z == 0 && y >= 0 && y <= 1) {
                                    world.setBlockState(p, Blocks.AIR.getDefaultState());
                                } else {
                                    world.setBlockState(p, Blocks.BARRIER.getDefaultState());
                                }
                            }
                        }
                    }

                    // Teleport Target
                    target.teleport(jailX + 0.5, jailY, jailZ + 0.5);
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 999999, 4, false, false));
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 999999, 4, false, false));
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 999999, 4, false, false));

                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§bPlayer compressed to dimension!"), true);

                } else {
                    // --- MOB LOGIC: STORE IN ITEM ---
                    tag.putBoolean("IsPlayer", false);

                    NbtCompound entityNbt = new NbtCompound();
                    if (target.saveNbt(entityNbt)) {
                        tag.put("EntityData", entityNbt);
                        tag.putString("EntityType", Registries.ENTITY_TYPE.getId(target.getType()).toString());

                        target.discard(); // Remove from world immediately

                        if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§bMob stored in marble!"), true);
                    } else {
                        if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cFailed to compress mob."), true);
                        return;
                    }
                }

                // Give Item
                if (entity instanceof PlayerEntity p) {
                    if (!p.getInventory().insertStack(marble)) {
                        p.dropItem(marble, false);
                    }
                }

                world.playSound(null, entity.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 1.5f);
                this.triggerCooldown(instance);
            }
        });

        // Ability 2: Release
        this.addAbility(new QuirkSystem.Ability("Decompress", QuirkSystem.AbilityType.INSTANT, 10, 1, 5.0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                ItemStack stack = entity.getMainHandStack();
                if (!isCompressedMarble(stack)) {
                    stack = entity.getOffHandStack();
                    if (!isCompressedMarble(stack)) {
                        if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cHold a Compressed Marble to release."), true);
                        return;
                    }
                }

                NbtCompound tag = stack.getNbt();
                ServerWorld world = (ServerWorld) entity.getWorld();
                boolean isPlayer = tag.getBoolean("IsPlayer");

                if (isPlayer) {
                    // --- PLAYER RELEASE ---
                    UUID targetId = tag.getUuid("CompressedUUID");
                    Entity target = world.getEntity(targetId);

                    if (target instanceof LivingEntity livingTarget) {
                        livingTarget.teleport(entity.getX(), entity.getY(), entity.getZ());

                        livingTarget.removeStatusEffect(StatusEffects.RESISTANCE);
                        livingTarget.removeStatusEffect(StatusEffects.WEAKNESS);
                        livingTarget.removeStatusEffect(StatusEffects.SLOWNESS);

                        // Cleanup Jail
                        long hash = targetId.hashCode();
                        int jailX = (int)(hash % 1000000);
                        int jailZ = (int)((hash * 31) % 1000000);
                        int jailY = 250;
                        BlockPos jailCenter = new BlockPos(jailX, jailY, jailZ);

                        for (int x = -1; x <= 1; x++) {
                            for (int y = -1; y <= 2; y++) {
                                for (int z = -1; z <= 1; z++) {
                                    world.setBlockState(jailCenter.add(x, y, z), Blocks.AIR.getDefaultState());
                                }
                            }
                        }

                        if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§bPlayer Decompressed."), true);
                        world.playSound(null, entity.getBlockPos(), SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        stack.decrement(1);
                    } else {
                        if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cPlayer not found (Offline or Dead)."), true);
                    }
                } else {
                    // --- MOB RELEASE ---
                    if (tag.contains("EntityData") && tag.contains("EntityType")) {
                        String typeStr = tag.getString("EntityType");
                        NbtCompound entityData = tag.getCompound("EntityData");

                        EntityType<?> type = Registries.ENTITY_TYPE.get(new Identifier(typeStr));
                        Entity newEntity = type.create(world);

                        if (newEntity != null) {
                            newEntity.readNbt(entityData);
                            // Ensure it spawns at user, not old location
                            newEntity.refreshPositionAndAngles(entity.getX(), entity.getY(), entity.getZ(), entity.getYaw(), entity.getPitch());
                            newEntity.setUuid(UUID.randomUUID()); // Generate new UUID to avoid conflicts

                            world.spawnEntity(newEntity);

                            if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§bMob Decompressed."), true);
                            world.playSound(null, entity.getBlockPos(), SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.PLAYERS, 1.0f, 1.0f);
                            stack.decrement(1);
                        }
                    }
                }

                this.triggerCooldown(instance);
            }
        });
    }

    private boolean isCompressedMarble(ItemStack stack) {
        return stack.getItem() == Items.HEART_OF_THE_SEA &&
                stack.hasNbt() &&
                (stack.getNbt().contains("CompressedUUID") || stack.getNbt().contains("EntityData"));
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) { }
}