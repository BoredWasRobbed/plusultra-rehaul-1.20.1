package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.bored.common.entities.QuirkProjectileEntity;
import net.bored.config.PlusUltraConfig;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.List;

public class StockpileQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "stockpile");

    public StockpileQuirk() { super(ID); }

    @Override
    public void registerAbilities() {
        this.addAbility(new QuirkSystem.Ability("Smash", QuirkSystem.AbilityType.INSTANT, 60, 1, 10.0) {
            @Override
            public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                return target != null && distanceSquared < 16.0;
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                float maxStock = instance.persistentData.getFloat("StockpilePercent");
                float tempSelected = instance.persistentData.contains("SelectedPercent") ?
                        instance.persistentData.getFloat("SelectedPercent") : maxStock;

                if (tempSelected > maxStock) tempSelected = maxStock;
                final float selectedStock = tempSelected;

                float multiplier = getPowerMultiplier(instance.count, data);
                float damage = (5.0f + (selectedStock * 0.2f)) * multiplier;

                entity.swingHand(Hand.MAIN_HAND, true);

                Vec3d rotation = entity.getRotationVector();
                double forwardOffset = 2.5;
                double impactX = entity.getX() + (rotation.x * forwardOffset);
                double impactY = entity.getY() + (rotation.y * forwardOffset) + 1.0;
                double impactZ = entity.getZ() + (rotation.z * forwardOffset);

                if (entity.getWorld() instanceof ServerWorld world) {
                    boolean canDestroy = !PlusUltraConfig.get().disableQuirkDestruction;
                    if (data.runtimeTags.containsKey("DESTRUCTION_DISABLED")) canDestroy = false;
                    if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING) && !(entity instanceof PlayerEntity)) canDestroy = false;

                    if (selectedStock >= 75.0f && canDestroy) {
                        BlockPos destCenter = new BlockPos((int)(entity.getX() + (rotation.x * 4.0)), (int)(entity.getY() + (rotation.y * 4.0)), (int)(entity.getZ() + (rotation.z * 4.0)));
                        int radius = 3 + (int)((selectedStock - 75) / 12.5);
                        createDestruction(world, destCenter, radius, entity, selectedStock);
                    }

                    float pitch = 1.0f - (selectedStock / 200.0f);
                    float volume = 1.0f + (selectedStock / 50.0f);
                    world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, volume, pitch);

                    if (selectedStock > 50) {
                        world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, volume * 0.8f, 1.0f);
                    }
                    if (selectedStock > 80) {
                        world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, volume * 0.5f, 0.5f);
                    }

                    int particleCount = (int)(selectedStock / 2);
                    if (selectedStock < 20) {
                        world.spawnParticles(ParticleTypes.POOF, impactX, impactY, impactZ, 10, 0.2, 0.2, 0.2, 0.05);
                    } else if (selectedStock < 70) {
                        world.spawnParticles(ParticleTypes.EXPLOSION, impactX, impactY, impactZ, 5 + (particleCount/5), 1.0, 1.0, 1.0, 0.1);
                        world.spawnParticles(ParticleTypes.CRIT, impactX, impactY, impactZ, 20 + particleCount, 1.5, 1.5, 1.5, 0.5);
                    } else {
                        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, impactX, impactY, impactZ, 2 + (particleCount/20), 2.0, 2.0, 2.0, 0.0);
                        world.spawnParticles(ParticleTypes.SONIC_BOOM, impactX, impactY, impactZ, 1, 0, 0, 0, 0);
                        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, impactX, impactY, impactZ, 50 + particleCount, 3.0, 3.0, 3.0, 0.5);
                        world.spawnParticles(ParticleTypes.FLASH, impactX, impactY, impactZ, 1, 0, 0, 0, 0);
                        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, impactX, impactY, impactZ, 20, 1.0, 1.0, 1.0, 0.1);
                    }
                }

                double radius = 2.5 + (selectedStock / 50.0);
                Box damageBox = new Box(
                        impactX - radius, impactY - radius, impactZ - radius,
                        impactX + radius, impactY + radius, impactZ + radius
                );

                entity.getWorld().getEntitiesByClass(LivingEntity.class, damageBox, e -> e != entity).forEach(e -> {
                    e.damage(entity.getDamageSources().mobAttack(entity), damage);
                    e.takeKnockback(1.0 + (selectedStock / 40.0), entity.getX() - e.getX(), entity.getZ() - e.getZ());
                });

                data.currentStamina -= this.getCost();

                if (entity instanceof PlayerEntity p) {
                    String msg = String.format("§eSmash! (%.0f%%) [%.1f Dmg]", selectedStock, damage);
                    p.sendMessage(Text.of(msg), true);
                }
                this.triggerCooldown(instance);
            }
        });

        this.addAbility(new QuirkSystem.Ability("Leap", QuirkSystem.AbilityType.INSTANT, 40, 1, 15.0) {
            @Override
            public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                return target != null && distanceSquared > 64.0 && user.isOnGround();
            }

            @Override
            public void onAIUse(LivingEntity user, LivingEntity target, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                user.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target.getPos());
                super.onAIUse(user, target, data, instance);
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                float maxStock = instance.persistentData.getFloat("StockpilePercent");
                float tempSelected = instance.persistentData.contains("SelectedPercent") ?
                        instance.persistentData.getFloat("SelectedPercent") : maxStock;

                if (tempSelected > maxStock) tempSelected = maxStock;
                final float selectedStock = tempSelected;

                if (!entity.isOnGround() && selectedStock < 35.0f) {
                    if (entity instanceof PlayerEntity p) {
                        p.sendMessage(Text.of("§cMust use at least 35% to Double Jump!"), true);
                    }
                    return;
                }

                float multiplier = getPowerMultiplier(instance.count, data);
                float force = (0.8f + (selectedStock * 0.03f)) * multiplier;

                Vec3d dir = entity.getRotationVector();
                entity.addVelocity(dir.x * force, (dir.y * force) + 0.5, dir.z * force);
                entity.velocityModified = true;

                data.runtimeTags.put("STOCKPILE_LEAPING", "true");
                data.runtimeTags.put("STOCKPILE_LEAP_Y", String.valueOf(entity.getY()));
                data.runtimeTags.put("STOCKPILE_LEAP_PEAK", String.valueOf(entity.getY()));
                data.runtimeTags.put("STOCKPILE_LEAP_GRACE", "true");

                if (entity.getWorld() instanceof ServerWorld world) {
                    world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5f + (selectedStock / 100f), 2.0f - (selectedStock / 100f));
                    int count = 10 + (int)(selectedStock / 2);
                    world.spawnParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY(), entity.getZ(), count, 0.5, 0.1, 0.5, 0.1);
                    if (selectedStock > 50) {
                        world.spawnParticles(ParticleTypes.EXPLOSION, entity.getX(), entity.getY(), entity.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
                    }
                }

                data.currentStamina -= this.getCost();

                if (entity instanceof PlayerEntity p) {
                    p.sendMessage(Text.of(String.format("§bLeap! (%.0f%%)", selectedStock)), true);
                }
                this.triggerCooldown(instance);
            }
        });

        this.addAbility(new QuirkSystem.Ability("Flick", QuirkSystem.AbilityType.INSTANT, 30, 1, 20.0) {
            @Override
            public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                return target != null && distanceSquared > 25.0 && distanceSquared < 400.0;
            }

            @Override
            public void onAIUse(LivingEntity user, LivingEntity target, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                user.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target.getPos().add(0, target.getHeight() * 0.5, 0));
                super.onAIUse(user, target, data, instance);
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                float maxStock = instance.persistentData.getFloat("StockpilePercent");
                float tempSelected = instance.persistentData.contains("SelectedPercent") ?
                        instance.persistentData.getFloat("SelectedPercent") : maxStock;
                if (tempSelected > maxStock) tempSelected = maxStock;
                final float selectedStock = tempSelected;

                float multiplier = getPowerMultiplier(instance.count, data);
                float damage = (2.0f + (selectedStock * 0.1f)) * multiplier;

                entity.swingHand(Hand.MAIN_HAND, true);

                if (!entity.getWorld().isClient) {
                    QuirkProjectileEntity proj = new QuirkProjectileEntity(entity.getWorld(), entity, selectedStock, damage, 0);
                    proj.setVelocity(entity, entity.getPitch(), entity.getYaw(), 0.0F, 3.0F, 1.0F);
                    entity.getWorld().spawnEntity(proj);

                    float volume = 1.0f + (selectedStock / 50.0f);
                    float pitch = 2.0f - (selectedStock / 100.0f);

                    entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, volume, pitch);

                    if(selectedStock > 40) {
                        entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, volume * 0.5f, 2.0f);
                    }
                    if(selectedStock > 80) {
                        entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, volume * 0.3f, 2.0f);
                    }
                }

                data.currentStamina -= this.getCost();

                if (entity instanceof PlayerEntity p) {
                    p.sendMessage(Text.of("§bFlick!"), true);
                }
                this.triggerCooldown(instance);
            }
        });
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        if (data.runtimeTags.containsKey("STOCKPILE_LEAPING")) {
            entity.fallDistance = 0;
            double currentY = entity.getY();
            double peakY = Double.parseDouble(data.runtimeTags.getOrDefault("STOCKPILE_LEAP_PEAK", String.valueOf(currentY)));

            if (currentY > peakY) {
                data.runtimeTags.put("STOCKPILE_LEAP_PEAK", String.valueOf(currentY));
                peakY = currentY;
            }

            if (entity.isOnGround()) {
                if (data.runtimeTags.containsKey("STOCKPILE_LEAP_GRACE")) {
                    data.runtimeTags.remove("STOCKPILE_LEAP_GRACE");
                }

                if (peakY - currentY > 1.0) {
                    handleLanding(entity, peakY - currentY, data);
                    data.runtimeTags.remove("STOCKPILE_LEAPING");
                    data.runtimeTags.remove("STOCKPILE_LEAP_Y");
                    data.runtimeTags.remove("STOCKPILE_LEAP_PEAK");
                }
            }
        }

        if (entity.getWorld().isClient) return;
        long currentTime = entity.getWorld().getTime();

        if (!instance.persistentData.contains("LastStockpileTime")) {
            instance.persistentData.putLong("LastStockpileTime", currentTime);
            return;
        }

        long lastTime = instance.persistentData.getLong("LastStockpileTime");
        if (currentTime > lastTime) {
            long diff = currentTime - lastTime;
            float current = instance.persistentData.getFloat("StockpilePercent");

            if (current < 100.0f) {
                double increment = (double)diff / 24000.0;
                current += (float)increment;
                if (current > 100.0f) current = 100.0f;
                instance.persistentData.putFloat("StockpilePercent", current);

                if (instance.persistentData.contains("SelectedPercent")) {
                    float sel = instance.persistentData.getFloat("SelectedPercent");
                    if (sel > current) instance.persistentData.putFloat("SelectedPercent", current);
                }
                if ((diff > 20 || entity.age % 100 == 0) && entity instanceof ServerPlayerEntity sp) {
                    PlusUltraNetwork.sync(sp);
                }
            }
        }
        instance.persistentData.putLong("LastStockpileTime", currentTime);
    }

    private void createDestruction(World world, BlockPos center, int radius, LivingEntity entity, float power) {
        if (radius > 0 && world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        double distance = Math.sqrt(x*x + y*y + z*z);
                        double noise = world.random.nextDouble() * 2.0;
                        if (distance <= (radius - noise)) {
                            BlockPos p = center.add(x, y, z);
                            if (!world.isAir(p)) {
                                BlockState state = world.getBlockState(p);
                                float hardness = state.getHardness(world, p);
                                if (hardness >= 0) {
                                    float breakChance = 0.0f;
                                    if (power >= hardness * 2.0f) {
                                        if (hardness > 0) {
                                            breakChance = (power / (hardness * 10.0f));
                                        } else {
                                            breakChance = 1.0f;
                                        }
                                    }
                                    if (hardness < 0.5f) breakChance = 1.0f;
                                    if (world.random.nextFloat() < breakChance) {
                                        boolean shouldDrop = world.random.nextFloat() < 0.3f;
                                        world.breakBlock(p, shouldDrop, entity);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void handleLanding(LivingEntity entity, double fallHeight, QuirkSystem.QuirkData data) {
        if (!(entity.getWorld() instanceof ServerWorld world)) return;
        boolean canDestroy = !PlusUltraConfig.get().disableQuirkDestruction;
        if (data.runtimeTags.containsKey("DESTRUCTION_DISABLED")) canDestroy = false;
        if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) canDestroy = false;

        if (fallHeight > 4.0 && canDestroy) {
            int radius = (int) (fallHeight / 5.0);
            if (radius > 6) radius = 6;
            float impactPower = (float)fallHeight * 2.0f;
            if (impactPower > 100f) impactPower = 100f;
            createDestruction(world, entity.getBlockPos().down(), radius, entity, impactPower);
        }

        world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.spawnParticles(ParticleTypes.EXPLOSION, entity.getX(), entity.getY(), entity.getZ(), 3, 1.0, 0.1, 1.0, 0.0);

        if (entity instanceof PlayerEntity p) {
            p.sendMessage(Text.of("§7Landed from " + String.format("%.1f", fallHeight) + "m"), true);
        }
    }
}