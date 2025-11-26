package net.bored.common.quirks;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.bored.common.entities.QuirkProjectileEntity;
import net.bored.config.PlusUltraConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

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
                ServerWorld world = (ServerWorld) entity.getWorld();

                // 1. Try Entity Target
                LivingEntity target = ErasureQuirk.findTargetInLineOfSight(entity, 4.0);

                if (target != null) {
                    // --- ENTITY COMPRESSION ---
                    if (target instanceof PlayerEntity && ((PlayerEntity)target).isCreative()) {
                        if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cCannot compress Creative players."), true);
                        return;
                    }

                    data.currentStamina -= this.getCost();

                    ItemStack marble = new ItemStack(Items.HEART_OF_THE_SEA);
                    NbtCompound tag = marble.getOrCreateNbt();
                    tag.putString("CompressedName", target.getName().getString());
                    marble.setCustomName(Text.of("§bCompressed: " + target.getName().getString()));

                    if (target instanceof PlayerEntity) {
                        // Player Logic
                        tag.putBoolean("IsPlayer", true);
                        tag.putUuid("CompressedUUID", target.getUuid());
                        tag.putDouble("ReturnX", target.getX());
                        tag.putDouble("ReturnY", target.getY());
                        tag.putDouble("ReturnZ", target.getZ());

                        long hash = target.getUuid().hashCode();
                        int jailX = (int)(hash % 1000000);
                        int jailZ = (int)((hash * 31) % 1000000);
                        int jailY = 250;
                        BlockPos jailCenter = new BlockPos(jailX, jailY, jailZ);

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

                        target.teleport(jailX + 0.5, jailY, jailZ + 0.5);
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 999999, 4, false, false));
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 999999, 4, false, false));
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 999999, 4, false, false));

                        if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§bPlayer compressed to dimension!"), true);

                    } else {
                        // Mob Logic
                        tag.putBoolean("IsPlayer", false);
                        NbtCompound entityNbt = new NbtCompound();
                        if (target.saveNbt(entityNbt)) {
                            tag.put("EntityData", entityNbt);
                            tag.putString("EntityType", Registries.ENTITY_TYPE.getId(target.getType()).toString());
                            target.discard();
                            if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§bMob stored in marble!"), true);
                        } else {
                            return;
                        }
                    }

                    if (entity instanceof PlayerEntity p) {
                        if (!p.getInventory().insertStack(marble)) p.dropItem(marble, false);
                    }
                    world.playSound(null, entity.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 1.5f);
                    this.triggerCooldown(instance);

                } else {
                    // 2. Try Block Target
                    HitResult hit = entity.raycast(4.5, 0, false);
                    if (hit.getType() == HitResult.Type.BLOCK) {
                        BlockPos pos = ((BlockHitResult)hit).getBlockPos();
                        BlockState state = world.getBlockState(pos);

                        if (state.isAir() || state.getHardness(world, pos) < 0) {
                            if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cCannot compress this block."), true);
                            return;
                        }

                        boolean canDestroy = !PlusUltraConfig.get().disableQuirkDestruction;
                        if (data.runtimeTags.containsKey("DESTRUCTION_DISABLED")) canDestroy = false;
                        if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING) && !(entity instanceof PlayerEntity)) canDestroy = false;

                        if (canDestroy) {
                            data.currentStamina -= this.getCost();

                            ItemStack marble = new ItemStack(Items.HEART_OF_THE_SEA);
                            NbtCompound tag = marble.getOrCreateNbt();
                            tag.putBoolean("IsBlock", true);
                            tag.put("BlockState", NbtHelper.fromBlockState(state));

                            String blockName = state.getBlock().getName().getString();
                            tag.putString("CompressedName", blockName);
                            marble.setCustomName(Text.of("§bCompressed: " + blockName));

                            world.setBlockState(pos, Blocks.AIR.getDefaultState());
                            world.playSound(null, pos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 1.5f);

                            if (entity instanceof PlayerEntity p) {
                                if (!p.getInventory().insertStack(marble)) p.dropItem(marble, false);
                                p.sendMessage(Text.of("§bBlock compressed!"), true);
                            }
                            this.triggerCooldown(instance);
                        } else {
                            if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cDestruction is disabled."), true);
                        }
                    } else {
                        if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cNothing to compress."), true);
                    }
                }
            }
        });

        // Ability 2: Decompress (Held Item)
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

                // Target location: Raycast where player is looking
                double range = 5.0;
                HitResult hit = entity.raycast(range, 0, false);
                Vec3d targetPos;

                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult bHit = (BlockHitResult)hit;
                    targetPos = Vec3d.ofBottomCenter(bHit.getBlockPos().offset(bHit.getSide()));
                } else if (hit.getType() == HitResult.Type.ENTITY) {
                    targetPos = hit.getPos();
                } else {
                    // Air: Max range
                    Vec3d look = entity.getRotationVector();
                    targetPos = entity.getEyePos().add(look.multiply(range));
                }

                decompressAt(entity.getWorld(), targetPos, stack, entity, false);
                this.triggerCooldown(instance);
            }
        });

        // Ability 3: Decompression Throw
        this.addAbility(new QuirkSystem.Ability("Decompression Throw", QuirkSystem.AbilityType.INSTANT, 20, 5, 15.0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                // Check if we are already tracking a thrown marble
                if (data.runtimeTags.containsKey("COMPRESS_THROW_ID")) {
                    int projId = Integer.parseInt(data.runtimeTags.get("COMPRESS_THROW_ID"));
                    Entity proj = entity.getWorld().getEntityById(projId);

                    if (proj instanceof QuirkProjectileEntity qp && qp.isAlive()) {
                        // TRIGGER DECOMPRESSION
                        ItemStack marble = ItemStack.fromNbt(deserializeNbt(data.runtimeTags.get("COMPRESS_MARBLE_DATA")));

                        if (isCompressedMarble(marble)) {
                            // For Throws: Falling Blocks enabled
                            decompressAt(entity.getWorld(), qp.getPos(), marble, entity, true);
                            qp.discard();
                            if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§bReleased!"), true);
                        }
                    } else {
                        if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cMarble lost or destroyed."), true);
                    }

                    // Cleanup tags
                    data.runtimeTags.remove("COMPRESS_THROW_ID");
                    data.runtimeTags.remove("COMPRESS_MARBLE_DATA");
                    this.triggerCooldown(instance);

                } else {
                    // THROW LOGIC
                    ItemStack stack = entity.getMainHandStack();
                    if (!isCompressedMarble(stack)) {
                        if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cHold a Compressed Marble to throw."), true);
                        return;
                    }

                    if (!entity.getWorld().isClient) {
                        QuirkProjectileEntity proj = new QuirkProjectileEntity(entity.getWorld(), entity, 0, 5.0f, 3); // Type 3 = Marble
                        proj.setVelocity(entity, entity.getPitch(), entity.getYaw(), 0.0F, 1.5F, 1.0F);
                        entity.getWorld().spawnEntity(proj);

                        // Store data for second press
                        data.runtimeTags.put("COMPRESS_THROW_ID", String.valueOf(proj.getId()));
                        data.runtimeTags.put("COMPRESS_MARBLE_DATA", serializeNbt(stack.writeNbt(new NbtCompound())));

                        // Consume item
                        stack.decrement(1);

                        if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§bThrown! Press again to release."), true);
                        entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 1.0f, 0.5f);
                    }
                }
            }
        });
    }

    private void decompressAt(net.minecraft.world.World world, Vec3d pos, ItemStack stack, LivingEntity owner, boolean allowFalling) {
        if (world.isClient) return;
        ServerWorld serverWorld = (ServerWorld) world;
        NbtCompound tag = stack.getNbt();

        world.playSound(null, BlockPos.ofFloored(pos), SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.PLAYERS, 1.0f, 1.0f);

        if (tag.contains("IsBlock") && tag.getBoolean("IsBlock")) {
            // --- BLOCK RELEASE ---
            BlockState state = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), tag.getCompound("BlockState"));

            // If thrown/falling enabled OR target pos is air, spawn falling block
            boolean spawnFalling = allowFalling;
            BlockPos targetPos = BlockPos.ofFloored(pos);

            // If not strictly falling mode, check if we should place it or let it fall
            if (!spawnFalling && !world.getBlockState(targetPos.down()).isSolid()) {
                // If placing in air, maybe fall? But let's stick to placement for standard ability
            }

            boolean canDestroy = !PlusUltraConfig.get().disableQuirkDestruction;
            if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) canDestroy = false;

            if (canDestroy) {
                if (spawnFalling) {
                    FallingBlockEntity fallingBlock = FallingBlockEntity.spawnFromBlock(world, targetPos, state);
                    fallingBlock.setHurtEntities(3.0f, 5); // Make it hurt people it lands on
                    // fallingBlock.setDamagePerBlock(2.0f); // Default is often sufficient, but can be tweaked
                    if (owner instanceof PlayerEntity p) p.sendMessage(Text.of("§bBlock Dropped!"), true);
                } else {
                    // Standard Placement
                    if (!world.getBlockState(targetPos).isAir()) targetPos = targetPos.up(); // Adjust if inside block
                    world.setBlockState(targetPos, state);
                    if (owner instanceof PlayerEntity p) p.sendMessage(Text.of("§bBlock Decompressed."), true);
                }
                stack.decrement(1);
            } else {
                if (owner instanceof PlayerEntity p) p.sendMessage(Text.of("§cCannot place block here (Griefing disabled)."), true);
                // Refund logic if needed (simplified: just spawn item)
                if (!canDestroy && stack.getCount() > 0) {
                    net.minecraft.entity.ItemEntity item = new net.minecraft.entity.ItemEntity(world, pos.x, pos.y, pos.z, stack.copy());
                    world.spawnEntity(item);
                }
            }
            return;
        }

        boolean isPlayer = tag.getBoolean("IsPlayer");

        if (isPlayer) {
            UUID targetId = tag.getUuid("CompressedUUID");
            // FIX: Use serverWorld instead of world to access getEntity(UUID)
            Entity target = serverWorld.getEntity(targetId);

            if (target instanceof LivingEntity livingTarget) {
                livingTarget.teleport(pos.x, pos.y, pos.z);
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
                            serverWorld.setBlockState(jailCenter.add(x, y, z), Blocks.AIR.getDefaultState());
                        }
                    }
                }
                if (owner instanceof PlayerEntity p) p.sendMessage(Text.of("§bPlayer Decompressed."), true);
                stack.decrement(1);
            } else {
                if (owner instanceof PlayerEntity p) p.sendMessage(Text.of("§cPlayer not found (Offline or Dead)."), true);
                // Drop marble back if player missing
                if (stack.getCount() <= 0) { // If this was a throw
                    net.minecraft.entity.ItemEntity item = new net.minecraft.entity.ItemEntity(world, pos.x, pos.y, pos.z, stack.copy());
                    item.setStack(stack);
                    item.getStack().setCount(1);
                    world.spawnEntity(item);
                }
            }
        } else {
            // Mob
            if (tag.contains("EntityData") && tag.contains("EntityType")) {
                String typeStr = tag.getString("EntityType");
                NbtCompound entityData = tag.getCompound("EntityData");
                EntityType<?> type = Registries.ENTITY_TYPE.get(new Identifier(typeStr));
                Entity newEntity = type.create(world);

                if (newEntity != null) {
                    newEntity.readNbt(entityData);
                    newEntity.refreshPositionAndAngles(pos.x, pos.y, pos.z, newEntity.getYaw(), newEntity.getPitch());
                    newEntity.setUuid(UUID.randomUUID());
                    world.spawnEntity(newEntity);
                    if (owner instanceof PlayerEntity p) p.sendMessage(Text.of("§bMob Decompressed."), true);
                    stack.decrement(1);
                }
            }
        }
    }

    private boolean isCompressedMarble(ItemStack stack) {
        return stack.getItem() == Items.HEART_OF_THE_SEA &&
                stack.hasNbt() &&
                (stack.getNbt().contains("CompressedUUID") || stack.getNbt().contains("EntityData") || stack.getNbt().contains("IsBlock"));
    }

    // Helper for tag serialization
    private String serializeNbt(NbtCompound nbt) { return nbt.toString(); }
    private NbtCompound deserializeNbt(String s) {
        try { return net.minecraft.nbt.StringNbtReader.parse(s); }
        catch (Exception e) { return new NbtCompound(); }
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) { }
}