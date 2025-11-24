package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

public class QuirklessQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "quirkless");

    public QuirklessQuirk() { super(ID); }

    @Override
    public void registerAbilities() {

    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {

    }
}
