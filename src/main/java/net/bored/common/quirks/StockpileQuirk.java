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
                // Updated: Start slightly higher to avoid destroying ground under feet when looking straight
                double startY = entity.getY() + entity.getEyeHeight(entity.getPose());

                // Travel Logic: Create effects along the vector
                int steps = 4;
                double stepSize = 1.5;

                if (entity.getWorld() instanceof ServerWorld world) {
                    boolean canDestroy = !PlusUltraConfig.get().disableQuirkDestruction;
                    if (data.runtimeTags.containsKey("DESTRUCTION_DISABLED")) canDestroy = false;
                    if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING) && !(entity instanceof PlayerEntity)) canDestroy = false;

                    for(int i = 1; i <= steps; i++) {
                        double dist = i * stepSize;
                        double px = entity.getX() + (rotation.x * dist);
                        double py = startY + (rotation.y * dist); // Uses Eye height start
                        double pz = entity.getZ() + (rotation.z * dist);

                        // Particles traveling forward
                        world.spawnParticles(ParticleTypes.EXPLOSION, px, py, pz, 2, 0.5, 0.5, 0.5, 0.05);
                        world.spawnParticles(ParticleTypes.POOF, px, py, pz, 5, 0.2, 0.2, 0.2, 0.1);

                        // Destruction at the end or along path if strong enough
                        if (selectedStock >= 75.0f && canDestroy && i == steps) {
                            BlockPos destCenter = new BlockPos((int)px, (int)py, (int)pz);
                            int radius = 3 + (int)((selectedStock - 75) / 12.5);
                            createDestruction(world, destCenter, radius, entity, selectedStock);
                        }

                        // Damage Box moving forward
                        double radius = 1.5 + (selectedStock / 60.0);
                        Box damageBox = new Box(px - radius, py - radius, pz - radius, px + radius, py + radius, pz + radius);
                        world.getEntitiesByClass(LivingEntity.class, damageBox, e -> e != entity).forEach(e -> {
                            e.damage(entity.getDamageSources().mobAttack(entity), damage);
                            e.takeKnockback(1.0 + (selectedStock / 40.0), entity.getX() - e.getX(), entity.getZ() - e.getZ());
                        });
                    }

                    float pitch = 1.0f - (selectedStock / 200.0f);
                    float volume = 1.0f + (selectedStock / 50.0f);
                    world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, volume, pitch);

                    if (selectedStock > 50) {
                        world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, volume * 0.8f, 1.0f);
                    }
                }

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
                // Store power for landing
                data.runtimeTags.put("STOCKPILE_LEAP_POWER", String.valueOf(selectedStock));

                if (entity.getWorld() instanceof ServerWorld world) {
                    // Launch Sound
                    world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5f + (selectedStock / 100f), 2.0f - (selectedStock / 100f));

                    // Launch Particles
                    int count = 10 + (int)(selectedStock / 2);
                    world.spawnParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY(), entity.getZ(), count, 0.5, 0.1, 0.5, 0.1);
                    if (selectedStock > 50) {
                        world.spawnParticles(ParticleTypes.EXPLOSION, entity.getX(), entity.getY(), entity.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
                    }

                    // 1. Launch Damage (AoE)
                    if (selectedStock > 20) {
                        double dmgRadius = 2.0 + (selectedStock / 40.0);
                        float launchDmg = 3.0f + (selectedStock * 0.1f);
                        Box box = entity.getBoundingBox().expand(dmgRadius);
                        world.getEntitiesByClass(LivingEntity.class, box, e -> e != entity).forEach(e -> {
                            e.damage(entity.getDamageSources().explosion(entity, null), launchDmg);
                            e.takeKnockback(1.0, entity.getX() - e.getX(), entity.getZ() - e.getZ());
                        });
                    }

                    // 2. Launch Destruction
                    boolean canDestroy = !PlusUltraConfig.get().disableQuirkDestruction;
                    if (data.runtimeTags.containsKey("DESTRUCTION_DISABLED")) canDestroy = false;
                    if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING) && !(entity instanceof PlayerEntity)) canDestroy = false;

                    if (canDestroy && selectedStock > 60) {
                        int rad = (int)(selectedStock / 30.0);
                        createDestruction(world, entity.getBlockPos().down(), rad, entity, selectedStock);
                    }
                }

                data.currentStamina -= this.getCost();

                if (entity instanceof PlayerEntity p) {
                    p.sendMessage(Text.of(String.format("§bLeap! (%.0f%%)", selectedStock)), true);
                }
                this.triggerCooldown(instance);
            }
        });

        // Updated Flick: Now HOLD type with Continuous Fire
        this.addAbility(new QuirkSystem.Ability("Flick", QuirkSystem.AbilityType.HOLD, 20, 1, 2.0) {
            @Override
            public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                return target != null && distanceSquared > 25.0 && distanceSquared < 400.0;
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                data.runtimeTags.put("FLICK_HOLD_TICKS", "0");
                if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§bRapid Flick!"), true);
            }

            @Override
            public void onHoldTick(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (entity.getWorld().isClient) return;

                float maxStock = instance.persistentData.getFloat("StockpilePercent");
                float tempSelected = instance.persistentData.contains("SelectedPercent") ?
                        instance.persistentData.getFloat("SelectedPercent") : maxStock;
                if (tempSelected > maxStock) tempSelected = maxStock;

                // Weaker flicks for rapid fire (50% power)
                float effectiveStock = tempSelected * 0.5f;

                int heldTicks = Integer.parseInt(data.runtimeTags.getOrDefault("FLICK_HOLD_TICKS", "0"));

                // Fire every 5 ticks (4 shots per second)
                if (heldTicks % 5 == 0) {
                    if (data.currentStamina < 2.0) {
                        // Force stop if out of stamina
                        onRelease(entity, data, instance);
                        return;
                    }
                    data.currentStamina -= 2.0; // Reduced cost per shot

                    float multiplier = getPowerMultiplier(instance.count, data);
                    float damage = (1.5f + (effectiveStock * 0.05f)) * multiplier;

                    entity.swingHand(Hand.MAIN_HAND, true);

                    QuirkProjectileEntity proj = new QuirkProjectileEntity(entity.getWorld(), entity, effectiveStock, damage, 0);
                    proj.setVelocity(entity, entity.getPitch(), entity.getYaw(), 0.0F, 3.0F, 1.0F);
                    entity.getWorld().spawnEntity(proj);

                    // Explosion Sound & Particles at hand
                    float volume = 0.5f + (effectiveStock / 100.0f);
                    entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, volume, 2.0f);

                    if (entity.getWorld() instanceof ServerWorld sw) {
                        Vec3d look = entity.getRotationVector().multiply(1.5);
                        sw.spawnParticles(ParticleTypes.EXPLOSION, entity.getX() + look.x, entity.getY() + entity.getEyeHeight(entity.getPose()) + look.y, entity.getZ() + look.z, 1, 0, 0, 0, 0);
                    }
                }

                data.runtimeTags.put("FLICK_HOLD_TICKS", String.valueOf(heldTicks + 1));
            }

            @Override
            public void onRelease(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                int heldTicks = Integer.parseInt(data.runtimeTags.getOrDefault("FLICK_HOLD_TICKS", "0"));
                data.runtimeTags.remove("FLICK_HOLD_TICKS");

                // Cooldown scales with how long you held it (or how many shots)
                // Max cooldown 5 seconds
                int cooldown = Math.min(100, heldTicks * 2);
                instance.cooldowns.put("Flick", cooldown);
            }
        });
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        // Ensure ability onHoldTick logic is called
        for (QuirkSystem.Ability ability : this.getAbilities()) {
            ability.onHoldTick(entity, data, instance);
        }

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
                    data.runtimeTags.remove("STOCKPILE_LEAP_POWER");
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

        // Retrieve power stored during launch
        float power = Float.parseFloat(data.runtimeTags.getOrDefault("STOCKPILE_LEAP_POWER", "0"));

        if (fallHeight > 4.0) {
            int radius = (int) (fallHeight / 5.0);
            if (radius > 6) radius = 6;
            float impactPower = (float)fallHeight * 2.0f;
            if (impactPower > 100f) impactPower = 100f;

            // 1. Block Destruction
            if (canDestroy) {
                createDestruction(world, entity.getBlockPos().down(), radius, entity, impactPower);
            }

            // 2. Entity Damage on Landing
            double damageRadius = radius + 2.0;
            float landingDmg = (float) (fallHeight / 2.0) + (power / 20.0f);
            Box box = entity.getBoundingBox().expand(damageRadius);
            world.getEntitiesByClass(LivingEntity.class, box, e -> e != entity).forEach(e -> {
                e.damage(entity.getDamageSources().explosion(entity, null), landingDmg);
                e.takeKnockback(1.5, entity.getX() - e.getX(), entity.getZ() - e.getZ());
            });
        }

        world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.spawnParticles(ParticleTypes.EXPLOSION, entity.getX(), entity.getY(), entity.getZ(), 3, 1.0, 0.1, 1.0, 0.0);

        if (entity instanceof PlayerEntity p) {
            p.sendMessage(Text.of("§7Landed from " + String.format("%.1f", fallHeight) + "m"), true);
        }
    }
}