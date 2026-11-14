import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.compat.jei.category.animations.AnimatedSpout;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.createmod.catnip.gui.element.GuiGameElement.GuiRenderBuilder;
import net.createmod.catnip.render.FluidRenderHelper;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Runs both real animation methods; captures model/box submissions instead of GPU rendering. */
public final class WorldSpoutingAnimationFixture {
    static final String SELF="WorldSpoutingAnimationFixture";
    static PoseStack pose=new PoseStack(); static float time;
    record Model(String name,double scale,Matrix4f pose,List<Double> local){}
    static List<String> textures=new ArrayList<>();
    public static void texture(com.simibubi.create.foundation.gui.AllGuiTextures texture,GuiGraphics g,int x,int y){textures.add(texture.name()+":"+x+","+y);}
    record Box(FluidStack fluid,List<Float> bounds,Matrix4f pose,int light){}
    static List<Model> models=new ArrayList<>();static List<Box> boxes=new ArrayList<>();
    static int assertions;
    static void check(boolean b,String why){assertions++;if(!b)throw new AssertionError(why);}
    public static float frameTime(){return time;}
    public static PoseStack pose(){return pose;}
    public static void noop(){}
    public static void flush(GuiGraphics g){}
    public static MultiBufferSource.BufferSource buffer(){return null;}
    public static BlockState state(String name){return (name.equals("SPOUT")?Blocks.IRON_BLOCK:Blocks.OAK_SLAB).defaultBlockState();}
    public static PartialModel partial(String name){return PartialModel.of(net.minecraft.resources.ResourceLocation.parse("create:block/"+name.toLowerCase(Locale.ROOT)));}
    static final class Recorder extends GuiRenderBuilder {
        final String name;Recorder(String n){name=n;}
        @Override public void render(GuiGraphics g){models.add(new Model(name,scale,new Matrix4f(pose.last().pose()),List.of(xRot,yRot,zRot,xLocal,yLocal,zLocal)));}
    }
    public static GuiRenderBuilder block(BlockState state){return new Recorder(state.is(Blocks.IRON_BLOCK)?"spout":state.is(Blocks.OAK_SLAB)?"depot":net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());}
    public static GuiRenderBuilder model(PartialModel model){return new Recorder(model.modelLocation().getPath());}
    public static void box(FluidRenderHelper ignored,Object fluid,float x1,float y1,float z1,float x2,float y2,float z2,MultiBufferSource buffer,PoseStack p,int light,boolean a,boolean b){
        boxes.add(new Box(((FluidStack)fluid).copy(),List.of(x1,y1,z1,x2,y2,z2),new Matrix4f(p.last().pose()),light));}
    static void body(MethodNode m,AbstractInsnNode... code){m.instructions.clear();m.tryCatchBlocks.clear();m.localVariables=null;for(var insn:code)m.instructions.add(insn);}
    static void capture(Path out)throws Exception {
        for(String name:List.of("net/minecraft/client/gui/GuiGraphics","net/createmod/catnip/gui/element/GuiGameElement",
                "com/simibubi/create/compat/jei/category/animations/AnimatedSpout","net/quiwh/cwi/compat/jei/WorldSpoutingJeiAnimation","net/quiwh/cwi/compat/jei/WorldSpoutingJeiCategory", "net/quiwh/cwi/compat/jei/FurnaceJeiAnimation")){
            var node=new ClassNode();try(var input=WorldSpoutingAnimationFixture.class.getResourceAsStream("/"+name+".class")){new ClassReader(input).accept(node,0);}
            for(var m:node.methods){
                if(name.endsWith("GuiGraphics")){
                    if(m.name.equals("pose")&&m.desc.equals("()Lcom/mojang/blaze3d/vertex/PoseStack;"))body(m,new MethodInsnNode(Opcodes.INVOKESTATIC,SELF,"pose",m.desc,false),new InsnNode(Opcodes.ARETURN));
                    if(m.name.equals("bufferSource")&&m.desc.startsWith("()"))body(m,new MethodInsnNode(Opcodes.INVOKESTATIC,SELF,"buffer",m.desc,false),new InsnNode(Opcodes.ARETURN));
                    if(m.name.equals("flush")&&m.desc.equals("()V"))body(m,new InsnNode(Opcodes.RETURN));
                    continue;
                }
                if(name.endsWith("GuiGameElement")){
                    if(!m.name.equals("of"))continue;
                    String factory=m.desc.startsWith("(Lnet/minecraft/world/level/block/state/BlockState;)")?"block":m.desc.startsWith("(Ldev/engine_room/flywheel/lib/model/baked/PartialModel;)")?"model":null;
                    if(factory!=null)body(m,new VarInsnNode(Opcodes.ALOAD,0),new MethodInsnNode(Opcodes.INVOKESTATIC,SELF,factory,m.desc,false),new InsnNode(Opcodes.ARETURN));continue;
                }
                for(var instruction:m.instructions.toArray()){
                    if(instruction instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC){
                        if(f.owner.equals("com/simibubi/create/AllBlocks"))m.instructions.set(f,new LdcInsnNode(f.name));
                        else if(f.owner.equals("com/simibubi/create/AllPartialModels")){
                            m.instructions.insertBefore(f,new LdcInsnNode(f.name));m.instructions.set(f,new MethodInsnNode(Opcodes.INVOKESTATIC,SELF,"partial","(Ljava/lang/String;)Ldev/engine_room/flywheel/lib/model/baked/PartialModel;",false));
                        }else if(f.name.equals("FLUID_RENDERER")&&f.owner.endsWith("CatnipServices"))m.instructions.set(f,new InsnNode(Opcodes.ACONST_NULL));
                    }
                    if(instruction instanceof MethodInsnNode call){
                        if(call.owner.equals("com/simibubi/create/foundation/gui/AllGuiTextures")&&call.name.equals("render")&&call.desc.equals("(Lnet/minecraft/client/gui/GuiGraphics;II)V"))m.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,SELF,"texture","(Lcom/simibubi/create/foundation/gui/AllGuiTextures;Lnet/minecraft/client/gui/GuiGraphics;II)V",false));
                        else if(call.owner.equals("com/tterrag/registrate/util/entry/BlockEntry")&&call.name.equals("getDefaultState"))m.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,SELF,"state","(Ljava/lang/String;)Lnet/minecraft/world/level/block/state/BlockState;",false));
                        else if(call.owner.equals("net/createmod/catnip/animation/AnimationTickHolder")&&call.name.equals("getRenderTime"))m.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,SELF,"frameTime","()F",false));
                        else if(call.name.equals("applyLighting")&&call.desc.equals("()V"))m.instructions.set(call,new InsnNode(Opcodes.POP));
                        else if(call.owner.equals("com/mojang/blaze3d/platform/Lighting")&&call.name.equals("setupFor3DItems"))m.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,SELF,"noop","()V",false));
                        else if(call.owner.equals("net/createmod/catnip/render/FluidRenderHelper")&&call.name.equals("renderFluidBox"))m.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,SELF,"box","(Lnet/createmod/catnip/render/FluidRenderHelper;"+call.desc.substring(1),false));
                    }
                }
            }
            var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);var dest=out.resolve(name+".class");Files.createDirectories(dest.getParent());Files.write(dest,writer.toByteArray());
        }
    }
    static Matrix4f relative(Model origin,Model target){return new Matrix4f(origin.pose).invert().mul(target.pose);}
    static void close(float a,float b,String why){check(Math.abs(a-b)<.0001f,why+": "+a+" / "+b);}
    static void categoryPreview(GuiGraphics graphics)throws Exception {
        net.neoforged.neoforge.registries.GameData.unfreezeData();
        var bus=net.neoforged.bus.api.BusBuilder.builder().build();net.quiwh.cwi.interaction.InteractionRecipes.register(bus);
        bus.post(MinimalNativeBootstrap.event(net.minecraft.core.registries.Registries.RECIPE_TYPE,net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE));
        bus.post(MinimalNativeBootstrap.event(net.minecraft.core.registries.Registries.RECIPE_SERIALIZER,net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER));
        var tag=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,net.minecraft.resources.ResourceLocation.parse("cwi:render/input"));
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.bindTags(Map.of(tag,List.of(Blocks.DIRT.builtInRegistryHolder(),Blocks.STONE.builtInRegistryHolder())));
        var fluidTag=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID,net.minecraft.resources.ResourceLocation.parse("cwi:render/fluid"));
        net.minecraft.core.registries.BuiltInRegistries.FLUID.bindTags(Map.of(fluidTag,List.of(Fluids.WATER.builtInRegistryHolder(),Fluids.LAVA.builtInRegistryHolder())));
        var recipe=WorldSpoutingJeiFixture.recipe(net.quiwh.cwi.interaction.InteractionRecipes.Kind.BLOCK_SPOUTING,"""
            {"input":"#cwi:render/input","fluid":{"tag":"cwi:render/fluid","amount":50},"outputs":["minecraft:mud"]}
            """);
        var category=WorldSpoutingJeiFixture.category(recipe);
        var views=WorldSpoutingJeiFixture.proxy(mezz.jei.api.gui.ingredient.IRecipeSlotsView.class,(p,m,a)-> {
            if(m.getName().equals("findSlotByName"))return Optional.of(WorldSpoutingJeiFixture.proxy(mezz.jei.api.gui.ingredient.IRecipeSlotView.class,(q,n,b)->switch(n.getName()){
                case "getDisplayedItemStack"->Optional.of(new net.minecraft.world.item.ItemStack(Blocks.STONE));
                case "getDisplayedIngredient"->Optional.of(new FluidStack(Fluids.LAVA,50));
                default->WorldSpoutingJeiFixture.def(n.getReturnType());}));
            return WorldSpoutingJeiFixture.def(m.getReturnType());
        });
        pose=new PoseStack();models.clear();boxes.clear();textures.clear();time=10;
        category.draw(new net.minecraft.world.item.crafting.RecipeHolder<>(recipe.kind().id(),recipe),views,graphics,0,0);
        check(textures.equals(List.of("JEI_SHADOW:65,109","JEI_LONG_ARROW:54,135")),"authored shadow/arrow coordinates");
        check(models.getFirst().name.equals("minecraft:stone"),"world preview follows displayed tagged block, not first tag entry");
        check(models.getFirst().local.equals(List.of(0d,180d,0d,0d,0d,2d))&&models.getFirst().scale==24,"authored block rotation/local offset/scale");
        var expected=new PoseStack();expected.translate(56,105,0);expected.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-12.5f));expected.mulPose(com.mojang.math.Axis.YP.rotationDegrees(22.5f));
        check(models.getFirst().pose.equals(expected.last().pose(),.0001f),"authored camera and block origin");expected.translate(0,-48,48);
        check(models.get(1).pose.equals(expected.last().pose(),.0001f),"authored spout origin without duplicate Create camera");
        check(boxes.stream().allMatch(b->b.fluid.getFluid()==Fluids.LAVA&&b.fluid.getAmount()==50),"animated fluid follows displayed tagged fluid, amount preserved");check(pose.clear(),"category poses balanced");
    }
    static void furnacePreview(GuiGraphics graphics)throws Exception {
        var bus=net.neoforged.bus.api.BusBuilder.builder().build();
        net.quiwh.cwi.machine.MachineBlocks.register(bus);net.quiwh.cwi.machine.FurnaceMaterials.register(bus);
        bus.post(MinimalNativeBootstrap.event(net.minecraft.core.registries.Registries.BLOCK,net.minecraft.core.registries.BuiltInRegistries.BLOCK));
        var type=Class.forName("net.quiwh.cwi.compat.jei.FurnaceJeiAnimation");var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);
        var draw=type.getDeclaredMethod("draw",GuiGraphics.class,int.class,int.class);draw.setAccessible(true);
        pose=new PoseStack();models.clear();draw.invoke(ctor.newInstance(),graphics,90,50);
        check(pose.clear(),"furnace preview balances its pose stack");
        check(models.stream().filter(m->m.name.equals("cwi:blast_furnace")).count()==1,"structure preview includes its native controller");
        check(models.stream().filter(m->m.name.equals("cwi:furnace_hatch")).count()==1,"structure preview includes a native hatch");
        check(models.getLast().name.equals("minecraft:fire"),"heat uses the animated vanilla fire model");
        int expected=1;
        for(int u=0;u<4;u++)for(int b=0;b<5;b++)for(int r=0;r<5;r++)
            if(net.quiwh.cwi.machine.FurnaceStructure.predicate(r,u,b)!='0' && !(u>0&&r>2&&b<2))expected++;
        check(models.size()==expected,"cutaway follows every real structure cell without an invented cube");
        for(var model:models){
            var v=model.local;
            var matrix=new Matrix4f(model.pose).scale((float)model.scale).translate(v.get(3).floatValue(),v.get(4).floatValue(),v.get(5).floatValue())
                .scale(1,-1,-1).rotateX((float)Math.toRadians(v.get(0))).rotateY((float)Math.toRadians(v.get(1)));
            for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++){
                var point=matrix.transformPosition(new org.joml.Vector3f(x,y,z));
                check(point.x>=54 && point.x<130 && point.y>=0 && point.y<77,
                    "native-scaled model stays between ingredient grids, above processing arrow: "+point);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==2&&args[0].equals("capture")){capture(Path.of(args[1]));return;}
        MinimalNativeBootstrap.initialize();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var unsafe=(sun.misc.Unsafe)field.get(null);
        var graphics=(GuiGraphics)unsafe.allocateInstance(GuiGraphics.class);
        var nativeSpout=new AnimatedSpout().withFluids(List.of(new FluidStack(Fluids.WATER,250)));
        var type=Class.forName("net.quiwh.cwi.compat.jei.WorldSpoutingJeiAnimation");var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);Object animation=ctor.newInstance();
        var draw=type.getDeclaredMethod("draw",GuiGraphics.class,FluidStack.class);draw.setAccessible(true);
        for(float tick:new float[]{0,2.5f,5,10,15,19.5f,20,25,29.5f,30,40}){
            time=tick;models.clear();boxes.clear();pose=new PoseStack();nativeSpout.draw(graphics,0,0);
            var nativeModels=List.copyOf(models);var nativeBoxes=boxes.stream().filter(b->b.bounds.get(3)>b.bounds.get(0)).toList();
            models.clear();boxes.clear();pose=new PoseStack();draw.invoke(animation,graphics,new FluidStack(Fluids.WATER,250));
            check(models.size()==4&&nativeModels.size()==5,"native spout and three bellows only; no depot for world recipes");
            for(int i=0;i<4;i++){
                check(models.get(i).name.equals(nativeModels.get(i).name),"same Create partials");
                check(models.get(i).scale==24&&nativeModels.get(i).scale==20,"authored vs native model scale");
                check(relative(models.getFirst(),models.get(i)).equals(relative(nativeModels.getFirst(),nativeModels.get(i)),.0001f),"same Create bellows translations at tick "+tick);
            }
            check(boxes.size()==nativeBoxes.size(),"same stream on/off cycle at "+tick);
            for(int i=0;i<boxes.size();i++){
                var actual=boxes.get(i);var expected=nativeBoxes.get(i);check(actual.bounds.equals(expected.bounds),"same fluid box/stream width");
                check(actual.light==15728880&&actual.fluid.getFluid()==Fluids.WATER&&actual.fluid.getAmount()==250,"configured fluid/full brightness");
                var world=new Matrix4f(models.getFirst().pose).invert().mul(actual.pose);
                var original=new Matrix4f(nativeModels.getFirst().pose).invert().mul(expected.pose);
                close(world.m00(),original.m00()*1.2f,"scaled fluid width");close(world.m11(),original.m11()*1.2f,"scaled fluid height");
            }
            check(pose.clear(),"balanced pose stack");
        }
        models.clear();boxes.clear();pose=new PoseStack();draw.invoke(animation,graphics,FluidStack.EMPTY);check(models.size()==4&&boxes.isEmpty(),"empty fluid tags safely render only spout");check(pose.clear(),"empty-fluid poses balanced");
        categoryPreview(graphics);furnacePreview(graphics);
        System.out.println("WorldSpoutingAnimationFixture PASS: "+assertions+" assertions comparing actual Create AnimatedSpout and Core frames; bellows, stream cycle/width, models, fluids, lighting, scale, no depot");
    }
}
