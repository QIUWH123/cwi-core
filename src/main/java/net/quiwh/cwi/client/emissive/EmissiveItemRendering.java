package net.quiwh.cwi.client.emissive;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.MatrixUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;

/** Snapshot the native seven seeded quad queries once, then draw each face exactly once. */
public final class EmissiveItemRendering {
    private static final Direction[] DIRECTIONS = Direction.values();
    private EmissiveItemRendering() {}

    public record Parts(BakedModel normal, BakedModel emissive) {}

    public static Parts split(BakedModel model) {
        var normal = new ArrayList<List<BakedQuad>>(7);
        var emissive = new ArrayList<List<BakedQuad>>(7);
        var random = RandomSource.create();
        boolean hasNormal = false, hasEmissive = false;
        for (int i = 0; i <= DIRECTIONS.length; i++) {
            random.setSeed(42);
            var quads = model.getQuads(null, i == DIRECTIONS.length ? null : DIRECTIONS[i], random);
            List<BakedQuad> dark = quads;
            List<BakedQuad> glow = List.of();
            for (int j = 0; j < quads.size(); j++) {
                var quad = quads.get(j);
                if (!EmissiveTextures.isEmissive(quad)) continue;
                if (glow.isEmpty()) {
                    dark = new ArrayList<>(quads.subList(0, j));
                    glow = new ArrayList<>();
                }
                glow.add(quad);
                for (int k = j + 1; k < quads.size(); k++) {
                    var next = quads.get(k);
                    (EmissiveTextures.isEmissive(next) ? glow : dark).add(next);
                }
                break;
            }
            hasNormal |= !dark.isEmpty();
            hasEmissive |= !glow.isEmpty();
            // Dynamic models may reuse a scratch list for the next face query.
            normal.add(List.copyOf(dark));
            emissive.add(List.copyOf(glow));
        }
        return new Parts(hasNormal ? new QuadSnapshot(model, normal) : null,
                hasEmissive ? new QuadSnapshot(model, emissive) : null);
    }

    public static com.mojang.blaze3d.vertex.VertexConsumer buffer(ItemStack stack, ItemDisplayContext display,
            PoseStack pose, MultiBufferSource buffers) {
        var type = NeoForgeRenderTypes.getUnlitTranslucent(TextureAtlas.LOCATION_BLOCKS);
        if (stack.hasFoil() && (stack.is(ItemTags.COMPASSES) || stack.is(Items.CLOCK))) {
            var glintPose = pose.last().copy();
            if (display == ItemDisplayContext.GUI) MatrixUtil.mulComponentWise(glintPose.pose(), 0.5f);
            else if (display.firstPerson()) MatrixUtil.mulComponentWise(glintPose.pose(), 0.75f);
            return ItemRenderer.getCompassFoilBuffer(buffers, type, glintPose);
        }
        boolean direct = true;
        if (display != ItemDisplayContext.GUI && !display.firstPerson() && stack.getItem() instanceof BlockItem item) {
            direct = !(item.getBlock() instanceof HalfTransparentBlock) && !(item.getBlock() instanceof StainedGlassPaneBlock);
        }
        return direct ? ItemRenderer.getFoilBufferDirect(buffers, type, true, stack.hasFoil())
                : ItemRenderer.getFoilBuffer(buffers, type, true, stack.hasFoil());
    }

    private static final class QuadSnapshot extends BakedModelWrapper<BakedModel> {
        private final List<List<BakedQuad>> quads;
        private QuadSnapshot(BakedModel model, List<List<BakedQuad>> quads) { super(model); this.quads = quads; }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return quads.get(side == null ? DIRECTIONS.length : side.ordinal());
        }
    }
}
