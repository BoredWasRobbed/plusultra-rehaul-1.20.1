package net.bored.mixin;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.bored.common.PlusUltraNetwork; // Added Import
import net.bored.common.QuirkRegistry;
import net.bored.config.PlusUltraConfig;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(MobEntity.class)
public class MobEntityMixin {

    @Inject(method = "initialize", at = @At("TAIL"))
    public void onInitialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, EntityData entityData, NbtCompound entityNbt, CallbackInfoReturnable<EntityData> cir) {
        PlusUltraConfig config = PlusUltraConfig.get();
        if (!config.mobsCanSpawnWithQuirks) return;

        QuirkSystem.QuirkData data = ((IQuirkDataAccessor) this).getQuirkData();
        if (!data.getQuirks().isEmpty()) return;

        Random random = world.getRandom();

        if (random.nextDouble() < config.mobQuirkChance) {
            assignRandomQuirk(data, config, random);
        }
    }

    private void assignRandomQuirk(QuirkSystem.QuirkData data, PlusUltraConfig config, Random random) {
        List<Identifier> validQuirks = new ArrayList<>();

        for (Identifier id : QuirkRegistry.getKeys()) {
            String idStr = id.toString();

            if (!config.isQuirkEnabled(idStr)) continue;

            if (idStr.equals("plusultra:one_for_all") ||
                    idStr.equals("plusultra:all_for_one") ||
                    idStr.equals("plusultra:quirk_bestowal")) {
                continue;
            }

            validQuirks.add(id);
        }

        if (!validQuirks.isEmpty()) {
            Identifier randomQuirk = validQuirks.get(random.nextInt(validQuirks.size()));
            data.addQuirk(randomQuirk.toString(), true);

            // NEW: Sync to trackers immediately so client knows to render particles
            if (!((MobEntity)(Object)this).getWorld().isClient) {
                PlusUltraNetwork.sync((MobEntity)(Object)this);
            }
        }
    }
}