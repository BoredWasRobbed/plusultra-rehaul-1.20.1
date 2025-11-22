package net.bored.common;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;

import java.util.HashSet;
import java.util.Set;

public class UniqueQuirkState extends PersistentState {
    private final Set<String> takenQuirks = new HashSet<>();

    public static UniqueQuirkState getServerState(ServerWorld serverWorld) {
        // We attach this to the OVERWORLD to ensure it's global
        ServerWorld overworld = serverWorld.getServer().getOverworld();
        return overworld.getPersistentStateManager().getOrCreate(
                UniqueQuirkState::readNbt,
                UniqueQuirkState::new,
                "plusultra_unique_quirks"
        );
    }

    public static UniqueQuirkState readNbt(NbtCompound nbt) {
        UniqueQuirkState state = new UniqueQuirkState();
        NbtList list = nbt.getList("TakenQuirks", 8); // 8 = String
        for (int i = 0; i < list.size(); i++) {
            state.takenQuirks.add(list.getString(i));
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (String id : takenQuirks) {
            list.add(net.minecraft.nbt.NbtString.of(id));
        }
        nbt.put("TakenQuirks", list);
        return nbt;
    }

    public boolean isQuirkTaken(String quirkId) {
        return takenQuirks.contains(quirkId);
    }

    public void setQuirkTaken(String quirkId, boolean taken) {
        if (taken) {
            takenQuirks.add(quirkId);
        } else {
            takenQuirks.remove(quirkId);
        }
        this.markDirty();
    }
}