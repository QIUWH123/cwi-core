import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.compat.jei.category.animations.AnimatedMillstone;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.createmod.catnip.gui.element.GuiGameElement.GuiRenderBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** One-off native draw capture: compare effective matrices, slots and animation without a GPU. */
public final class RollingJeiAlignmentFixture {
 static final String SELF="RollingJeiAlignmentFixture";
 static PoseStack pose=new PoseStack();static float frame;
 record Model(String name,Matrix4f matrix){}
 static List<Model> models=new ArrayList<>();static List<String> textures=new ArrayList<>();
 public static PoseStack pose(){return pose;}
 public static float time(){return frame;}
 public static void texture(com.simibubi.create.foundation.gui.AllGuiTextures t,GuiGraphics g,int x,int y){textures.add(t.name()+":"+x+","+y);}
 public static BlockState state(String name){return (name.equals("ROLLING_MILL")?Blocks.IRON_BLOCK:Blocks.COBBLESTONE).defaultBlockState();}
 public static BlockState shaft(Direction.Axis axis){return Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS,axis);}
 public static PartialModel partial(String name){return PartialModel.of(ResourceLocation.parse("create:block/"+name.toLowerCase(Locale.ROOT)));}
 static final class Recorder extends GuiRenderBuilder {
  final String name;Recorder(String n){name=n;}
  @Override public void render(GuiGraphics g){var effective=new PoseStack();effective.mulPose(new Matrix4f(pose.last().pose()));transformMatrix(effective);models.add(new Model(name,new Matrix4f(effective.last().pose())));}
 }
 public static GuiRenderBuilder block(BlockState state){return new Recorder(state.is(Blocks.IRON_BLOCK)?"rolling":state.is(Blocks.COBBLESTONE)?"millstone":"shaft");}
 public static GuiRenderBuilder model(PartialModel model){return new Recorder(model.modelLocation().toString());}
 static void body(MethodNode m,AbstractInsnNode... code){m.instructions.clear();m.tryCatchBlocks.clear();m.localVariables=null;for(var i:code)m.instructions.add(i);}
 static void capture(Path out)throws Exception {
  for(String name:List.of("net/minecraft/client/gui/GuiGraphics","net/createmod/catnip/gui/element/GuiGameElement",
   "com/simibubi/create/compat/jei/category/animations/AnimatedKinetics","com/simibubi/create/compat/jei/category/animations/AnimatedMillstone",
   "com/mrh0/createaddition/compat/jei/AnimatedRollingMill","com/mrh0/createaddition/compat/jei/RollingMillCategory","com/simibubi/create/compat/jei/category/MillingCategory")){
   var node=new ClassNode();try(var in=RollingJeiAlignmentFixture.class.getResourceAsStream("/"+name+".class")){new ClassReader(in).accept(node,0);}
   for(var m:node.methods){
    if(name.endsWith("GuiGraphics")){if(m.name.equals("pose")&&m.desc.equals("()Lcom/mojang/blaze3d/vertex/PoseStack;"))body(m,new MethodInsnNode(Opcodes.INVOKESTATIC,SELF,"pose",m.desc,false),new InsnNode(Opcodes.ARETURN));continue;}
    if(name.endsWith("GuiGameElement")){
     if(m.name.equals("of")){
      String factory=m.desc.startsWith("(Lnet/minecraft/world/level/block/state/BlockState;)")?"block":m.desc.startsWith("(Ldev/engine_room/flywheel/lib/model/baked/PartialModel;)")?"model":null;
      if(factory!=null)body(m,new VarInsnNode(Opcodes.ALOAD,0),new MethodInsnNode(Opcodes.INVOKESTATIC,SELF,factory,m.desc,false),new InsnNode(Opcodes.ARETURN));
     }continue;
    }
    if(name.endsWith("AnimatedKinetics")&&m.name.equals("shaft")){body(m,new VarInsnNode(Opcodes.ALOAD,1),new MethodInsnNode(Opcodes.INVOKESTATIC,SELF,"shaft",m.desc,false),new InsnNode(Opcodes.ARETURN));continue;}
    for(var i:m.instructions.toArray()){
     if(i instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC){
      if(f.owner.equals("com/mrh0/createaddition/index/CABlocks")||f.owner.equals("com/simibubi/create/AllBlocks"))m.instructions.set(f,new LdcInsnNode(f.name));
      else if(f.owner.equals("com/simibubi/create/AllPartialModels")){m.instructions.insertBefore(f,new LdcInsnNode(f.name));m.instructions.set(f,new MethodInsnNode(Opcodes.INVOKESTATIC,SELF,"partial","(Ljava/lang/String;)Ldev/engine_room/flywheel/lib/model/baked/PartialModel;",false));}
     }
     if(i instanceof MethodInsnNode call){
      if(call.owner.equals("com/simibubi/create/foundation/gui/AllGuiTextures")&&call.name.equals("render")&&call.desc.equals("(Lnet/minecraft/client/gui/GuiGraphics;II)V"))m.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,SELF,"texture","(Lcom/simibubi/create/foundation/gui/AllGuiTextures;Lnet/minecraft/client/gui/GuiGraphics;II)V",false));
      else if(call.owner.equals("com/tterrag/registrate/util/entry/BlockEntry")&&call.name.equals("getDefaultState"))m.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,SELF,"state","(Ljava/lang/String;)Lnet/minecraft/world/level/block/state/BlockState;",false));
      else if(call.owner.equals("net/createmod/catnip/animation/AnimationTickHolder")&&call.name.equals("getRenderTime")&&call.desc.equals("()F"))m.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,SELF,"time","()F",false));
     }
    }
   }
   var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);var dest=out.resolve(name+".class");Files.createDirectories(dest.getParent());Files.write(dest,writer.toByteArray());
  }
 }
 static sun.misc.Unsafe unsafe;
 static Object allocate(String name)throws Exception{return unsafe.allocateInstance(Class.forName(name));}
 static void put(Object o,Class<?> owner,String name,Object value)throws Exception{var f=owner.getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
 static boolean close(Matrix4f a,Matrix4f b){float[] x=new float[16],y=new float[16];a.get(x);b.get(y);for(int i=0;i<16;i++)if(Math.abs(x[i]-y[i])>.0001f)return false;return true;}
 static Object category(String name,String field,Object animation)throws Exception{Object cat=allocate(name);put(cat,cat.getClass(),field,animation);return cat;}
 static List<String> slots(Object cat,Object recipe)throws Exception{
  var result=new ArrayList<String>();
  var builder=RecipeOutputJeiFixture.proxy(mezz.jei.api.gui.builder.IRecipeLayoutBuilder.class,(p,m,a)->{
   if(m.getName().equals("addSlot")){result.add(a[0]+":"+a[1]+","+a[2]);return RecipeOutputJeiFixture.proxy(mezz.jei.api.gui.builder.IRecipeSlotBuilder.class,(q,n,b)->n.getReturnType().isInstance(q)?q:RecipeOutputJeiFixture.defaultValue(n.getReturnType()));}
   return RecipeOutputJeiFixture.defaultValue(m.getReturnType());
  });
  Arrays.stream(cat.getClass().getMethods()).filter(m->m.getName().equals("setRecipe") && !m.isBridge() && m.getParameterCount()==3 && m.getParameterTypes()[1].isInstance(recipe)).findFirst().orElseThrow().invoke(cat,builder,recipe,null);return result;
 }
 public static void main(String[] args)throws Exception{
  if(args.length>0&&args[0].equals("capture")){capture(Path.of(args[1]));return;}
  MinimalNativeBootstrap.initialize();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
  var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(sun.misc.Unsafe)f.get(null);RecipeOutputJeiFixture.unsafe=unsafe;
  var graphics=(GuiGraphics)unsafe.allocateInstance(GuiGraphics.class);
  var mill=new AnimatedMillstone();var rolling=new com.mrh0.createaddition.compat.jei.AnimatedRollingMill();
  var rollingNoShadow=new com.mrh0.createaddition.compat.jei.AnimatedRollingMill(false);
  List<String> failures=new ArrayList<>();int frames=0;Matrix4f previous=null;
  for(float t:new float[]{0,5,11,19,35,64,89}){
   frame=t;models.clear();textures.clear();pose=new PoseStack();mill.draw(graphics,48,27);
   var reference=models.stream().filter(v->v.name.equals("millstone")).findFirst().orElseThrow().matrix;
   var shadows=List.copyOf(textures);models.clear();textures.clear();rolling.draw(graphics,48,27);
   var machine=models.stream().filter(v->v.name.equals("rolling")).findFirst().orElseThrow().matrix;
   if(!close(reference,machine))failures.add("machine origin differs from native millstone at frame "+t);
   if(!shadows.equals(textures))failures.add("shadow anchor differs");
   var shafts=models.stream().filter(v->v.name.equals("shaft")).toList();if(shafts.size()!=2)throw new AssertionError("two shafts preserved");
   if(previous!=null&&close(previous,shafts.get(0).matrix))throw new AssertionError("roller stopped animating");previous=shafts.get(0).matrix;
   if(!pose.clear())throw new AssertionError("unbalanced pose stack");
   models.clear();textures.clear();rollingNoShadow.draw(graphics,10,30);if(!textures.isEmpty())throw new AssertionError("assembly acquired shadow");frames++;
  }
  Object rollingRecipe=allocate("com.mrh0.createaddition.recipe.rolling.RollingRecipe");
  Object millingRecipe=allocate("com.simibubi.create.content.kinetics.millstone.MillingRecipe");
  for(Object recipe:List.of(rollingRecipe,millingRecipe)){
   put(recipe,com.simibubi.create.content.processing.recipe.ProcessingRecipe.class,"ingredients",net.minecraft.core.NonNullList.of(Ingredient.EMPTY,Ingredient.of(Items.IRON_INGOT)));
   put(recipe,com.simibubi.create.content.processing.recipe.ProcessingRecipe.class,"results",net.minecraft.core.NonNullList.of(new com.simibubi.create.content.processing.recipe.ProcessingOutput(new ItemStack(Items.IRON_NUGGET,2),1),new com.simibubi.create.content.processing.recipe.ProcessingOutput(new ItemStack(Items.IRON_NUGGET,2),1)));
  }
  var rollingSlots=slots(category("com.mrh0.createaddition.compat.jei.RollingMillCategory","rolling_mill",rolling),rollingRecipe);
  // Milling's public overload accepts its AbstractCrushingRecipe superclass.
  var milling=category("com.simibubi.create.compat.jei.category.MillingCategory","millstone",mill);
  var canonical=slots(milling,millingRecipe);
  if(!rollingSlots.equals(canonical))failures.add("rolling slots differ from Create single-output milling: "+rollingSlots);
  System.out.println("Captured "+frames+" native animation frames; rolling slots="+rollingSlots);
  if(!failures.isEmpty())throw new AssertionError(String.join("; ",failures));
  System.out.println("PASS effective machine matrices match Create millstone, shadows align, both rollers animate, assembly stays shadowless, poses balanced, slots aligned");
 }
}
