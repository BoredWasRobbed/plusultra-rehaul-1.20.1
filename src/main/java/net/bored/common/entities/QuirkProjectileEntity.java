package net.bored.common.entities;

import net.bored.PlusUltra;
import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.bored.config.PlusUltraConfig;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

public class QuirkProjectileEntity extends PersistentProjectileEntity {

    // 0 = Flick, 1 = Warp Shot, 2 = Portal, 3 = Marble
    private static final TrackedData<Integer> TYPE = DataTracker.registerData(QuirkProjectileEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> POWER = DataTracker.registerData(QuirkProjectileEntity.class, TrackedDataHandlerRegistry.FLOAT);

    // Destination for Warp types
    private static final TrackedData<Float> DEST_X = DataTracker.registerData(QuirkProjectileEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> DEST_Y = DataTracker.registerData(QuirkProjectileEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> DEST_Z = DataTracker.registerData(QuirkProjectileEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> HAS_DEST = DataTracker.registerData(QuirkProjectileEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private float damage = 0f;

    public QuirkProjectileEntity(EntityType<? extends PersistentProjectileEntity> entityType, World world) {
        super(entityType, world);
    }

    public QuirkProjectileEntity(World world, LivingEntity owner, float power, float dmg, int type) {
        super(PlusUltra.QUIRK_PROJECTILE, owner, world);
        this.dataTracker.set(POWER, power);
        this.dataTracker.set(TYPE, type);
        this.damage = dmg;

        if (type == 2) { // Portal
            this.setNoGravity(true);
            this.setVelocity(0, 0, 0);
        } else if (type == 3) { // Marble
            this.setNoGravity(false);
        } else if (type == 1) { // Warp Shot
            this.setNoGravity(true);
        } else { // Flick
            this.setNoGravity(true);
        }
        this.setDamage(dmg);
    }

    public void setDestination(Vec3d pos) {
        this.dataTracker.set(DEST_X, (float)pos.x);
        this.dataTracker.set(DEST_Y, (float)pos.y);
        this.dataTracker.set(DEST_Z, (float)pos.z);
        this.dataTracker.set(HAS_DEST, true);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(TYPE, 0);
        this.dataTracker.startTracking(POWER, 0f);
        this.dataTracker.startTracking(DEST_X, 0f);
        this.dataTracker.startTracking(DEST_Y, 0f);
        this.dataTracker.startTracking(DEST_Z, 0f);
        this.dataTracker.startTracking(HAS_DEST, false);
    }

    @Override
    protected ItemStack asItemStack() {
        // Render as Heart of the Sea for Marble Type
        if (this.dataTracker.get(TYPE) == 3) {
            return new ItemStack(Items.HEART_OF_THE_SEA);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void tick() {
        super.tick();

        float powerPercent = this.dataTracker.get(POWER);
        int type = this.dataTracker.get(TYPE);

        // --- TYPE 3: MARBLE ---
        if (type == 3) {
            // Just particles, actual logic handled by Quirk (Pressing ability again)
            if (this.getWorld().isClient) {
                this.getWorld().addParticle(ParticleTypes.BUBBLE, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
            }
            return;
        }

        // --- TYPE 2: PORTAL ---
        if (type == 2) {
            this.setVelocity(0,0,0);

            // Standard portals last 10s, but "Held" portals (reset by ability) last longer
            if (this.age > 200) this.discard();

            if (this.getWorld().isClient) {
                // TALLER PORTAL VFX: Column roughly 2 blocks high
                for(int i=0; i<5; i++) {
                    // Offset from -0.5 (feet) to +1.5 (above head)
                    double heightOffset = (random.nextDouble() * 2.0) - 0.5;

                    this.getWorld().addParticle(ParticleTypes.PORTAL,
                            this.getX() + (random.nextDouble()-0.5),
                            this.getY() + heightOffset,
                            this.getZ() + (random.nextDouble()-0.5),
                            (random.nextDouble()-0.5)*0.5, (random.nextDouble()-0.5)*0.5, (random.nextDouble()-0.5)*0.5);

                    if (random.nextBoolean()) {
                        this.getWorld().addParticle(ParticleTypes.SQUID_INK,
                                this.getX() + (random.nextDouble()-0.5)*0.5,
                                this.getY() + heightOffset,
                                this.getZ() + (random.nextDouble()-0.5)*0.5,
                                0, 0.05, 0);
                    }
                }
            } else {
                if (this.dataTracker.get(HAS_DEST)) {
                    Box box = this.getBoundingBox().expand(0.2, 1.0, 0.2);
                    this.getWorld().getOtherEntities(this, box).forEach(this::teleportEntity);
                }
            }
            return;
        }

        // --- PROJECTILE LOGIC (Types 0 & 1) ---
        if (this.getWorld().isClient) {
            spawnParticles(type, powerPercent);
        } else {
            if (type == 0 && powerPercent >= 50) {
                handleDestruction(powerPercent);
            }
            if (this.age > 60) this.discard();
        }
    }

    private void spawnParticles(int type, float power) {
        if (type == 1) { // Warp Shot
            this.getWorld().addParticle(ParticleTypes.PORTAL, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
            this.getWorld().addParticle(ParticleTypes.SQUID_INK, this.getX(), this.getY(), this.getZ(), 0, 0.05, 0);
        } else { // Flick
            int cloudCount = (power > 70) ? 10 : (power > 30 ? 5 : 2);
            for(int i=0; i<cloudCount; i++) {
                this.getWorld().addParticle(ParticleTypes.CLOUD,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.5,
                        this.getY() + (this.random.nextDouble() - 0.5) * 0.5,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
            }
            if (power >= 50 && this.age % 2 == 0) this.getWorld().addParticle(ParticleTypes.SWEEP_ATTACK, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
            if (power > 80 && this.age % 4 == 0) this.getWorld().addParticle(ParticleTypes.SONIC_BOOM, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
        }
    }

    private void handleDestruction(float powerPercent) {
        Entity owner = this.getOwner();
        boolean destructionEnabled = !PlusUltraConfig.get().disableQuirkDestruction;
        if (owner instanceof LivingEntity livingOwner) {
            QuirkSystem.QuirkData data = ((IQuirkDataAccessor)livingOwner).getQuirkData();
            if (data.runtimeTags.containsKey("DESTRUCTION_DISABLED")) destructionEnabled = false;
        }

        if (destructionEnabled && this.age % 2 == 0) {
            int rad = (powerPercent > 80) ? 3 : 2;
            createDestruction((ServerWorld) this.getWorld(), this.getBlockPos(), rad, powerPercent);
        }
    }

    private void teleportEntity(Entity target) {
        if (!this.dataTracker.get(HAS_DEST)) return;

        if (target instanceof LivingEntity living && target instanceof IQuirkDataAccessor accessor) {
            QuirkSystem.QuirkData data = accessor.getQuirkData();
            long currentTime = target.getWorld().getTime();
            long cooldownUntil = 0;

            if (data.runtimeTags.containsKey("WARP_COOLDOWN")) {
                cooldownUntil = Long.parseLong(data.runtimeTags.get("WARP_COOLDOWN"));
            }

            if (currentTime < cooldownUntil) return;

            data.runtimeTags.put("WARP_COOLDOWN", String.valueOf(currentTime + 40));
        } else {
            if (target.age % 20 != 0) return;
        }

        double tx = this.dataTracker.get(DEST_X);
        double ty = this.dataTracker.get(DEST_Y);
        double tz = this.dataTracker.get(DEST_Z);

        target.teleport(tx, ty, tz);
        this.getWorld().playSound(null, target.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);
        if (this.getWorld().isClient) return;

        int type = this.dataTracker.get(TYPE);
        Entity target = entityHitResult.getEntity();

        if (type == 3) { // Marble Hit
            // Bounce off, don't do damage, just exist until second press
            this.setVelocity(this.getVelocity().multiply(-0.5, -0.5, -0.5));
            return;
        }

        if (type == 1) { // WARP SHOT
            teleportEntity(target);
            // No impact sound here, teleportEntity handles the TP sound.
            this.discard();
        } else if (type == 0) { // FLICK
            float powerPercent = this.dataTracker.get(POWER);
            target.damage(this.getDamageSources().mobProjectile(this, (LivingEntity)this.getOwner()), (float)this.getDamage());
            double knockback = 0.5 + (powerPercent / 50.0);
            target.addVelocity(this.getVelocity().x * knockback, 0.2, this.getVelocity().z * knockback);

            float volume = 1.0f + (powerPercent / 50.0f);
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.NEUTRAL, volume, 2.0f);
            this.discard();
        }
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        // Do NOT call super.onBlockHit() for Warp Shot or Portals to prevent arrow sounds
        if (this.getWorld().isClient) return;

        int type = this.dataTracker.get(TYPE);

        if (type == 2) return; // Portals ignore block hits

        if (type == 3) { // Marble Hit
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_GLASS_HIT, SoundCategory.NEUTRAL, 1.0f, 1.0f);
            // Stick or Bounce logic could go here, for now standard physics applies
            super.onBlockHit(blockHitResult);
            return;
        }

        if (type == 1) { // Warp Shot
            // Play subtle magic sound instead of arrow hit
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.NEUTRAL, 1.0f, 2.0f);
            this.discard();
            return;
        }

        if (type == 0) { // Flick sound
            super.onBlockHit(blockHitResult); // Flick can use default physics/sound if desired, but custom logic below overrides it anyway
            float powerPercent = this.dataTracker.get(POWER);
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_STONE_HIT, SoundCategory.NEUTRAL, 1.0f + (powerPercent/50f), 0.5f);
            this.discard();
        }
    }

    private void createDestruction(ServerWorld world, BlockPos center, int radius, float powerPercent) {
        if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) return;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double distance = Math.sqrt(x*x + y*y + z*z);
                    double noise = world.random.nextDouble() * 1.5;
                    if (distance <= (radius - noise)) {
                        BlockPos p = center.add(x, y, z);
                        if (!world.isAir(p)) {
                            BlockState state = world.getBlockState(p);
                            float hardness = state.getHardness(world, p);
                            if (hardness >= 0) {
                                float breakChance = 0.0f;
                                if (powerPercent >= hardness * 2.0f) {
                                    breakChance = (hardness > 0) ? (powerPercent / (hardness * 10.0f)) : 1.0f;
                                }
                                if (hardness < 0.5f) breakChance = 1.0f;
                                if (world.random.nextFloat() < breakChance) {
                                    world.breakBlock(p, world.random.nextFloat() < 0.3f, this.getOwner());
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