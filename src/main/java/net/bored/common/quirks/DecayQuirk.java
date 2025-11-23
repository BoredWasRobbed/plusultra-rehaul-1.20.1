package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.bored.config.PlusUltraConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;

public class DecayQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "decay");

    public DecayQuirk() { super(ID); }

    @Override
    public int getIconColor() { return 0xFFDDDDDD; } // Pale Gray

    @Override
    public void registerAbilities() {
        // Ability 1: Decay Grip (Grab)
        this.addAbility(new QuirkSystem.Ability("Decay Grip", QuirkSystem.AbilityType.INSTANT, 100, 1, 15.0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                float multiplier = getPowerMultiplier(instance.count, data);

                double range = 3.5;
                Vec3d start = entity.getCameraPosVec(1.0f);
                Vec3d direction = entity.getRotationVector();
                Vec3d end = start.add(direction.multiply(range));
                Box box = entity.getBoundingBox().stretch(direction.multiply(range)).expand(1.0);

                Entity target = null;
                double closestDist = range * range;

                for (Entity e : entity.getWorld().getOtherEntities(entity, box)) {
                    if (e instanceof LivingEntity) {
                        float border = e.getTargetingMargin() + 0.5f;
                        Box hitBox = e.getBoundingBox().expand(border);
                        if (hitBox.raycast(start, end).isPresent()) {
                            double d = start.squaredDistanceTo(e.getPos());
                            if (d < closestDist) {
                                target = e;
                                closestDist = d;
                            }
                        }
                    }
                }

                if (target instanceof LivingEntity livingTarget) {
                    entity.swingHand(Hand.MAIN_HAND, true);

                    float dmg = 6.0f * multiplier;
                    livingTarget.damage(entity.getDamageSources().magic(), dmg);

                    livingTarget.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 10, false, false));
                    livingTarget.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 5, false, false));
                    livingTarget.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 1, false, false));

                    for (ItemStack stack : livingTarget.getArmorItems()) {
                        if (!stack.isEmpty() && stack.isDamageable()) {
                            int shred = 50 + (int)(50 * multiplier);
                            stack.damage(shred, livingTarget, (p) -> {});
                        }
                    }

                    if (entity.getWorld() instanceof ServerWorld world) {
                        world.spawnParticles(ParticleTypes.ASH, livingTarget.getX(), livingTarget.getY() + 1, livingTarget.getZ(), 20, 0.5, 1.0, 0.5, 0.1);
                        world.playSound(null, livingTarget.getX(), livingTarget.getY(), livingTarget.getZ(), SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 1.0f, 0.5f);
                    }

                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§7Decayed Target!"), true);
                } else {
                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cMissed Grab!"), true);
                }

                data.currentStamina -= this.getCost();
                this.triggerCooldown();
            }
        });

        // Ability 2: Ground Rot (Infectious Spread)
        this.addAbility(new QuirkSystem.Ability("Ground Rot", QuirkSystem.AbilityType.INSTANT, 150, 10, 25.0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                float multiplier = getPowerMultiplier(instance.count, data);

                // META SCALING: Radius
                int radius = 4 + (int)multiplier + (data.meta / 5);

                // FIX: Check block BELOW if current is air to ensure we hit the ground
                BlockPos start = entity.getBlockPos();
                if (entity.getWorld().isAir(start)) {
                    start = start.down();
                }

                // Start the infection
                data.runtimeTags.put("DECAY_ROT_ACTIVE", "true");
                data.runtimeTags.put("DECAY_ROT_START", String.valueOf(entity.getWorld().getTime()));
                data.runtimeTags.put("DECAY_ROT_CENTER_X", String.valueOf(start.getX()));
                data.runtimeTags.put("DECAY_ROT_CENTER_Y", String.valueOf(start.getY()));
                data.runtimeTags.put("DECAY_ROT_CENTER_Z", String.valueOf(start.getZ()));
                data.runtimeTags.put("DECAY_ROT_RADIUS", String.valueOf(radius));

                // Initialize the "Active Rim" with the starting blocks
                Set<Long> rim = new HashSet<>();
                rim.add(start.asLong());

                data.runtimeTags.put("DECAY_ROT_RIM", serializePosSet(rim));
                data.runtimeTags.put("DECAY_ROT_VISITED", serializePosSet(rim));

                entity.swingHand(Hand.MAIN_HAND, true);
                if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§8Ground Rot spreading..."), true);

                data.currentStamina -= this.getCost();
                this.triggerCooldown();
            }
        });

        // Ability 3: Dust Stream
        this.addAbility(new QuirkSystem.Ability("Dust Stream", QuirkSystem.AbilityType.INSTANT, 100, 20, 20.0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                data.runtimeTags.put("DECAY_STREAM_ACTIVE", "true");
                data.runtimeTags.put("DECAY_STREAM_TICKS", "0");

                int maxTicks = 20 + (data.meta / 2);
                data.runtimeTags.put("DECAY_STREAM_MAX", String.valueOf(maxTicks));

                Vec3d lookDir = entity.getRotationVector();
                Vec3d horizDir = new Vec3d(lookDir.x, 0, lookDir.z).normalize();

                BlockPos startPos = entity.getBlockPos().down().add((int)Math.round(horizDir.x), 0, (int)Math.round(horizDir.z));

                data.runtimeTags.put("DECAY_STREAM_DX", String.valueOf(horizDir.x));
                data.runtimeTags.put("DECAY_STREAM_DZ", String.valueOf(horizDir.z));
                data.runtimeTags.put("DECAY_STREAM_X", String.valueOf(startPos.getX()));
                data.runtimeTags.put("DECAY_STREAM_Y", String.valueOf(startPos.getY()));
                data.runtimeTags.put("DECAY_STREAM_Z", String.valueOf(startPos.getZ()));

                entity.swingHand(Hand.MAIN_HAND, true);
                if (entity.getWorld() instanceof ServerWorld w) {
                    w.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BLOCK_SAND_FALL, SoundCategory.PLAYERS, 1.0f, 0.8f);
                }

                data.currentStamina -= this.getCost();
                this.triggerCooldown();
            }
        });

        // Ability 4: Sudden Pit
        this.addAbility(new QuirkSystem.Ability("Sudden Pit", QuirkSystem.AbilityType.INSTANT, 300, 30, 40.0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                HitResult hit = entity.raycast(20.0, 0, false);
                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHit = (BlockHitResult) hit;
                    BlockPos center = blockHit.getBlockPos();

                    if (entity.getWorld() instanceof ServerWorld world) {
                        boolean canDestroy = !PlusUltraConfig.get().disableQuirkDestruction;
                        if (data.runtimeTags.containsKey("DESTRUCTION_DISABLED")) canDestroy = false;

                        world.spawnParticles(ParticleTypes.ASH, center.getX()+0.5, center.getY()+1.0, center.getZ()+0.5, 30, 0.5, 0.5, 0.5, 0.1);
                        world.playSound(null, center, SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 1.0f, 0.5f);

                        int depth = 8 + (data.meta / 5);

                        if (canDestroy) {
                            for (int y = 0; y > -depth; y--) {
                                for (int x = -1; x <= 1; x++) {
                                    for (int z = -1; z <= 1; z++) {
                                        BlockPos p = center.add(x, y, z);
                                        if (world.getBlockState(p).getHardness(world, p) >= 0) {
                                            world.breakBlock(p, false);
                                        }
                                    }
                                }
                            }
                        } else {
                            Box box = new Box(center).expand(2.0, 4.0, 2.0);
                            world.getEntitiesByClass(LivingEntity.class, box, e -> e != entity).forEach(e -> {
                                e.damage(entity.getDamageSources().magic(), 10.0f);
                                e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 1));
                            });
                        }
                    }
                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§8Pit target struck!"), true);
                } else {
                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cNo block targeted."), true);
                }

                data.currentStamina -= this.getCost();
                this.triggerCooldown();
            }
        });

        // Ability 5: Catastrophe
        this.addAbility(new QuirkSystem.Ability("Catastrophe", QuirkSystem.AbilityType.HOLD, 1200, 50, 100.0) {
            @Override
            public void onRelease(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                int maxR = 25 + (data.meta / 2);

                data.runtimeTags.put("DECAY_CAT_ACTIVE", "true");
                data.runtimeTags.put("DECAY_CAT_TICKS", "0");
                data.runtimeTags.put("DECAY_CAT_X", String.valueOf(entity.getX()));
                data.runtimeTags.put("DECAY_CAT_Y", String.valueOf(entity.getY()));
                data.runtimeTags.put("DECAY_CAT_Z", String.valueOf(entity.getZ()));
                data.runtimeTags.put("DECAY_CAT_MAX_R", String.valueOf(maxR));

                if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§4§lCATASTROPHE!"), true);
                entity.swingHand(Hand.MAIN_HAND, true);

                data.currentStamina -= this.getCost();
                this.triggerCooldown();
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (entity.getWorld() instanceof ServerWorld world) {
                    world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.PLAYERS, 0.2f, 0.5f);
                }
            }
        });
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        World world = entity.getWorld();
        if (world.isClient) return;
        ServerWorld serverWorld = (ServerWorld) world;
        boolean canDestroy = !PlusUltraConfig.get().disableQuirkDestruction;
        if (data.runtimeTags.containsKey("DESTRUCTION_DISABLED")) canDestroy = false;

        // --- Handle Ground Rot Spread (Connected BFS) ---
        if (data.runtimeTags.containsKey("DECAY_ROT_ACTIVE")) {
            int centerX = Integer.parseInt(data.runtimeTags.get("DECAY_ROT_CENTER_X"));
            int centerY = Integer.parseInt(data.runtimeTags.get("DECAY_ROT_CENTER_Y"));
            int centerZ = Integer.parseInt(data.runtimeTags.get("DECAY_ROT_CENTER_Z"));
            int maxRadius = Integer.parseInt(data.runtimeTags.get("DECAY_ROT_RADIUS"));
            BlockPos centerPos = new BlockPos(centerX, centerY, centerZ);

            // Deserialize state
            Set<Long> currentRim = deserializePosSet(data.runtimeTags.get("DECAY_ROT_RIM"));
            Set<Long> visited = deserializePosSet(data.runtimeTags.get("DECAY_ROT_VISITED"));
            Set<Long> nextRim = new HashSet<>();

            // If rim is empty, stop
            if (currentRim.isEmpty()) {
                data.runtimeTags.remove("DECAY_ROT_ACTIVE");
                data.runtimeTags.remove("DECAY_ROT_RIM");
                data.runtimeTags.remove("DECAY_ROT_VISITED");
            } else {
                boolean changesMade = false;

                for (Long posLong : currentRim) {
                    BlockPos pos = BlockPos.fromLong(posLong);

                    // Check all 6 neighbors
                    for (Direction dir : Direction.values()) {
                        BlockPos neighbor = pos.offset(dir);
                        long nLong = neighbor.asLong();

                        // 1. Don't revisit
                        if (visited.contains(nLong)) continue;

                        // 2. Radius Check
                        if (Math.sqrt(neighbor.getSquaredDistance(centerPos)) > maxRadius) continue;

                        // 3. Safety Check (Center & Below Center are safe)
                        if (neighbor.equals(centerPos) || neighbor.equals(centerPos.down())) {
                            visited.add(nLong); // Mark visited so we don't check again
                            nextRim.add(nLong); // Continue spreading FROM safe spot
                            continue;
                        }

                        // 4. Air Rule: Only spread to non-air blocks (Connected)
                        // FIX: If it's air, we don't spread INTO it, nor THROUGH it.
                        if (serverWorld.isAir(neighbor)) {
                            continue;
                        } else {
                            // Valid solid block
                            visited.add(nLong);
                            nextRim.add(nLong);
                            changesMade = true;

                            // VFX
                            if (serverWorld.random.nextFloat() < 0.3f) {
                                serverWorld.spawnParticles(ParticleTypes.ASH, neighbor.getX()+0.5, neighbor.getY()+0.5, neighbor.getZ()+0.5, 1, 0.1, 0.1, 0.1, 0);
                            }

                            // Destruction
                            if (canDestroy && isDestructible(serverWorld, neighbor)) {
                                serverWorld.breakBlock(neighbor, false);
                            }

                            // Damage
                            Box box = new Box(neighbor);
                            world.getEntitiesByClass(LivingEntity.class, box, e -> e != entity).forEach(e -> {
                                e.damage(entity.getDamageSources().magic(), 4.0f);
                                e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 40, 1));
                            });
                        }
                    }
                }

                if (!changesMade || nextRim.isEmpty()) {
                    data.runtimeTags.remove("DECAY_ROT_ACTIVE");
                } else {
                    // Serialize and save for next tick
                    data.runtimeTags.put("DECAY_ROT_RIM", serializePosSet(nextRim));

                    // Optimization: We only need 'visited' to block backtracking.
                    // Since we are adding to the string, we just append.
                    String visitedStr = data.runtimeTags.get("DECAY_ROT_VISITED");
                    if (visitedStr.length() < 30000) { // Safety cap for NBT string
                        data.runtimeTags.put("DECAY_ROT_VISITED", serializePosSet(visited));
                    } else {
                        // Emergency stop if too big
                        data.runtimeTags.remove("DECAY_ROT_ACTIVE");
                    }
                }
            }
        }

        // --- Handle Dust Stream Spread ---
        if (data.runtimeTags.containsKey("DECAY_STREAM_ACTIVE")) {
            int ticks = Integer.parseInt(data.runtimeTags.get("DECAY_STREAM_TICKS"));
            int maxTicks = Integer.parseInt(data.runtimeTags.getOrDefault("DECAY_STREAM_MAX", "20"));

            if (ticks > maxTicks) {
                data.runtimeTags.remove("DECAY_STREAM_ACTIVE");
            } else {
                int cx = Integer.parseInt(data.runtimeTags.get("DECAY_STREAM_X"));
                int cy = Integer.parseInt(data.runtimeTags.get("DECAY_STREAM_Y"));
                int cz = Integer.parseInt(data.runtimeTags.get("DECAY_STREAM_Z"));
                BlockPos currentPos = new BlockPos(cx, cy, cz);

                double dx = Double.parseDouble(data.runtimeTags.get("DECAY_STREAM_DX"));
                double dz = Double.parseDouble(data.runtimeTags.get("DECAY_STREAM_DZ"));

                BlockPos nextFlat = currentPos.add((int)Math.round(dx), 0, (int)Math.round(dz));
                BlockPos finalNext = null;
                boolean shouldStop = false;

                if (!serverWorld.isAir(nextFlat)) {
                    if (canDestroy && isDestructible(serverWorld, nextFlat)) {
                        finalNext = nextFlat;
                        serverWorld.breakBlock(nextFlat, false);
                        serverWorld.breakBlock(nextFlat.up(), false);
                    } else {
                        shouldStop = true;
                    }
                } else {
                    boolean foundGround = false;
                    for (int i = 1; i <= 4; i++) {
                        BlockPos check = nextFlat.down(i);
                        if (!serverWorld.isAir(check)) {
                            finalNext = check.up();
                            foundGround = true;
                            break;
                        }
                    }
                    if (!foundGround) shouldStop = true;
                }

                if (shouldStop || finalNext == null) {
                    data.runtimeTags.remove("DECAY_STREAM_ACTIVE");
                    serverWorld.spawnParticles(ParticleTypes.SMOKE, cx+0.5, cy+0.5, cz+0.5, 5, 0.1, 0.1, 0.1, 0);
                    return;
                }

                data.runtimeTags.put("DECAY_STREAM_X", String.valueOf(finalNext.getX()));
                data.runtimeTags.put("DECAY_STREAM_Y", String.valueOf(finalNext.getY()));
                data.runtimeTags.put("DECAY_STREAM_Z", String.valueOf(finalNext.getZ()));

                if (finalNext.getY() < cy) {
                    for (int y = cy; y > finalNext.getY(); y--) {
                        serverWorld.spawnParticles(ParticleTypes.ASH, finalNext.getX()+0.5, y+0.5, finalNext.getZ()+0.5, 5, 0.2, 0.2, 0.2, 0);
                    }
                }

                serverWorld.spawnParticles(ParticleTypes.ASH, finalNext.getX()+0.5, finalNext.getY()+0.5, finalNext.getZ()+0.5, 10, 0.4, 0.4, 0.4, 0);

                // Destructive: 3x3 Rough Area
                if (canDestroy) {
                    for (int xx = -1; xx <= 1; xx++) {
                        for (int zz = -1; zz <= 1; zz++) {
                            boolean isCenter = (xx == 0 && zz == 0);
                            float chance = isCenter ? 1.0f : 0.6f;

                            if (serverWorld.random.nextFloat() < chance) {
                                BlockPos target = finalNext.add(xx, 0, zz);
                                if (isDestructible(serverWorld, target) && !serverWorld.isAir(target)) {
                                    serverWorld.breakBlock(target, false);
                                }
                                if (serverWorld.random.nextFloat() < (isCenter ? 1.0f : 0.5f)) {
                                    BlockPos targetUp = target.up();
                                    if (isDestructible(serverWorld, targetUp) && !serverWorld.isAir(targetUp)) {
                                        serverWorld.breakBlock(targetUp, false);
                                    }
                                }
                            }
                        }
                    }
                }

                Box box = new Box(finalNext).expand(1.5, 1.5, 1.5);
                world.getEntitiesByClass(LivingEntity.class, box, e -> e != entity).forEach(e -> {
                    e.damage(entity.getDamageSources().magic(), 12.0f);
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 1));
                });

                data.runtimeTags.put("DECAY_STREAM_TICKS", String.valueOf(ticks + 1));
            }
        }

        // --- Handle Catastrophe Spread (Keep simple sphere for Ultimate) ---
        if (data.runtimeTags.containsKey("DECAY_CAT_ACTIVE")) {
            int ticks = Integer.parseInt(data.runtimeTags.get("DECAY_CAT_TICKS"));
            double sx = Double.parseDouble(data.runtimeTags.get("DECAY_CAT_X"));
            double sy = Double.parseDouble(data.runtimeTags.get("DECAY_CAT_Y"));
            double sz = Double.parseDouble(data.runtimeTags.get("DECAY_CAT_Z"));
            int maxR = Integer.parseInt(data.runtimeTags.getOrDefault("DECAY_CAT_MAX_R", "25"));

            int currentR = ticks / 2;

            if (currentR > maxR) {
                data.runtimeTags.remove("DECAY_CAT_ACTIVE");
            } else {
                if (currentR % 2 == 0) {
                    serverWorld.spawnParticles(ParticleTypes.ASH, sx, sy+1, sz, currentR * 10, currentR/2.0, 1, currentR/2.0, 0.1);
                    serverWorld.playSound(null, sx, sy, sz, SoundEvents.BLOCK_SCULK_BREAK, SoundCategory.HOSTILE, 1.0f, 0.5f);
                }

                if (canDestroy) {
                    destroySphere(serverWorld, new BlockPos((int)sx, (int)sy, (int)sz), currentR);
                }
                damageEntitiesInRadius(serverWorld, new Vec3d(sx, sy, sz), currentR, entity, 10.0f);

                data.runtimeTags.put("DECAY_CAT_TICKS", String.valueOf(ticks + 1));
            }
        }
    }

    private boolean isDestructible(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getHardness(world, pos) >= 0;
    }

    private String serializePosSet(Set<Long> set) {
        StringBuilder sb = new StringBuilder();
        for(Long l : set) {
            if(sb.length() > 0) sb.append(";");
            sb.append(l);
        }
        return sb.toString();
    }

    private Set<Long> deserializePosSet(String s) {
        Set<Long> set = new HashSet<>();
        if (s == null || s.isEmpty()) return set;
        String[] parts = s.split(";");
        for(String p : parts) {
            try {
                set.add(Long.parseLong(p));
            } catch(NumberFormatException e) {}
        }
        return set;
    }

    private void destroySphere(ServerWorld world, BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double d = Math.sqrt(x*x + y*y + z*z);
                    if (d <= radius && d > radius - 1.5) {
                        BlockPos p = center.add(x, y, z);
                        if (world.getBlockState(p).getHardness(world, p) >= 0 && !world.isAir(p)) {
                            if (world.random.nextFloat() < 0.6f) {
                                world.breakBlock(p, false);
                            }
                        }
                    }
                }
            }
        }
    }

    private void damageEntitiesInRadius(ServerWorld world, Vec3d center, double radius, LivingEntity source, float damage) {
        Box box = new Box(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius);
        world.getEntitiesByClass(LivingEntity.class, box, e -> e != source && e.squaredDistanceTo(center) <= radius * radius).forEach(e -> {
            e.damage(source.getDamageSources().magic(), damage);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 1));
        });
    }
}