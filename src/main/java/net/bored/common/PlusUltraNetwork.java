package net.bored.common;

import net.bored.api.IQuirkDataAccessor;
import net.bored.api.QuirkSystem;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class PlusUltraNetwork {
    public static final Identifier ACTIVATE_ABILITY = new Identifier("plusultra", "activate_ability");
    public static final Identifier SWITCH_ABILITY = new Identifier("plusultra", "switch_ability");
    public static final Identifier SWITCH_QUIRK = new Identifier("plusultra", "switch_quirk");
    public static final Identifier SYNC_DATA = new Identifier("plusultra", "sync_data");
    public static final Identifier OPEN_STEAL_SELECTION = new Identifier("plusultra", "open_steal_selection");
    public static final Identifier PERFORM_STEAL = new Identifier("plusultra", "perform_steal");
    public static final Identifier TOGGLE_ABILITY = new Identifier("plusultra", "toggle_ability");

    // NEW: Packet to handle stat upgrades
    public static final Identifier UPGRADE_STAT = new Identifier("plusultra", "upgrade_stat");

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ACTIVATE_ABILITY, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                QuirkSystem.QuirkData data = ((IQuirkDataAccessor)player).getQuirkData();
                if (data.getQuirks().isEmpty()) return;

                int qIndex = data.getSelectedQuirkIndex();
                if (qIndex < 0 || qIndex >= data.getQuirks().size()) return;

                QuirkSystem.QuirkData.QuirkInstance instance = data.getQuirks().get(qIndex);
                QuirkSystem.Quirk quirk = QuirkRegistry.get(new Identifier(instance.quirkId));

                if (quirk != null) {
                    int aIndex = data.getSelectedAbilityIndex();
                    if (aIndex >= 0 && aIndex < quirk.getAbilities().size()) {
                        QuirkSystem.Ability ability = quirk.getAbilities().get(aIndex);
                        if (ability.canUse(data)) {
                            ability.onActivate(player, data, instance);
                            sync(player);
                        } else {
                            if (data.level < ability.getRequiredLevel()) player.sendMessage(Text.of("§cLevel too low!"), true);
                            else if (!ability.isReady()) player.sendMessage(Text.of("§cCooldown!"), true);
                            else player.sendMessage(Text.of("§cNot enough Stamina!"), true);
                        }
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_ABILITY, (server, player, handler, buf, responseSender) -> {
            int qIndex = buf.readInt();
            server.execute(() -> {
                QuirkSystem.QuirkData data = ((IQuirkDataAccessor)player).getQuirkData();
                if (qIndex < 0 || qIndex >= data.getQuirks().size()) return;

                QuirkSystem.QuirkData.QuirkInstance instance = data.getQuirks().get(qIndex);
                QuirkSystem.Quirk quirk = QuirkRegistry.get(new Identifier(instance.quirkId));

                if (quirk != null) {
                    for(QuirkSystem.Ability ability : quirk.getAbilities()) {
                        if(ability.getType() == QuirkSystem.AbilityType.TOGGLE) {
                            if (ability.canUse(data)) {
                                ability.onActivate(player, data, instance);
                                sync(player);
                            }
                            return;
                        }
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SWITCH_ABILITY, (server, player, handler, buf, responseSender) -> {
            int direction = buf.readInt();
            server.execute(() -> {
                QuirkSystem.QuirkData data = ((IQuirkDataAccessor)player).getQuirkData();
                if (data.getQuirks().isEmpty()) return;

                QuirkSystem.QuirkData.QuirkInstance instance = data.getQuirks().get(data.getSelectedQuirkIndex());
                QuirkSystem.Quirk quirk = QuirkRegistry.get(new Identifier(instance.quirkId));

                if (quirk != null) {
                    data.cycleAbility(direction, quirk.getAbilities().size());
                    sync(player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SWITCH_QUIRK, (server, player, handler, buf, responseSender) -> {
            int index = buf.readInt();
            server.execute(() -> {
                ((IQuirkDataAccessor)player).getQuirkData().setSelectedQuirkIndex(index);
                ((IQuirkDataAccessor)player).getQuirkData().cycleAbility(0, 1);
                sync(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PERFORM_STEAL, (server, player, handler, buf, responseSender) -> {
            int targetId = buf.readInt();
            String quirkToSteal = buf.readString();

            server.execute(() -> {
                Entity entity = player.getWorld().getEntityById(targetId);
                if (!(entity instanceof LivingEntity target)) return;

                if (player.distanceTo(target) > 10) return;

                QuirkSystem.QuirkData attackerData = ((IQuirkDataAccessor)player).getQuirkData();
                QuirkSystem.QuirkData targetData = ((IQuirkDataAccessor)target).getQuirkData();

                attackerData.addQuirk(quirkToSteal);
                boolean fullyRemoved = targetData.removeQuirk(quirkToSteal);

                if (fullyRemoved) {
                    QuirkSystem.Quirk q = QuirkRegistry.get(new Identifier(quirkToSteal));
                    if (q != null) q.onRemove(target, targetData);
                }

                player.sendMessage(Text.of("§cStole " + quirkToSteal + "!"), true);
                if (target instanceof ServerPlayerEntity tp) {
                    tp.sendMessage(Text.of("§4Your quirk " + quirkToSteal + " was stolen!"), true);
                    sync(tp);
                }
                sync(player);
            });
        });

        // NEW: Handle Stat Upgrades
        ServerPlayNetworking.registerGlobalReceiver(UPGRADE_STAT, (server, player, handler, buf, responseSender) -> {
            int statIndex = buf.readInt();
            int amount = buf.readInt();

            server.execute(() -> {
                QuirkSystem.QuirkData data = ((IQuirkDataAccessor) player).getQuirkData();

                if (amount <= 0 || data.statPoints < amount) return;

                // 0: Strength, 1: Endurance, 2: Speed, 3: Stamina, 4: Meta
                switch (statIndex) {
                    case 0 -> data.strength += amount;
                    case 1 -> data.endurance += amount;
                    case 2 -> data.speed += amount;
                    case 3 -> data.staminaMax += amount;
                    case 4 -> data.meta += amount;
                }

                data.statPoints -= amount;
                sync(player);
            });
        });
    }

    public static void sync(ServerPlayerEntity player) {
        QuirkSystem.QuirkData data = ((IQuirkDataAccessor)player).getQuirkData();
        PacketByteBuf buf = PacketByteBufs.create();
        NbtCompound nbt = new NbtCompound();
        data.writeToNbt(nbt);
        buf.writeNbt(nbt);
        ServerPlayNetworking.send(player, SYNC_DATA, buf);
    }
}