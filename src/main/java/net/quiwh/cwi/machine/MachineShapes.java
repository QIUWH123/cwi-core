package net.quiwh.cwi.machine;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Collision geometry retained from the original machine definitions. */
public final class MachineShapes {
    private static final VoxelShape EXTRACTOR = Shapes.or(
            Shapes.box(-.5, 0, 0, 1.5, 1, 1), Shapes.box(0, 0, -.5, 1, 1, 1.5),
            Shapes.box(-.25, 0, -.25, 1.25, 1, 1.25));
    private static final VoxelShape BLADE = Shapes.or(
            Shapes.box(.375, 0, .375, .625, 1, .625), Shapes.box(.185, .315, .185, .815, .685, .815));
    private static final VoxelShape SOLAR = Shapes.or(Shapes.box(0, 0, 0, 1, .12, 1),
            Shapes.box(.88, .12, 0, 1, .5, 1), Shapes.box(0, .12, .88, 1, .5, 1),
            Shapes.box(0, .12, 0, .12, .5, 1), Shapes.box(0, .12, 0, 1, .5, .12));

    public static VoxelShape shape(String id, Direction facing) {
        return switch (id) {
            case "lava_extractor_head", "lava_extractor_pump", "lava_extractor_stress_input" -> EXTRACTOR;
            case "lava_extractor_pump_blade" -> BLADE;
            case "solar_evaporator" -> SOLAR;
            case "stirling_engine" -> {
                VoxelShape base = Shapes.box(0, 0, 0, 1, .19, 1);
                yield Shapes.or(base, switch (facing) {
                    case EAST -> Shapes.box(0, .19, 0, .875, .815, 1);
                    case SOUTH -> Shapes.box(0, .19, 0, 1, .815, .875);
                    case WEST -> Shapes.box(.125, .19, 0, 1, .815, 1);
                    default -> Shapes.box(0, .19, .125, 1, .815, 1);
                });
            }
            default -> Shapes.block();
        };
    }

    private MachineShapes() {}
}
