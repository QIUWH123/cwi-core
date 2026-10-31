package net.quiwh.cwi.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Only data-defined actions are allowed; no commands, reflection, or retained JS closures. */
public sealed interface ImpactAction {
    void apply(ItemEntity item, double distance);

    record Sound(SoundEvent event, float volume, float pitch) implements ImpactAction {
        @Override public void apply(ItemEntity item, double distance) {
            item.level().playSound(null, item.getX(), item.getY(), item.getZ(), event, SoundSource.BLOCKS, volume, pitch);
        }
    }
    record Particle(ParticleOptions options, int count, double dx, double dy, double dz, double speed) implements ImpactAction {
        @Override public void apply(ItemEntity item, double distance) {
            double xz = Math.pow(Math.max(distance, 1), .25), y = Math.pow(Math.max(distance, 1), .15);
            ((ServerLevel) item.level()).sendParticles(options, item.getX(), item.getY() + .2, item.getZ(), count,
                    dx * xz, dy * y, dz * xz, speed * xz);
        }
    }
    record Replace(ItemStack output, boolean copyComponents) implements ImpactAction {
        public Replace { output = output.copy(); }
        @Override public ItemStack output() { return output.copy(); }
        @Override public void apply(ItemEntity item, double distance) {
            var result = copyComponents ? item.getItem().transmuteCopy(output.getItem(), item.getItem().getCount())
                    : output.copyWithCount(item.getItem().getCount());
            if (copyComponents) result.applyComponents(output.getComponentsPatch());
            item.setItem(result);
        }
    }
    record Discard() implements ImpactAction {
        @Override public void apply(ItemEntity item, double distance) { item.discard(); }
    }
    record Place(BlockState state) implements ImpactAction {
        @Override public void apply(ItemEntity item, double distance) {
            BlockPos pos = item.blockPosition();
            if (item.level().getBlockState(pos).canBeReplaced()) item.level().setBlockAndUpdate(pos, state);
        }
    }
    record Explosion(float strength, boolean fire, Level.ExplosionInteraction interaction) implements ImpactAction {
        @Override public void apply(ItemEntity item, double distance) {
            item.level().explode(null, item.getX(), item.getY(), item.getZ(), strength, fire, interaction);
        }
    }

    static ImpactAction decode(RuleReader<?> r) {
        ImpactAction result = switch (r.string("type")) {
            case "sound" -> new Sound(r.registered("id", BuiltInRegistries.SOUND_EVENT),
                    (float) r.number("volume", 1, 0, 64), (float) r.number("pitch", 1, .001, 64));
            case "particle" -> new Particle(particle(r), r.integer("count", 1, 0, 4096),
                    r.number("dx", 0, 0, 64), r.number("dy", 0, 0, 64), r.number("dz", 0, 0, 64),
                    r.number("speed", 0, 0, 64));
            case "replace_item" -> new Replace(r.value("output", ItemStack.STRICT_CODEC), r.bool("copy_components", true));
            case "discard" -> new Discard();
            case "place_block" -> new Place(r.registered("block", BuiltInRegistries.BLOCK).defaultBlockState());
            case "explosion" -> new Explosion((float) r.number("strength", 1, 0, 64), r.bool("fire", false),
                    Level.ExplosionInteraction.valueOf(r.string("interaction", "TNT")));
            default -> throw new IllegalArgumentException("Unknown impact action type");
        };
        r.finish();
        return result;
    }

    private static ParticleOptions particle(RuleReader<?> r) {
        var type = r.registered("id", BuiltInRegistries.PARTICLE_TYPE);
        if (type == ParticleTypes.BLOCK) return new BlockParticleOption(ParticleTypes.BLOCK,
                r.registered("block", BuiltInRegistries.BLOCK).defaultBlockState());
        if (type == ParticleTypes.FALLING_DUST) return new BlockParticleOption(ParticleTypes.FALLING_DUST,
                r.registered("block", BuiltInRegistries.BLOCK).defaultBlockState());
        if (type instanceof SimpleParticleType simple) return simple;
        throw new IllegalArgumentException("Particle requires unsupported data: " + BuiltInRegistries.PARTICLE_TYPE.getKey(type));
    }
}
