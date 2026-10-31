package net.quiwh.cwi.processing;

import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public final class CwiFanProcessingType implements FanProcessingType {
    private final FanRecipes.Entry recipes;
    private final TagKey<Block> catalysts;

    public CwiFanProcessingType(FanRecipes.Entry recipes) {
        this.recipes = recipes;
        catalysts = TagKey.create(Registries.BLOCK, recipes.mode().catalystId());
    }

    @Override public boolean isValidAt(Level level, BlockPos pos) { return level.getBlockState(pos).is(catalysts); }
    @Override public int getPriority() { return recipes.mode().priority(); }

    private Optional<RecipeHolder<FanRecipe>> find(ItemStack stack, Level level) {
        return level.getRecipeManager().getRecipeFor(recipes.recipeType(), new SingleRecipeInput(stack), level);
    }

    @Override public boolean canProcess(ItemStack stack, Level level) { return find(stack, level).isPresent(); }
    @Override public List<ItemStack> process(ItemStack stack, Level level) {
        return find(stack, level).map(holder -> RecipeApplier.applyRecipeOn(level, stack, holder.value(), true)).orElse(null);
    }

    @Override public void spawnProcessingParticles(Level level, Vec3 position) {
        if (level.random.nextInt(8) != 0) return;
        ParticleOptions particle = recipes.mode() == FanMode.CORRODING
                ? biomancyParticle("falling_acid") : ParticleTypes.CLOUD;
        if (particle != null) {
            level.addParticle(particle, position.x, position.y + 0.1, position.z, 0, 0.02, 0);
        }
    }

    @Override public void morphAirFlow(AirFlowParticleAccess access, RandomSource random) {
        access.setColor(recipes.mode().color());
        access.setAlpha(recipes.mode().alpha());
        boolean corroding = recipes.mode() == FanMode.CORRODING;
        if (random.nextFloat() < (corroding ? 0.02F : 0.05F)) {
            ParticleOptions particle = corroding ? biomancyParticle("toxin_gas") : ParticleTypes.CLOUD;
            if (particle != null) access.spawnExtraParticle(particle, 0.2F);
        }
    }

    private static ParticleOptions biomancyParticle(String path) {
        // Biomancy supplies these optional visual effects; gameplay does not require it.
        return BuiltInRegistries.PARTICLE_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath("biomancy", path))
                .filter(ParticleOptions.class::isInstance).map(ParticleOptions.class::cast).orElse(null);
    }

    @Override public void affectEntity(Entity entity, Level level) {
        if (!level.isClientSide && entity.isAlive() && entity instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
        }
    }
}
