/*
 * Adapted from Create Bits 'n' Bobs 2.3.5 (MIT), by Cake, Kipti, NormalGuy,
 * Astral and Spydnel. See META-INF/LICENSE-bits-n-bobs.txt for the license.
 */
package net.quiwh.cwi.content.weathered;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.EnumMap;
import java.util.Map;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.quiwh.cwi.CwiCore;

/** Loaded from client setup before model baking; common block classes never reference it. */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = CwiCore.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WeatheredGirderClient {
    static final CTSpriteShiftEntry POLE_SHIFT = CTSpriteShifter.getCT(AllCTTypes.VERTICAL,
            CwiCore.loadResource("block/weathered_girder_pole_side"),
            CwiCore.loadResource("block/weathered_girder_pole_side_connected"));
    private static final Map<Direction, PartialModel> BRACKETS = new EnumMap<>(Direction.class);

    static {
        for (Direction direction : Iterate.horizontalDirections) {
            BRACKETS.put(direction, PartialModel.of(CwiCore.loadResource(
                    "block/weathered_metal_girder/bracket_" + direction.getSerializedName())));
        }
    }

    public static void init() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        init();
    }

    static PartialModel bracket(Direction direction) {
        return BRACKETS.get(direction);
    }

    /** Invoke on the client tick, alongside Create's native wrench outline. */
    public static void tickWrenchOutline() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.player.isShiftKeyDown()
                || !AllItems.WRENCH.isIn(minecraft.player.getMainHandItem())
                || !(minecraft.hitResult instanceof BlockHitResult hit)) return;
        BlockPos pos = hit.getBlockPos();
        if (!WeatheredContent.GIRDER.has(minecraft.level.getBlockState(pos))) return;
        var selected = WeatheredGirderWrenchBehaviour.getDirectionAndAction(hit, minecraft.level, pos);
        if (selected == null) return;
        Direction direction = selected.getFirst();
        Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal());
        Vec3 edge = VecHelper.getCenterOf(pos).add(normal.scale(0.4));
        double reach = selected.getSecond() == WeatheredGirderWrenchBehaviour.Action.PAIR ? 0.4 : 0.1;
        Vec3 corner1 = edge.add(0.3, 0, 0.3).add(normal.scale(reach));
        Vec3 corner2 = edge.add(-0.3, 0, -0.3).add(normal.scale(-0.2));
        Outliner.getInstance().showAABB("cwiWeatheredGirderWrench", new AABB(corner1, corner2))
                .lineWidth(0.03125F).colored(new Color(127, 127, 127));
    }

    @EventBusSubscriber(modid = CwiCore.ID, value = Dist.CLIENT)
    public static final class Ticks {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            tickWrenchOutline();
        }

        private Ticks() {}
    }

    private WeatheredGirderClient() {}
}
