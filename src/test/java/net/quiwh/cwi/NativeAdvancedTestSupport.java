package net.quiwh.cwi;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.kinetics.motor.*;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import java.lang.reflect.Field;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.items.IItemHandler;

final class NativeAdvancedTestSupport {
    @SuppressWarnings({"unchecked","rawtypes"})
    static RecipeHolder<? extends ProcessingRecipe<?,?>> recipe(GameTestHelper h,String type,String suffix,String json){
        var serializer=BuiltInRegistries.RECIPE_SERIALIZER.get(ResourceLocation.parse(type));
        var value=(ProcessingRecipe<?,?>)serializer.codec().codec().parse(h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(json)).getOrThrow();
        return new RecipeHolder(ResourceLocation.parse("cwi:advanced_test/"+suffix),value);
    }
    @SuppressWarnings("unchecked") static <T extends SmartBlockEntity>T place(GameTestHelper h,BlockPos p,String id){
        h.setBlock(p,BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)));T entity=(T)h.getBlockEntity(p);entity.initialize();return entity;
    }
    static CreativeMotorBlockEntity drive(GameTestHelper h,KineticBlockEntity entity,int speed){
        var axis=((IRotate)entity.getBlockState().getBlock()).getRotationAxis(entity.getBlockState());var dir=Direction.get(Direction.AxisDirection.POSITIVE,axis);
        if(entity instanceof com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity)dir=Direction.DOWN;
        var relative=entity.getBlockPos().subtract(h.absolutePos(BlockPos.ZERO));
        if(entity instanceof com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity){
            var cog=relative.east();h.setBlock(cog,AllBlocks.COGWHEEL.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.Y));
            ((KineticBlockEntity)h.getBlockEntity(cog)).initialize();relative=cog;speed=-speed;
        }
        var p=relative.relative(dir);
        h.setBlock(p,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING,dir.getOpposite()));
        CreativeMotorBlockEntity motor=h.getBlockEntity(p);motor.generatedSpeed.setValue(speed);motor.initialize();entity.attachKinetics();
        h.assertTrue(Math.abs(entity.getSpeed())==Math.abs(speed),"native kinetic drive connects: machine="+entity.getBlockPos()+" motor="+motor.getBlockPos()+" axis="+axis+" rpm="+entity.getSpeed()+" generated="+motor.getGeneratedSpeed()+" state="+entity.getBlockState());return motor;
    }
    static int total(IItemHandler items,Item item){int sum=0;for(int slot=0;slot<items.getSlots();slot++){var stack=items.getStackInSlot(slot);if(stack.is(item))sum+=stack.getCount();}return sum;}
    static Field field(Class<?> owner,String name){for(var c=owner;c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new AssertionError(name);}
    static Object get(Object target,String name){try{return field(target.getClass(),name).get(target);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    static void set(Object target,String name,Object value){try{field(target.getClass(),name).set(target,value);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    @SuppressWarnings("unchecked") static <T extends SmartBlockEntity>T reload(GameTestHelper h,T entity){
        var tag=entity.saveWithoutMetadata(h.getLevel().registryAccess());entity.setRemoved();
        var copy=(T)entity.getType().create(entity.getBlockPos(),entity.getBlockState());copy.setLevel(h.getLevel());copy.loadCustomOnly(tag,h.getLevel().registryAccess());
        h.getLevel().setBlockEntity(copy);copy.initialize();if(copy instanceof KineticBlockEntity kinetic)kinetic.attachKinetics();return copy;
    }
    static String periodicItems(){return """
        {"ingredients":[{"ingredient":{"item":"minecraft:sand"},"per_tick":2,"tick_mode":"recipe"}],
         "results":[{"stack":{"id":"minecraft:clay_ball"},"per_tick":2,"tick_mode":"recipe"}],"processing_time":8,
         "output_pools":[{"entries":[{"stack":{"id":"minecraft:diamond"}}],"show_outputs":true}]}
        """;}
    private NativeAdvancedTestSupport(){}
}
