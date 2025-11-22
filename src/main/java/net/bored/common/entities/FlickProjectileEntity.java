package net.bored.common.entities;

import net.bored.PlusUltra;
import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

public class FlickProjectileEntity extends PersistentProjectileEntity {

    // FIX 1: Use TrackedData so the Client knows the power level for VFX
    private static final TrackedData<Float> POWER = DataTracker.registerData(FlickProjectileEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private float damage = 0f;

    public FlickProjectileEntity(EntityType<? extends PersistentProjectileEntity> entityType, World world) {
        super(entityType, world);
    }

    public FlickProjectileEntity(World world, LivingEntity owner, float power, float dmg) {
        super(PlusUltra.FLICK_PROJECTILE, owner, world);
        this.dataTracker.set(POWER, power); // Sync power
        this.damage = dmg;
        this.setNoGravity(true);
        this.setDamage(dmg);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(POWER, 0f);
    }

    @Override
    protected ItemStack asItemStack() {
        return ItemStack.EMPTY;
    }

    @Override
    public void tick() {
        super.tick();

        // Get synced power
        float powerPercent = this.dataTracker.get(POWER);

        if (this.getWorld().isClient) {
            // --- INCREASED VFX ---
            int cloudCount = 2;
            if (powerPercent > 30) cloudCount = 5;
            if (powerPercent > 70) cloudCount = 10;

            // Main Wind Trail (Cloud + White Smoke)
            for(int i=0; i<cloudCount; i++) {
                this.getWorld().addParticle(ParticleTypes.CLOUD,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.5,
                        this.getY() + (this.random.nextDouble() - 0.5) * 0.5,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.5,
                        0, 0, 0);
            }

            // High Power effects
            if (powerPercent >= 50) {
                // Sweep Attack particles for "cutting" air look
                if (this.age % 2 == 0) {
                    this.getWorld().addParticle(ParticleTypes.SWEEP_ATTACK, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
                }
                // Flash occasionally
                if (this.random.nextFloat() < 0.1f) {
                    this.getWorld().addParticle(ParticleTypes.FLASH, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
                }
            }

            if (powerPercent > 80) {
                // Intense Sparks / Magic
                for (int i = 0; i < 3; i++) {
                    this.getWorld().addParticle(ParticleTypes.ELECTRIC_SPARK,
                            this.getX() + (this.random.nextDouble() - 0.5),
                            this.getY() + (this.random.nextDouble() - 0.5),
                            this.getZ() + (this.random.nextDouble() - 0.5),
                            0, 0, 0);
                }
                // Sonic Boom Ring every few ticks
                if (this.age % 4 == 0) {
                    this.getWorld().addParticle(ParticleTypes.SONIC_BOOM, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
                }
            }
        } else {
            if (powerPercent >= 50) {
                Entity owner = this.getOwner();
                boolean destructionEnabled = true;

                if (owner instanceof LivingEntity livingOwner) {
                    QuirkSystem.QuirkData data = ((IQuirkDataAccessor)livingOwner).getQuirkData();
                    if (data.runtimeTags.containsKey("DESTRUCTION_DISABLED")) {
                        destructionEnabled = false;
                    }
                }

                if (destructionEnabled && this.age % 2 == 0) {
                    int rad = (powerPercent > 80) ? 3 : 2;
                    createDestruction((ServerWorld) this.getWorld(), this.getBlockPos(), rad);
                }
            }

            if (this.age > 40) {
                this.discard();
            }
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);
        if (!this.getWorld().isClient) {
            float powerPercent = this.dataTracker.get(POWER);
            Entity target = entityHitResult.getEntity();
            target.damage(this.getDamageSources().mobProjectile(this, (LivingEntity)this.getOwner()), (float)this.getDamage());

            double knockback = 0.5 + (powerPercent / 50.0);
            target.addVelocity(this.getVelocity().x * knockback, 0.2, this.getVelocity().z * knockback);

            float volume = 1.0f + (powerPercent / 50.0f);
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.NEUTRAL, volume, 2.0f);

            if (powerPercent > 50) {
                this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.NEUTRAL, volume * 0.8f, 1.5f);
            }

            if (powerPercent > 80) {
                this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.NEUTRAL, volume * 0.5f, 2.0f);
            }

            this.discard();
        }
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        if (!this.getWorld().isClient) {
            float powerPercent = this.dataTracker.get(POWER);
            float volume = 1.0f + (powerPercent / 50.0f);

            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BLOCK_STONE_HIT, SoundCategory.NEUTRAL, volume, 0.5f);

            if (powerPercent > 60) {
                this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.NEUTRAL, volume * 0.5f, 1.2f);
            }

            this.discard();
        }
    }

    private void createDestruction(ServerWorld world, BlockPos center, int radius) {
        if (world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
            float powerPercent = this.dataTracker.get(POWER);

            // FIX 2: Jagged/Rough Sphere Logic
            // We iterate a slightly larger box to allow for noise variance
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {

                        double distance = Math.sqrt(x*x + y*y + z*z);

                        // Noise factor: Subtract a random amount between 0.0 and 1.5 from the radius check
                        // effectively making the edge "fuzzy"
                        double noise = world.random.nextDouble() * 1.5;

                        if (distance <= (radius - noise)) {
                            BlockPos p = center.add(x, y, z);
                            if (!world.isAir(p)) {
                                BlockState state = world.getBlockState(p);
                                float hardness = state.getHardness(world, p);

                                if (hardness >= 0) {
                                    float breakChance = 0.0f;

                                    // Hardness Threshold Logic
                                    if (powerPercent >= hardness * 2.0f) {
                                        if (hardness > 0) {
                                            breakChance = (powerPercent / (hardness * 10.0f));
                                        } else {
                                            breakChance = 1.0f;
                                        }
                                    }
                                    if (hardness < 0.5f) breakChance = 1.0f;

                                    if (world.random.nextFloat() < breakChance) {
                                        boolean shouldDrop = world.random.nextFloat() < 0.3f;
                                        world.breakBlock(p, shouldDrop, this.getOwner());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}