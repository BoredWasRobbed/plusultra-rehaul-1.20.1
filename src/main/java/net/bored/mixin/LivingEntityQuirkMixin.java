package net.bored.mixin;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityQuirkMixin extends Entity implements IQuirkDataAccessor {
    @Unique private final QuirkSystem.QuirkData quirkData = new QuirkSystem.QuirkData();

    public LivingEntityQuirkMixin(EntityType<?> type, World world) { super(type, world); }

    @Override public QuirkSystem.QuirkData getQuirkData() { return quirkData; }

    @Inject(method = "writeCustomDataToNbt", at = @At("HEAD"))
    public void writeQuirk(NbtCompound nbt, CallbackInfo ci) { quirkData.writeToNbt(nbt); }

    @Inject(method = "readCustomDataFromNbt", at = @At("HEAD"))
    public void readQuirk(NbtCompound nbt, CallbackInfo ci) { quirkData.readFromNbt(nbt); }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tickQuirk(CallbackInfo ci) {
        if (!this.getWorld().isClient) {
            quirkData.tick((LivingEntity)(Object)this);
        }
    }
}