package net.quiwh.cwi.mixin.pack;

import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PackSelectionModel.class)
public interface PackSelectionModelAccessor {
    @Accessor("repository")
    PackRepository cwi$repository();
}
