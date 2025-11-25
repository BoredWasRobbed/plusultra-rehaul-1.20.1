package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.bored.config.PlusUltraConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

public class DecayQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "decay");

    public DecayQuirk() { super(ID); }

    @Override
    public int getIconColor() { return 0xFFDDDDDD; }

    @Override
    public void registerAbilities() {
        // Ability 1: Decay Grip
        this.addAbility(new QuirkSystem.Ability("Decay Grip", QuirkSystem.AbilityType.HOLD, 100, 1, 5.0) {
            @Override
            public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                return target != null && distanceSquared < 9.0;
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                data.runtimeTags.put("DECAY_GRIP_ACTIVE", "true");
                entity.swingHand(Hand.MAIN_HAND, true);
            }

            @Override
            public void onRelease(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (data.runtimeTags.containsKey("DECAY_GRIP_ACTIVE")) {
                    data.runtimeTags.remove("DECAY_GRIP_ACTIVE");
                    this.triggerCooldown(instance);
                }
            }
        });

        // Ability 2: Ground Rot
        this.addAbility(new QuirkSystem.Ability("Ground Rot", QuirkSystem.AbilityType.HOLD, 150, 10, 10.0) {
            @Override
            public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                return instance.awakened && target != null && distanceSquared < 100.0 && distanceSquared > 16.0;
            }

            @Override
            public boolean isHidden(QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                // If Awakened, show regardless of level. If not Awakened, check level via super.
                if (instance.awakened) return false;
                return super.isHidden(data, instance);
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                // Double check in case of packet spoofing
                if (!instance.awakened) return;

                float multiplier = getPowerMultiplier(instance.count, data);
                int radius = 10 + (int)multiplier + (data.meta * 3);
                BlockPos start = entity.getBlockPos();
                if (entity.getWorld().isAir(start)) {
                    start = start.down();
                }

                data.runtimeTags.put("DECAY_ROT_ACTIVE", "true");
                data.runtimeTags.put("DECAY_ROT_CENTER_X", String.valueOf(start.getX()));
                data.runtimeTags.put("DECAY_ROT_CENTER_Y", String.valueOf(start.getY()));
                data.runtimeTags.put("DECAY_ROT_CENTER_Z", String.valueOf(start.getZ()));
                data.runtimeTags.put("DECAY_ROT_RADIUS", String.valueOf(radius));
                data.runtimeTags.put("DECAY_ROT_CURRENT_R", "1.0");

                if (entity.isSneaking()) {
                    data.runtimeTags.put("DECAY_ROT_MODE", "OBJECT");
                    BlockState targetState = entity.getWorld().getBlockState(start);
                    String blockId = Registries.BLOCK.getId(targetState.getBlock()).toString();
                    data.runtimeTags.put("DECAY_ROT_TARGET_ID", blockId);
                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§5Decaying Object: " + targetState.getBlock().getName().getString()), true);
                } else {
                    data.runtimeTags.put("DECAY_ROT_MODE", "NORMAL");
                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§8Ground Rot spreading... (Max Radius: " + radius + ")"), true);
                }

                Set<Long> rim = new HashSet<>();
                rim.add(start.asLong());
                data.runtimeTags.put("DECAY_ROT_RIM", serializePosSet(rim));
                data.runtimeTags.put("DECAY_ROT_VISITED", serializePosSet(rim));

                entity.swingHand(Hand.MAIN_HAND, true);
            }

            @Override
            public void onRelease(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (data.runtimeTags.containsKey("DECAY_ROT_ACTIVE")) {
                    data.runtimeTags.remove("DECAY_ROT_ACTIVE");
                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§7Rot stopped."), true);
                    this.triggerCooldown(instance);
                }
            }
        });

        // Ability 3: Dust Stream
        this.addAbility(new QuirkSystem.Ability("Dust Stream", QuirkSystem.AbilityType.HOLD, 100, 20, 8.0) {
            @Override
            public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                return instance.awakened && target != null && distanceSquared > 25.0 && distanceSquared < 400.0;
            }

            @Override
            public void onAIUse(LivingEntity user, LivingEntity target, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                user.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target.getPos());
                super.onAIUse(user, target, data, instance);
            }

            @Override
            public boolean isHidden(QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                // If Awakened, show regardless of level.
                if (instance.awakened) return false;
                return super.isHidden(data, instance);
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (!instance.awakened) return;

                data.runtimeTags.put("DECAY_STREAM_ACTIVE", "true");
                data.runtimeTags.put("DECAY_STREAM_TICKS", "0");
                int maxTicks = 100 + (data.meta);
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
            }

            @Override
            public void onRelease(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (data.runtimeTags.containsKey("DECAY_STREAM_ACTIVE")) {
                    data.runtimeTags.remove("DECAY_STREAM_ACTIVE");
                    this.triggerCooldown(instance);
                }
            }
        });

        // Ability 4: Sudden Pit
        this.addAbility(new QuirkSystem.Ability("Sudden Pit", QuirkSystem.AbilityType.HOLD, 300, 30, 15.0) {
            @Override
            public boolean isHidden(QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                // If Awakened, show regardless of level.
                if (instance.awakened) return false;
                return super.isHidden(data, instance);
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (!instance.awakened) return;

                HitResult hit = entity.raycast(20.0, 0, false);
                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHit = (BlockHitResult) hit;
                    BlockPos center = blockHit.getBlockPos();
                    data.runtimeTags.put("DECAY_PIT_ACTIVE", "true");
                    data.runtimeTags.put("DECAY_PIT_X", String.valueOf(center.getX()));
                    data.runtimeTags.put("DECAY_PIT_Y", String.valueOf(center.getY()));
                    data.runtimeTags.put("DECAY_PIT_Z", String.valueOf(center.getZ()));
                    data.runtimeTags.put("DECAY_PIT_DEPTH", "0");
                } else {
                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cNo block targeted."), true);
                }
            }

            @Override
            public void onRelease(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (data.runtimeTags.containsKey("DECAY_PIT_ACTIVE")) {
                    data.runtimeTags.remove("DECAY_PIT_ACTIVE");
                    this.triggerCooldown(instance);
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
        if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING) && !(entity instanceof PlayerEntity)) canDestroy = false;

        // --- Ability 1: Decay Grip ---
        if (data.runtimeTags.containsKey("DECAY_GRIP_ACTIVE")) {
            if (data.currentStamina < 2.0) {
                data.runtimeTags.remove("DECAY_GRIP_ACTIVE");
                this.getAbilities().get(0).triggerCooldown(instance);
                if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cOut of Stamina!"), true);
            } else {
                data.currentStamina -= 2.0;
                double range = 4.0;
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
                    float multiplier = getPowerMultiplier(instance.count, data);
                    float dmg = 1.5f * multiplier;
                    livingTarget.damage(entity.getDamageSources().magic(), dmg);
                    livingTarget.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 40, 0, false, false));
                    serverWorld.spawnParticles(ParticleTypes.ASH, livingTarget.getX(), livingTarget.getY() + 1, livingTarget.getZ(), 5, 0.2, 0.5, 0.2, 0.05);
                }
                serverWorld.spawnParticles(ParticleTypes.SMOKE, start.x + direction.x, start.y + direction.y, start.z + direction.z, 2, 0, 0, 0, 0);
            }
            if (entity instanceof ServerPlayerEntity sp) PlusUltraNetwork.sync(sp);
        }

        // --- Ability 2: Ground Rot ---
        if (data.runtimeTags.containsKey("DECAY_ROT_ACTIVE")) {
            if (data.currentStamina < 2.0) {
                data.runtimeTags.remove("DECAY_ROT_ACTIVE");
                this.getAbilities().get(1).triggerCooldown(instance);
                if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cOut of Stamina!"), true);
            } else {
                data.currentStamina -= 2.0;
                int centerX = Integer.parseInt(data.runtimeTags.get("DECAY_ROT_CENTER_X"));
                int centerY = Integer.parseInt(data.runtimeTags.get("DECAY_ROT_CENTER_Y"));
                int centerZ = Integer.parseInt(data.runtimeTags.get("DECAY_ROT_CENTER_Z"));
                int maxRadius = Integer.parseInt(data.runtimeTags.get("DECAY_ROT_RADIUS"));

                float currentR = Float.parseFloat(data.runtimeTags.getOrDefault("DECAY_ROT_CURRENT_R", "1.0"));
                if (currentR < maxRadius) {
                    currentR += 0.25f;
                    data.runtimeTags.put("DECAY_ROT_CURRENT_R", String.valueOf(currentR));
                }

                BlockPos centerPos = new BlockPos(centerX, centerY, centerZ);
                String mode = data.runtimeTags.getOrDefault("DECAY_ROT_MODE", "NORMAL");
                String targetBlockId = data.runtimeTags.getOrDefault("DECAY_ROT_TARGET_ID", "");

                Set<Long> currentRim = deserializePosSet(data.runtimeTags.get("DECAY_ROT_RIM"));
                Set<Long> visited = deserializePosSet(data.runtimeTags.get("DECAY_ROT_VISITED"));
                Set<Long> nextRim = new HashSet<>();

                if (currentRim.isEmpty()) {
                    data.runtimeTags.remove("DECAY_ROT_ACTIVE");
                } else {
                    boolean changesMade = false;
                    for (Long posLong : currentRim) {
                        BlockPos pos = BlockPos.fromLong(posLong);

                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                if (dx == 0 && dz == 0) continue;

                                for (int dy = -1; dy <= 1; dy++) {
                                    BlockPos neighbor = pos.add(dx, dy, dz);
                                    long nLong = neighbor.asLong();

                                    if (visited.contains(nLong)) continue;
                                    if (Math.sqrt(neighbor.getSquaredDistance(centerPos)) > currentR) continue;
                                    if (neighbor.equals(centerPos) || neighbor.equals(centerPos.down())) {
                                        visited.add(nLong);
                                        nextRim.add(nLong);
                                        continue;
                                    }

                                    if ("OBJECT".equals(mode)) {
                                        BlockState neighborState = serverWorld.getBlockState(neighbor);
                                        String nId = Registries.BLOCK.getId(neighborState.getBlock()).toString();

                                        if (!nId.equals(targetBlockId)) continue;

                                        visited.add(nLong);
                                        nextRim.add(nLong);
                                        changesMade = true;

                                        if (serverWorld.random.nextFloat() < 0.3f) {
                                            serverWorld.spawnParticles(ParticleTypes.ASH, neighbor.getX()+0.5, neighbor.getY()+0.5, neighbor.getZ()+0.5, 1, 0.1, 0.1, 0.1, 0);
                                        }
                                        if (canDestroy) {
                                            serverWorld.breakBlock(neighbor, false);
                                        }
                                    } else {
                                        if (neighbor.getY() < centerY - 5) continue;
                                        if (serverWorld.isAir(neighbor)) continue;

                                        visited.add(nLong);
                                        nextRim.add(nLong);
                                        changesMade = true;

                                        if (serverWorld.random.nextFloat() < 0.3f) {
                                            serverWorld.spawnParticles(ParticleTypes.ASH, neighbor.getX()+0.5, neighbor.getY()+0.5, neighbor.getZ()+0.5, 1, 0.1, 0.1, 0.1, 0);
                                        }
                                        if (canDestroy && isDestructible(serverWorld, neighbor)) {
                                            serverWorld.breakBlock(neighbor, false);
                                        }
                                    }

                                    Box box = new Box(neighbor);
                                    world.getEntitiesByClass(LivingEntity.class, box, e -> e != entity).forEach(e -> {
                                        e.damage(entity.getDamageSources().magic(), 4.0f);
                                        e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 40, 1));
                                    });
                                }
                            }
                        }
                    }

                    if (!nextRim.isEmpty()) {
                        data.runtimeTags.put("DECAY_ROT_RIM", serializePosSet(nextRim));
                        data.runtimeTags.put("DECAY_ROT_VISITED", serializePosSet(visited));
                    } else {
                        if (currentR >= maxRadius) {
                            data.runtimeTags.remove("DECAY_ROT_ACTIVE");
                        }
                    }
                }
            }
            if (entity instanceof ServerPlayerEntity sp) PlusUltraNetwork.sync(sp);
        }

        // --- Ability 3: Dust Stream ---
        if (data.runtimeTags.containsKey("DECAY_STREAM_ACTIVE")) {
            if (data.currentStamina < 2.0) {
                data.runtimeTags.remove("DECAY_STREAM_ACTIVE");
                this.getAbilities().get(2).triggerCooldown(instance);
                if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cOut of Stamina!"), true);
            } else {
                data.currentStamina -= 2.0;
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
                    serverWorld.spawnParticles(ParticleTypes.ASH, finalNext.getX()+0.5, finalNext.getY()+0.5, finalNext.getZ()+0.5, 10, 0.4, 0.4, 0.4, 0);

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
            if (entity instanceof ServerPlayerEntity sp) PlusUltraNetwork.sync(sp);
        }

        // --- Ability 4: Sudden Pit ---
        if (data.runtimeTags.containsKey("DECAY_PIT_ACTIVE")) {
            if (data.currentStamina < 3.0) {
                data.runtimeTags.remove("DECAY_PIT_ACTIVE");
                this.getAbilities().get(3).triggerCooldown(instance);
                if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cOut of Stamina!"), true);
            } else {
                data.currentStamina -= 3.0;
                int cx = Integer.parseInt(data.runtimeTags.get("DECAY_PIT_X"));
                int cy = Integer.parseInt(data.runtimeTags.get("DECAY_PIT_Y"));
                int cz = Integer.parseInt(data.runtimeTags.get("DECAY_PIT_Z"));
                int depth = Integer.parseInt(data.runtimeTags.get("DECAY_PIT_DEPTH"));
                int maxDepth = 8 + (data.meta / 5);

                if (depth < maxDepth && canDestroy) {
                    BlockPos currentCenter = new BlockPos(cx, cy - depth, cz);
                    for (int x = -1; x <= 1; x++) {
                        for (int z = -1; z <= 1; z++) {
                            BlockPos p = currentCenter.add(x, 0, z);
                            if (isDestructible(serverWorld, p) && !serverWorld.isAir(p)) {
                                serverWorld.breakBlock(p, false);
                                serverWorld.spawnParticles(ParticleTypes.ASH, p.getX()+0.5, p.getY()+0.5, p.getZ()+0.5, 2, 0.2, 0.2, 0.2, 0);
                            }
                        }
                    }
                    serverWorld.playSound(null, currentCenter, SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 0.5f, 0.5f);
                    data.runtimeTags.put("DECAY_PIT_DEPTH", String.valueOf(depth + 1));
                }
                BlockPos damageCenter = new BlockPos(cx, cy, cz);
                Box box = new Box(damageCenter).expand(2.0, 10.0, 2.0);
                world.getEntitiesByClass(LivingEntity.class, box, e -> e != entity).forEach(e -> {
                    e.damage(entity.getDamageSources().magic(), 5.0f);
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 40, 1));
                });
            }
            if (entity instanceof ServerPlayerEntity sp) PlusUltraNetwork.sync(sp);
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
}