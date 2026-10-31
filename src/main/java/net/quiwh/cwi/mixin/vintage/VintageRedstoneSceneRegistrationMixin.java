package net.quiwh.cwi.mixin.vintage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.negodya1.vintageimprovements.infrastructure.ponder.VintagePonderScene", remap = false)
abstract class VintageRedstoneSceneRegistrationMixin {
    @WrapOperation(method = "register", at = @At(value = "INVOKE",
            target = "Lnet/createmod/ponder/api/registration/PonderSceneRegistrationHelper;addStoryBoard(Ljava/lang/Object;Ljava/lang/String;Lnet/createmod/ponder/api/scene/PonderStoryBoard;[Lnet/minecraft/resources/ResourceLocation;)Lnet/createmod/ponder/api/registration/StoryBoardEntry;"))
    private static StoryBoardEntry cwi$skipRemovedModule(PonderSceneRegistrationHelper<?> helper, Object component,
            String schematic, PonderStoryBoard story, ResourceLocation[] tags, Operation<StoryBoardEntry> original) {
        // Machine storyboards stay registered; only the removed item's duplicate entry is omitted.
        if (component instanceof DeferredHolder<?, ?> holder && holder.getId().toString().equals("vintageimprovements:redstone_module")) return null;
        return original.call(helper, component, schematic, story, tags);
    }
}
