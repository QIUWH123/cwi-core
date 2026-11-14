package net.quiwh.cwi.mixin.pack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.PackCompatibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.screens.packs.PackSelectionModel$EntryBase")
public abstract class ResourcePackEntryMixin {
    @Shadow(remap = false) @Final private PackSelectionModel this$0;

    @Inject(method = "getCompatibility", at = @At("HEAD"), cancellable = true)
    private void cwi$hideResourcePackWarning(CallbackInfoReturnable<PackCompatibility> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        // Scope presentation to the resource-pack model, including retained entries after refresh.
        if (minecraft != null && ((PackSelectionModelAccessor) this.this$0).cwi$repository()
                == minecraft.getResourcePackRepository()) {
            cir.setReturnValue(PackCompatibility.COMPATIBLE);
        }
    }
}
