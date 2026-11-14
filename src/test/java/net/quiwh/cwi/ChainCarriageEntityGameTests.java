package net.quiwh.cwi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.content.carriage.CarriageContent;
import net.quiwh.cwi.content.carriage.ChainCarriageContraption;
import net.quiwh.cwi.content.carriage.ChainCarriageEntity;
import net.quiwh.cwi.content.carriage.ChainConveyorAttachment;

/** Native tracking and persistent identity contracts; no graphical client is launched. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class ChainCarriageEntityGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void partialTrackingPacketsPreserveTargetsAndShortestRotation(GameTestHelper helper) throws Exception {
        ChainCarriageEntity entity = CarriageContent.ENTITY.get().create(helper.getLevel());
        helper.assertTrue(entity != null, "native entity factory resolves the Core carriage");
        entity.setInitialOrientation(Direction.SOUTH);
        entity.setPos(10, 2, 10);
        entity.yaw = entity.prevYaw = 170;
        entity.setYRot(170);
        entity.lerpTo(13, 5, 7, -170, 0, 3);
        helper.assertTrue(entity.lerpTargetX() == 13 && entity.lerpTargetY() == 5 && entity.lerpTargetZ() == 7
                        && entity.lerpTargetYRot() == -170,
                "native packet partial-update accessors retain the unconsumed server target");
        interpolate(entity);
        helper.assertTrue(Math.abs(entity.getX() - 11) < 1e-6 && Math.abs(entity.getY() - 3) < 1e-6
                        && Math.abs(entity.getZ() - 9) < 1e-6 && Math.abs(entity.yaw - 176.66667F) < .001F,
                "client interpolation advances position and crosses the 180-degree boundary by the shortest angle");
        // ClientPacketListener constructs a rotation-only update using the pending positional target.
        entity.lerpTo(entity.lerpTargetX(), entity.lerpTargetY(), entity.lerpTargetZ(), -160, 0, 3);
        for (int i = 0; i < 3; i++) interpolate(entity);
        helper.assertTrue(entity.position().distanceToSqr(new Vec3(13, 5, 7)) < 1e-10
                        && Math.abs(net.minecraft.util.Mth.wrapDegrees(entity.yaw + 160)) < .001
                        && entity.getYRot() == entity.yaw,
                "rotation-only packets preserve the full destination and feed native entity rotation");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void savedEntityRetainsAttachmentBlocksAndQueuedDisassembly(GameTestHelper helper) {
        var attachment = new ChainConveyorAttachment(new BlockPos(8, 20, 8), new BlockPos(12, 2, 0),
                3.25F, true, new BlockPos(1, 0, 0));
        var contraption = new ChainCarriageContraption(attachment);
        contraption.anchor = new BlockPos(8, 18, 8);
        contraption.bounds = new AABB(0, -1, 0, 1, 1, 1);
        contraption.getBlocks().put(BlockPos.ZERO,
                new StructureBlockInfo(BlockPos.ZERO, CarriageContent.BLOCK.getDefaultState(), null));
        contraption.getBlocks().put(BlockPos.ZERO.below(),
                new StructureBlockInfo(BlockPos.ZERO.below(), Blocks.STONE.defaultBlockState(), null));
        var entity = ChainCarriageEntity.create(helper.getLevel(), contraption);
        entity.setInitialOrientation(Direction.EAST);
        entity.setPos(8.5, 18, 8.5);
        entity.yaw = entity.prevYaw = 220;
        entity.setYRot(entity.yaw);
        entity.requestDisassembly();
        CompoundTag saved = new CompoundTag();
        helper.assertTrue(entity.save(saved), "native entity persistence accepts the moving carriage");
        var loaded = EntityType.loadEntityRecursive(saved, helper.getLevel(), restored -> restored);
        helper.assertTrue(loaded instanceof ChainCarriageEntity, "native entity registry restores the Core entity type");
        var restored = (ChainCarriageEntity) loaded;
        var restoredAttachment = restored.attachment();
        helper.assertTrue(restoredAttachment != null && restoredAttachment.write().equals(attachment.write())
                        && restored.getContraption().getBlocks().equals(contraption.getBlocks()),
                "native contraption persistence retains its directed chain position and assembled load");
        var resaved = new CompoundTag();
        restored.save(resaved);
        helper.assertTrue(resaved.getBoolean("CwiDisassemble") && restored.yaw == 220
                        && restored.prevYaw == restored.yaw && restored.getYRot() == restored.yaw,
                "reload preserves queued safe disassembly and initializes native/interpolated yaw together");
        helper.succeed();
    }

    private static void interpolate(ChainCarriageEntity entity) throws Exception {
        var method = ChainCarriageEntity.class.getDeclaredMethod("interpolateMovement");
        method.setAccessible(true);
        method.invoke(entity);
    }
}
