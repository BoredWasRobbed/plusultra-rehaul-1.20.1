package net.bored.common.entities;

import net.bored.PlusUltra;
import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
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

    private float powerPercent = 0f;
    private float damage = 0f;

    public FlickProjectileEntity(EntityType<? extends PersistentProjectileEntity> entityType, World world) {
        super(entityType, world);
    }

    public FlickProjectileEntity(World world, LivingEntity owner, float power, float dmg) {
        super(PlusUltra.FLICK_PROJECTILE, owner, world);
        this.powerPercent = power;
        this.damage = dmg;
        this.setNoGravity(true);
        this.setDamage(dmg);
    }

    @Override
    protected ItemStack asItemStack() {
        return ItemStack.EMPTY;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) {
            // SCALING PARTICLES
            int cloudCount = 2;
            if (powerPercent > 30) cloudCount = 5;
            if (powerPercent > 70) cloudCount = 12;

            for(int i=0; i<cloudCount; i++) {
                this.getWorld().addParticle(ParticleTypes.CLOUD,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.3,
                        this.getY() + (this.random.nextDouble() - 0.5) * 0.3,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.3,
                        0, 0, 0);
            }

            if (powerPercent >= 50) {
                this.getWorld().addParticle(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
                this.getWorld().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
            }

            if (powerPercent > 80) {
                this.getWorld().addParticle(ParticleTypes.ELECTRIC_SPARK,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.5,
                        this.getY() + (this.random.nextDouble() - 0.5) * 0.5,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.5,
                        0, 0, 0);
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
                    // Radius scales: 2 at 50%, 3 at 80%+
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
            Entity target = entityHitResult.getEntity();
            target.damage(this.getDamageSources().mobProjectile(this, (LivingEntity)this.getOwner()), (float)this.getDamage());

            double knockback = 0.5 + (powerPercent / 50.0);
            target.addVelocity(this.getVelocity().x * knockback, 0.2, this.getVelocity().z * knockback);

            // IMPACT SOUNDS
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
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (x*x + y*y + z*z <= radius*radius) {
                            BlockPos p = center.add(x, y, z);
                            if (!world.isAir(p)) {
                                BlockState state = world.getBlockState(p);
                                float hardness = state.getHardness(world, p);

                                if (hardness >= 0) {
                                    float breakChance = 1.0f;
                                    if (hardness > 0) {
                                        // Use stored powerPercent for calculation
                                        breakChance = (this.powerPercent / (hardness * 15.0f));
                                    }
                                    if (hardness < 0.5f) breakChance = 1.0f;

                                    if (world.random.nextFloat() < breakChance) {
                                        world.breakBlock(p, true, this.getOwner());
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