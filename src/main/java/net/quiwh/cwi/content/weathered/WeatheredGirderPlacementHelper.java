/*
 * Adapted from Create Bits 'n' Bobs 2.3.5 (MIT), by Cake, Kipti, NormalGuy,
 * Astral and Spydnel. See META-INF/LICENSE-bits-n-bobs.txt for the license.
 */
package net.quiwh.cwi.content.weathered;

import com.simibubi.create.content.decoration.girder.GirderPlacementHelper;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class WeatheredGirderPlacementHelper extends GirderPlacementHelper {
    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return WeatheredContent.GIRDER::isIn;
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return state -> WeatheredContent.GIRDER.has(state) || WeatheredContent.GIRDER_SHAFT.has(state);
    }
}
