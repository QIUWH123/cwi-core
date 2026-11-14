package net.quiwh.cwi.content.weathered;

import com.simibubi.create.AllTags.AllItemTags;
import com.simibubi.create.content.decoration.bracket.BracketBlock;
import com.simibubi.create.content.decoration.bracket.BracketBlockItem;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.quiwh.cwi.CwiCore;

/** Selected weathered decoration, independent of the original addon and its libraries. */
public final class WeatheredContent {
    public static final BlockEntry<WeatheredGirderBlock> GIRDER = CwiCore.REGISTRATE
            .block("weathered_metal_girder", WeatheredGirderBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(properties -> properties.mapColor(MapColor.COLOR_GRAY).sound(SoundType.NETHERITE_BLOCK))
            .onRegister(CreateRegistrate.blockModel(() -> WeatheredConnectedGirderModel::new))
            .simpleItem()
            .register();

    public static final BlockEntry<WeatheredGirderEncasedShaftBlock> GIRDER_SHAFT = CwiCore.REGISTRATE
            .block("weathered_metal_girder_encased_shaft", WeatheredGirderEncasedShaftBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(properties -> properties.mapColor(MapColor.COLOR_GRAY).sound(SoundType.NETHERITE_BLOCK))
            .onRegister(CreateRegistrate.blockModel(() -> WeatheredConnectedGirderModel::new))
            .register();

    public static final BlockEntry<BracketBlock> BRACKET = CwiCore.REGISTRATE
            .block("weathered_metal_bracket", BracketBlock::new)
            .properties(properties -> properties.sound(SoundType.NETHERITE_BLOCK))
            .item(BracketBlockItem::new)
            .tag(AllItemTags.INVALID_FOR_TRACK_PAVING.tag)
            .build()
            .register();

    public static final BlockEntityEntry<KineticBlockEntity> ENCASED_SHAFT = CwiCore.REGISTRATE
            .blockEntity("weathered_girder_encased_shaft", KineticBlockEntity::new)
            .visual(() -> SingleAxisRotatingVisual::shaft, false)
            .validBlocks(GIRDER_SHAFT)
            .renderer(() -> ShaftRenderer::new)
            .register();

    public static void register(IEventBus bus) {
        // Entries are registered by Core's shared Registrate event listeners.
    }

    private WeatheredContent() {}
}
