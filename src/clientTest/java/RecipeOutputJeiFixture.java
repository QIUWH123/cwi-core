import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import java.lang.reflect.*;
import java.util.*;
import mezz.jei.api.gui.builder.*;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/** Real transformed JEI callbacks; records visible slot geometry without a graphics context. */
public final class RecipeOutputJeiFixture {
    record Slot(int x, int y, RecipeIngredientRole role) { }
    static final List<Slot> slots = new ArrayList<>();
    static sun.misc.Unsafe unsafe;
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p,m,a) -> {
            if (m.getDeclaringClass()==Object.class) return switch(m.getName()) {
                case "hashCode" -> System.identityHashCode(p); case "equals" -> p==a[0]; default -> "layout fixture";
            };
            return handler.invoke(p,m,a);
        });
    }
    static Object defaultValue(Class<?> type) { return type==boolean.class ? false : type==int.class ? 0 : null; }
    static void put(Object object, Class<?> owner, String name, Object value) throws Exception {
        var field=owner.getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    static Object allocate(String name) throws Exception { return unsafe.allocateInstance(Class.forName(name)); }
    public static void main(String[] args) throws Exception {
        MinimalNativeBootstrap.initialize();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);unsafe=(sun.misc.Unsafe)field.get(null);
        List<String> failures=new ArrayList<>();
        for (int count=1; count<=9; count++) {
            try { verify("com.negodya1.vintageimprovements.compat.jei.category.VibratingCategory",
                    "com.negodya1.vintageimprovements.content.kinetics.vibration.VibratingRecipe",count,0,70); }
            catch (AssertionError e) {failures.add(e.getMessage());}
            for (int fluids=0;fluids<=2;fluids++) {
                try { verify("com.negodya1.vintageimprovements.compat.jei.category.CentrifugationCategory",
                        "com.negodya1.vintageimprovements.content.kinetics.centrifuge.CentrifugationRecipe",count,fluids,113); }
                catch (AssertionError e) {failures.add(e.getMessage());}
            }
            if (count<=6) {
                try { verify("io.github.hadron13.rubberworks.compat.jei.category.CompressingCategory",
                        "io.github.hadron13.rubberworks.blocks.compressor.CompressingRecipe",count,0,75); }
                catch (AssertionError e) {failures.add(e.getMessage());}
            }
        }
        if (!failures.isEmpty()) throw new AssertionError(String.join("\n",failures));
        System.out.println("RecipeOutputJeiFixture PASS: 42 native layouts retain every result within category bounds, without output overlaps");
    }
    static void verify(String categoryName,String recipeName,int count,int fluids,int height) throws Exception {
        Object recipe=allocate(recipeName);
        var outputs=NonNullList.<ProcessingOutput>create();
        for (int i=0;i<count;i++) outputs.add(new ProcessingOutput(new ItemStack(Items.IRON_INGOT),i==count-1?0.25f:1f));
        put(recipe,ProcessingRecipe.class,"results",outputs);
        put(recipe,ProcessingRecipe.class,"ingredients",NonNullList.of(Ingredient.EMPTY,Ingredient.of(Items.PAPER)));
        put(recipe,ProcessingRecipe.class,"fluidIngredients",NonNullList.of(SizedFluidIngredient.of(Fluids.WATER,100),SizedFluidIngredient.of(Fluids.WATER,100)));
        var fluidResults=NonNullList.<FluidStack>create();
        for (int i=0;i<fluids;i++) fluidResults.add(new FluidStack(Fluids.WATER,100));
        put(recipe,ProcessingRecipe.class,"fluidResults",fluidResults);
        Object category=allocate(categoryName);
        put(category,Class.forName("com.simibubi.create.compat.jei.category.CreateRecipeCategory"),"background",
                proxy(IDrawable.class,(p,m,a)->m.getName().equals("getWidth")?177:m.getName().equals("getHeight")?height:defaultValue(m.getReturnType())));
        slots.clear();
        var builder=proxy(IRecipeLayoutBuilder.class,(p,m,a)-> {
            if (m.getName().equals("addSlot")) {
                var slot=new Slot((int)a[1],(int)a[2],(RecipeIngredientRole)a[0]);slots.add(slot);
                return proxy(IRecipeSlotBuilder.class,(q,n,b)->n.getReturnType().isInstance(q)?q:defaultValue(n.getReturnType()));
            }
            return defaultValue(m.getReturnType());
        });
        category.getClass().getMethod("setRecipe",IRecipeLayoutBuilder.class,recipe.getClass(),IFocusGroup.class)
                .invoke(category,builder,recipe,null);
        var outputsAt=slots.stream().filter(s->s.role==RecipeIngredientRole.OUTPUT).toList();
        if(outputsAt.size()!=count+fluids) throw new AssertionError(categoryName+" omitted an output");
        for(int i=0;i<outputsAt.size();i++) {
            var slot=outputsAt.get(i);
            if(slot.x<1 || slot.x+17>177 || slot.y<1 || slot.y+17>height)
                throw new AssertionError(categoryName+" count="+count+" fluids="+fluids+" output outside bounds: "+slot);
            for(int j=0;j<i;j++) {
                var other=outputsAt.get(j);
                if(Math.abs(slot.x-other.x)<18 && Math.abs(slot.y-other.y)<18)
                    throw new AssertionError(categoryName+" overlaps output slots");
            }
        }
    }
}
