package net.quiwh.cwi.sound;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.quiwh.cwi.CwiCore;

/** Native sound events for the pack's original custom audio. */
public final class CwiSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CwiCore.ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_SPAWN = register("player_spawn");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEEL_PIPE = register("steel_pipe");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(CwiCore.ID, name)));
    }

    public static void register(IEventBus bus) { SOUNDS.register(bus); }

    private CwiSounds() {}
}
