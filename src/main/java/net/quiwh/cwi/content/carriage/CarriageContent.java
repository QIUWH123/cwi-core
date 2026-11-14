package net.quiwh.cwi.content.carriage;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import com.simibubi.create.foundation.data.SharedProperties;
import com.simibubi.create.foundation.data.CreateEntityBuilder;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.entry.EntityEntry;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.quiwh.cwi.CwiCore;

/** A native Create contraption carried by a chain-conveyor connection. */
public final class CarriageContent {
    private static final DeferredRegister<ContraptionType> CONTRAPTION_TYPES =
            DeferredRegister.create(CreateRegistries.CONTRAPTION_TYPE, CwiCore.ID);
    public static final BlockEntry<ChainCarriageBlock> BLOCK = CwiCore.REGISTRATE
            .block("chain_conveyor_carriage", ChainCarriageBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(properties -> properties.noOcclusion().mapColor(MapColor.METAL).sound(SoundType.METAL))
            .onRegister(MovingInteractionBehaviour.interactionBehaviour(new ChainCarriageInteraction()))
            .simpleItem().register();

    public static final BlockEntityEntry<ChainCarriageBlockEntity> BLOCK_ENTITY = CwiCore.REGISTRATE
            .blockEntity("chain_conveyor_carriage", ChainCarriageBlockEntity::new)
            .validBlocks(BLOCK).register();

    public static final DeferredHolder<ContraptionType, ContraptionType> CONTRAPTION = CONTRAPTION_TYPES.register(
            "chain_conveyor_carriage", () -> new ContraptionType(ChainCarriageContraption::new));

    public static final EntityEntry<ChainCarriageEntity> ENTITY = ((CreateEntityBuilder<ChainCarriageEntity, ?>) CwiCore.REGISTRATE
            .entity("chain_conveyor_carriage", ChainCarriageEntity::new, MobCategory.MISC)
            .properties(AbstractContraptionEntity::build)
            .properties(builder -> builder.fireImmune().setTrackingRange(10).setUpdateInterval(1)
                    .setShouldReceiveVelocityUpdates(false))
            .renderer(() -> ContraptionEntityRenderer::new))
            .visual(() -> ContraptionVisual::new)
            .tag(net.neoforged.neoforge.common.Tags.EntityTypes.TELEPORTING_NOT_SUPPORTED)
            .register();

    public static void register(IEventBus bus) {
        CONTRAPTION_TYPES.register(bus);
    }

    private CarriageContent() {}
}
