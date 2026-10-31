package net.quiwh.cwi.machine.client;

import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.CTType;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import com.simibubi.create.foundation.block.connected.SimpleCTBehaviour;
import com.simibubi.create.foundation.model.ModelSwapper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.quiwh.cwi.CwiCore;
import net.quiwh.cwi.machine.FurnaceMaterials;

/** Create's native CTModel consumes the converted atlas; no LDLib runtime is required. */
final class ReinforcementTextures {
    private static final CTType TYPE = new CTType() {
        @Override public ResourceLocation getId() { return CwiCore.loadResource("reinforcement"); }
        @Override public int getSheetSize() { return 16; }
        @Override public ConnectedTextureBehaviour.ContextRequirement getContextRequirement() {
            return ConnectedTextureBehaviour.ContextRequirement.builder().all().build();
        }
        @Override public int getTextureIndex(ConnectedTextureBehaviour.CTContext context) {
            return (context.up ? 1 : 0) | (context.down ? 2 : 0) | (context.left ? 4 : 0) | (context.right ? 8 : 0)
                    | (context.topLeft ? 16 : 0) | (context.topRight ? 32 : 0)
                    | (context.bottomLeft ? 64 : 0) | (context.bottomRight ? 128 : 0);
        }
    };
    private static final CTSpriteShiftEntry CAST = shift("cast_iron_blast_furnace_reinforcement");
    private static final CTSpriteShiftEntry STEEL = shift("steel_blast_furnace_reinforcement");

    private static CTSpriteShiftEntry shift(String name) {
        return CTSpriteShifter.getCT(TYPE, ResourceLocation.fromNamespaceAndPath("kubejs", "block/" + name),
                CwiCore.loadResource("block/" + name + "_connected"));
    }
    static void init() {}
    static void wrap(ModelEvent.ModifyBakingResult event) {
        wrap(event, FurnaceMaterials.CAST_REINFORCEMENT.get(), CAST);
        wrap(event, FurnaceMaterials.STEEL_REINFORCEMENT.get(), STEEL);
    }
    private static void wrap(ModelEvent.ModifyBakingResult event, Block block, CTSpriteShiftEntry shift) {
        ModelSwapper.swapModels(event.getModels(), ModelSwapper.getAllBlockStateModelLocations(block),
                original -> new CTModel(original, new SimpleCTBehaviour(shift)));
    }
    private ReinforcementTextures() {}
}
