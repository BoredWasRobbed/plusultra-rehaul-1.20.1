package net.bored.common.quirks;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork;
import net.bored.common.entities.QuirkProjectileEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

public class WarpGateQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "warp_gate");

    public WarpGateQuirk() { super(ID); }

    @Override
    public int getIconColor() { return 0xFF5D00FF; } // Deep Purple

    @Override
    public void registerAbilities() {
        // Ability 1: Gate Anchor
        this.addAbility(new QuirkSystem.Ability("Gate Anchor", QuirkSystem.AbilityType.HOLD, 40, 1, 5.0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (entity.isSneaking()) {
                    // Set Anchor Logic
                    NbtList anchors = instance.persistentData.getList("Anchors", NbtElement.COMPOUND_TYPE);
                    NbtCompound newAnchor = new NbtCompound();
                    newAnchor.putDouble("X", entity.getX());
                    newAnchor.putDouble("Y", entity.getY());
                    newAnchor.putDouble("Z", entity.getZ());

                    // Name Logic: Check for Name Tag
                    String name = "Anchor " + (anchors.size() + 1);
                    ItemStack handStack = entity.getStackInHand(Hand.OFF_HAND);
                    if (handStack.getItem() == Items.NAME_TAG && handStack.hasCustomName()) {
                        name = handStack.getName().getString();
                    } else {
                        handStack = entity.getMainHandStack(); // Check main hand if offhand empty/invalid
                        if (handStack.getItem() == Items.NAME_TAG && handStack.hasCustomName()) {
                            name = handStack.getName().getString();
                        }
                    }

                    newAnchor.putString("Name", name);
                    anchors.add(newAnchor);
                    instance.persistentData.put("Anchors", anchors);
                    instance.persistentData.putInt("SelectedAnchor", anchors.size() - 1);

                    if (entity instanceof PlayerEntity p) {
                        p.sendMessage(Text.of("§5Anchor Set: " + name), true);
                        if (name.startsWith("Anchor ")) {
                            p.sendMessage(Text.of("§7(Tip: Hold a renamed Name Tag to name your anchor!)"), false);
                        }
                    }

                    entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    this.triggerCooldown(instance);
                } else {
                    // Activate Portal Mode
                    Vec3d targetPos = getSelectedAnchorPos(instance);
                    if (targetPos == null) {
                        if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cNo Anchor Selected! Shift+Use to set."), true);
                        return;
                    }

                    data.runtimeTags.put("WARP_ANCHOR_ACTIVE", "true");

                    // Store the "Front" position relative to player when they started holding
                    Vec3d look = entity.getRotationVector().multiply(2.0);
                    Vec3d startPos = new Vec3d(entity.getX() + look.x, entity.getY(), entity.getZ() + look.z);
                    data.runtimeTags.put("WARP_ANCHOR_START_X", String.valueOf(startPos.x));
                    data.runtimeTags.put("WARP_ANCHOR_START_Y", String.valueOf(startPos.y));
                    data.runtimeTags.put("WARP_ANCHOR_START_Z", String.valueOf(startPos.z));

                    entity.swingHand(Hand.MAIN_HAND, true);
                    entity.getWorld().playSound(null, startPos.x, startPos.y, startPos.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
            }

            @Override
            public void onHoldTick(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (!data.runtimeTags.containsKey("WARP_ANCHOR_ACTIVE")) return;

                if (data.currentStamina < 0.5) {
                    onRelease(entity, data, instance);
                    return;
                }
                data.currentStamina -= 0.5;
            }

            @Override
            public void onRelease(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (data.runtimeTags.containsKey("WARP_ANCHOR_ACTIVE")) {
                    data.runtimeTags.remove("WARP_ANCHOR_ACTIVE");
                    this.triggerCooldown(instance);
                }
            }
        });

        // Ability 2: Warp Mist
        this.addAbility(new QuirkSystem.Ability("Warp Mist", QuirkSystem.AbilityType.HOLD, 60, 10, 15.0) {
            @Override
            public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                // AI: Blink to target if they are far away
                return target != null && distanceSquared > 100.0;
            }

            @Override
            public void onAIUse(LivingEntity user, LivingEntity target, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                // Face target, Start, then immediately Release to simulate instant cast
                user.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target.getPos());
                this.onActivate(user, data, instance);
                this.onRelease(user, data, instance);
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                data.runtimeTags.put("WARP_MIST_ACTIVE", "true");
            }

            @Override
            public void onHoldTick(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (!data.runtimeTags.containsKey("WARP_MIST_ACTIVE")) return;

                if (entity.getWorld().isClient) {
                    float metaMult = getPowerMultiplier(instance.count, data);
                    double range = 20.0 + (metaMult * 5.0);
                    HitResult hit = entity.raycast(range, 0, false);
                    if (hit.getType() != HitResult.Type.MISS) {
                        entity.getWorld().addParticle(ParticleTypes.PORTAL, hit.getPos().x, hit.getPos().y, hit.getPos().z, 0, 0.5, 0);
                        entity.getWorld().addParticle(ParticleTypes.SQUID_INK, hit.getPos().x, hit.getPos().y + 1, hit.getPos().z, 0, 0.05, 0);
                    }
                }
            }

            @Override
            public void onRelease(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                if (!data.runtimeTags.containsKey("WARP_MIST_ACTIVE")) return;
                data.runtimeTags.remove("WARP_MIST_ACTIVE");

                data.currentStamina -= this.getCost();
                float metaMult = getPowerMultiplier(instance.count, data);
                double range = 20.0 + (metaMult * 5.0);

                HitResult hit = entity.raycast(range, 0, false);
                Vec3d dest;
                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult bHit = (BlockHitResult)hit;
                    dest = Vec3d.ofBottomCenter(bHit.getBlockPos().offset(bHit.getSide()));
                } else {
                    dest = hit.getPos();
                }

                if (!entity.getWorld().isClient) {
                    ServerWorld serverWorld = (ServerWorld) entity.getWorld();
                    serverWorld.spawnParticles(ParticleTypes.SQUID_INK, entity.getX(), entity.getY() + 1, entity.getZ(), 20, 0.5, 1.0, 0.5, 0.1);
                    serverWorld.spawnParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + 1, entity.getZ(), 30, 0.5, 1.0, 0.5, 0.5);
                }
                entity.getWorld().playSound(null, entity.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);

                entity.teleport(dest.x, dest.y, dest.z);

                if (!entity.getWorld().isClient) {
                    ServerWorld serverWorld = (ServerWorld) entity.getWorld();
                    serverWorld.spawnParticles(ParticleTypes.SQUID_INK, dest.x, dest.y + 1, dest.z, 20, 0.5, 1.0, 0.5, 0.1);
                    serverWorld.spawnParticles(ParticleTypes.PORTAL, dest.x, dest.y + 1, dest.z, 30, 0.5, 1.0, 0.5, 0.5);
                }
                entity.getWorld().playSound(null, entity.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);

                this.triggerCooldown(instance);
            }
        });

        // Ability 3: Warp Shot
        this.addAbility(new QuirkSystem.Ability("Warp Shot", QuirkSystem.AbilityType.INSTANT, 50, 20, 20.0) {
            @Override
            public boolean shouldAIUse(LivingEntity user, LivingEntity target, double distanceSquared, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                // Ensure anchor exists before AI tries to use it
                return target != null && distanceSquared > 64.0 && getSelectedAnchorPos(instance) != null;
            }

            @Override
            public void onAIUse(LivingEntity user, LivingEntity target, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                user.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target.getPos());
                super.onAIUse(user, target, data, instance);
            }

            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                Vec3d targetPos = getSelectedAnchorPos(instance);
                if (targetPos == null) {
                    if (entity instanceof PlayerEntity p) p.sendMessage(Text.of("§cNo Anchor Selected!"), true);
                    return;
                }

                data.currentStamina -= this.getCost();
                entity.swingHand(Hand.MAIN_HAND, true);

                if (!entity.getWorld().isClient) {
                    QuirkProjectileEntity proj = new QuirkProjectileEntity(entity.getWorld(), entity, 0, 0, 1); // Type 1
                    proj.setDestination(targetPos);
                    proj.setVelocity(entity, entity.getPitch(), entity.getYaw(), 0.0F, 3.0F, 1.0F);
                    entity.getWorld().spawnEntity(proj);

                    entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                this.triggerCooldown(instance);
            }
        });

        // Ability 4: Mist Body
        this.addAbility(new QuirkSystem.Ability("Mist Body", QuirkSystem.AbilityType.TOGGLE, 100, 30, 25.0) {
            @Override
            public void onActivate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
                boolean isActive = data.runtimeTags.containsKey("WARP_MIST_BODY");

                if (!isActive) {
                    // Activate
                    data.runtimeTags.put("WARP_MIST_BODY", "true");
                    data.runtimeTags.put("WARP_START_X", String.valueOf(entity.getX()));
                    data.runtimeTags.put("WARP_START_Y", String.valueOf(entity.getY()));
                    data.runtimeTags.put("WARP_START_Z", String.valueOf(entity.getZ()));

                    if (entity instanceof PlayerEntity p) {
                        p.sendMessage(Text.of("§5Mist Body Active"), true);
                        p.getAbilities().allowFlying = true;
                        p.getAbilities().flying = true;
                        p.sendAbilitiesUpdate();
                        p.setInvisible(true);
                        entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.5f, 2.0f);
                    }
                } else {
                    // Deactivate & Set Temporary Portals
                    data.runtimeTags.remove("WARP_MIST_BODY");

                    data.runtimeTags.put("WARP_TEMP_PORTAL_ACTIVE", "true");
                    data.runtimeTags.put("WARP_TEMP_PORTAL_TIMER", "200"); // 10 seconds
                    data.runtimeTags.put("WARP_TEMP_END_X", String.valueOf(entity.getX()));
                    data.runtimeTags.put("WARP_TEMP_END_Y", String.valueOf(entity.getY()));
                    data.runtimeTags.put("WARP_TEMP_END_Z", String.valueOf(entity.getZ()));
                    // Start coords are already in WARP_START_X/Y/Z

                    if (entity instanceof PlayerEntity p) {
                        p.sendMessage(Text.of("§5Portals Created"), true);
                        if (!p.isCreative() && !p.isSpectator()) {
                            p.getAbilities().allowFlying = false;
                            p.getAbilities().flying = false;
                        }
                        p.sendAbilitiesUpdate();
                        p.setInvisible(false);
                    }
                    this.triggerCooldown(instance);
                }
            }
        });
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        for (QuirkSystem.Ability ability : this.getAbilities()) {
            ability.onHoldTick(entity, data, instance);
        }

        // AI: Auto-set anchor if missing
        if (!entity.getWorld().isClient && !(entity instanceof PlayerEntity)) {
            if (!instance.persistentData.contains("Anchors") || instance.persistentData.getList("Anchors", NbtElement.COMPOUND_TYPE).isEmpty()) {
                NbtList anchors = new NbtList();
                NbtCompound anchor = new NbtCompound();
                anchor.putDouble("X", entity.getX());
                anchor.putDouble("Y", entity.getY());
                anchor.putDouble("Z", entity.getZ());
                anchor.putString("Name", "Spawn Point");
                anchors.add(anchor);
                instance.persistentData.put("Anchors", anchors);
                instance.persistentData.putInt("SelectedAnchor", 0);
            }
        }

        World world = entity.getWorld();

        // --- 1. Handle Active Gate Anchor (Ability 1) ---
        if (data.runtimeTags.containsKey("WARP_ANCHOR_ACTIVE")) {
            Vec3d targetPos = getSelectedAnchorPos(instance);
            if (targetPos != null) {
                double startX = Double.parseDouble(data.runtimeTags.get("WARP_ANCHOR_START_X"));
                double startY = Double.parseDouble(data.runtimeTags.get("WARP_ANCHOR_START_Y"));
                double startZ = Double.parseDouble(data.runtimeTags.get("WARP_ANCHOR_START_Z"));
                Vec3d startPos = new Vec3d(startX, startY, startZ);

                tickPortal(world, startPos, targetPos, entity);
                tickPortal(world, targetPos, startPos, entity);
            }
        }

        // --- 2. Handle Temporary Portals (Ability 4 Aftermath) ---
        if (data.runtimeTags.containsKey("WARP_TEMP_PORTAL_ACTIVE")) {
            int timer = Integer.parseInt(data.runtimeTags.getOrDefault("WARP_TEMP_PORTAL_TIMER", "0"));

            if (timer > 0) {
                double startX = Double.parseDouble(data.runtimeTags.get("WARP_START_X"));
                double startY = Double.parseDouble(data.runtimeTags.get("WARP_START_Y"));
                double startZ = Double.parseDouble(data.runtimeTags.get("WARP_START_Z"));

                double endX = Double.parseDouble(data.runtimeTags.get("WARP_TEMP_END_X"));
                double endY = Double.parseDouble(data.runtimeTags.get("WARP_TEMP_END_Y"));
                double endZ = Double.parseDouble(data.runtimeTags.get("WARP_TEMP_END_Z"));

                Vec3d startPos = new Vec3d(startX, startY, startZ);
                Vec3d endPos = new Vec3d(endX, endY, endZ);

                tickPortal(world, startPos, endPos, entity);
                tickPortal(world, endPos, startPos, entity);

                data.runtimeTags.put("WARP_TEMP_PORTAL_TIMER", String.valueOf(timer - 1));
            } else {
                data.runtimeTags.remove("WARP_TEMP_PORTAL_ACTIVE");
                data.runtimeTags.remove("WARP_TEMP_PORTAL_TIMER");
            }
        }

        // --- 3. Handle Active Mist Body (Ability 4 Active) ---
        if (data.runtimeTags.containsKey("WARP_MIST_BODY")) {
            if (data.currentStamina < 0.5) {
                getAbilities().get(3).onActivate(entity, data, instance); // Force Toggle Off
                return;
            }
            data.currentStamina -= 0.5;
            entity.fallDistance = 0;

            entity.getWorld().addParticle(ParticleTypes.SQUID_INK, entity.getX(), entity.getY()+0.5, entity.getZ(), 0, 0, 0);

            if (entity.age % 20 == 0 && entity instanceof ServerPlayerEntity sp) {
                PlusUltraNetwork.sync(sp);
            }
        }
    }

    // Helper to render VFX and handle logic for a portal at 'location' leading to 'destination'
    private void tickPortal(World world, Vec3d location, Vec3d destination, LivingEntity owner) {
        // 1. VFX (Server-side spawning ensures visibility for all)
        if (!world.isClient && world instanceof ServerWorld sw) {
            // 2-Block High Column
            for(int i=0; i<3; i++) {
                double ox = (world.random.nextDouble() - 0.5) * 1.0;
                double oy = (world.random.nextDouble() * 2.0); // 0 to 2 height
                double oz = (world.random.nextDouble() - 0.5) * 1.0;

                sw.spawnParticles(ParticleTypes.PORTAL, location.x + ox, location.y + oy, location.z + oz, 1, (world.random.nextDouble()-0.5)*0.5, (world.random.nextDouble()-0.5)*0.5, (world.random.nextDouble()-0.5)*0.5, 0);

                if (world.random.nextBoolean()) {
                    sw.spawnParticles(ParticleTypes.SQUID_INK, location.x + ox, location.y + oy, location.z + oz, 1, 0, 0.05, 0, 0);
                }
            }
        }

        // 2. Teleport Logic
        Box box = new Box(location.x - 0.5, location.y, location.z - 0.5, location.x + 0.5, location.y + 2.0, location.z + 0.5);
        List<Entity> entities = world.getOtherEntities(null, box); // Get ALL entities, not just others, so owner can teleport

        for (Entity e : entities) {
            if (canTeleport(e)) {
                e.teleport(destination.x, destination.y, destination.z);
                world.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                setTeleportCooldown(e, 40); // 2 Seconds
            }
        }
    }

    private Vec3d getSelectedAnchorPos(QuirkSystem.QuirkData.QuirkInstance instance) {
        if (!instance.persistentData.contains("Anchors")) return null;
        NbtList anchors = instance.persistentData.getList("Anchors", NbtElement.COMPOUND_TYPE);
        if (anchors.isEmpty()) return null;

        int idx = instance.persistentData.getInt("SelectedAnchor");
        if (idx < 0 || idx >= anchors.size()) idx = 0;

        NbtCompound tag = anchors.getCompound(idx);
        return new Vec3d(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
    }

    public static boolean canTeleport(Entity entity) {
        if (entity instanceof LivingEntity living && living instanceof IQuirkDataAccessor accessor) {
            QuirkSystem.QuirkData data = accessor.getQuirkData();
            long until = Long.parseLong(data.runtimeTags.getOrDefault("WARP_COOLDOWN_UNTIL", "0"));
            return entity.getWorld().getTime() > until;
        }
        // Non-living entities (items/arrows) cooldown check
        return entity.age % 20 == 0;
    }

    public static void setTeleportCooldown(Entity entity, int ticks) {
        if (entity instanceof LivingEntity living && living instanceof IQuirkDataAccessor accessor) {
            QuirkSystem.QuirkData data = accessor.getQuirkData();
            data.runtimeTags.put("WARP_COOLDOWN_UNTIL", String.valueOf(entity.getWorld().getTime() + ticks));
        }
    }
}