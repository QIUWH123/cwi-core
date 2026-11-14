package net.quiwh.cwi.compat.jei;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import java.util.Arrays;
import mezz.jei.api.gui.builder.*;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.registries.BuiltInRegistries;
import net.quiwh.cwi.compat.processing.*;
import net.quiwh.cwi.machine.recipe.MachineRecipes;

/** Native machine art/animation stays in its category; typed IO shares Core's frames and tooltips. */
public final class NativeProcessingJei {
    private record Grid(int x,int y,int columns,int rows,int dx,int dy,boolean centerLast) {
        Grid(int x,int y,int columns,int rows,boolean upwards){this(x,y,columns,rows,19,upwards?-19:19,false);}
        int capacity(){return columns*rows;}
        IRecipeSlotBuilder slot(IRecipeLayoutBuilder builder,RecipeIngredientRole role,int index,int count){
            if(count>capacity())return builder.addSlot(role);
            int offset=centerLast&&count%2==1&&index==count-1?10:index%columns*dx;
            return builder.addSlot(role,x+offset,y+index/columns*dy);
        }
        void extras(IRecipeExtrasBuilder builder,RecipeIngredientRole role,int count){extras(builder,role,count,"");}
        void extras(IRecipeExtrasBuilder builder,RecipeIngredientRole role,int count,String prefix){
            if(count>capacity())builder.addScrollGridWidget(builder.getRecipeSlots().getSlots(role).stream()
                    .filter(slot->prefix.isEmpty()||slot.getSlotName().orElse("").startsWith(prefix)).toList(),columns,rows)
                    .setPosition(x,dy<0?y+(rows-1)*dy:y);
        }
    }
    private static String id(ProcessingRecipe<?,?> recipe){return BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString();}
    public static boolean handles(ProcessingRecipe<?,?> recipe){
        String id=id(recipe);return NativeRecipeTypes.supports(id)&&(AdvancedProcessing.execution(recipe)
                ||id.equals("rubberworks:compressing")&&!recipe.getFluidResults().isEmpty());
    }
    public static int outputCount(ProcessingRecipe<?,?> recipe){
        var data=AdvancedProcessing.get(recipe);return data.results().size()+data.pools().size();
    }
    public static int itemInputs(ProcessingRecipe<?,?> recipe){return (int)AdvancedProcessing.get(recipe).ingredients().stream().filter(e->e.right().isPresent()).count();}
    public static int itemOutputs(ProcessingRecipe<?,?> recipe){var data=AdvancedProcessing.get(recipe);return (int)data.results().stream().filter(e->e.right().isPresent()).count()+data.pools().size();}
    private static Grid input(String id,int count,int outputs){return switch(id){
        case "create:milling","createaddition:rolling","vintageimprovements:vibrating" -> new Grid(15,9,1,1,false);
        case "create:crushing" -> new Grid(51,3,1,1,false);
        case "create:cutting" -> new Grid(44,5,1,1,false);
        case "create:pressing","create:deploying","create:filling" -> new Grid(27,51,1,1,false);
        case "create:emptying" -> new Grid(27,8,1,1,false);
        case "rubberworks:compressing" -> new Grid(35,23,1,2,true);
        case "vintageimprovements:centrifugation" -> new Grid(10,5,3,3,false);
        case "createdieselgenerators:distillation" -> new Grid(17,145,1,1,false);
        case "tfmg:distillation" -> new Grid(18,70+outputs*10,1,1,false);
        default -> new Grid(17+(count<3?(3-count)*19/2:0),51,3,3,true);
    };}
    private static Grid output(String id,int count,int width){return switch(id){
        case "create:milling","createaddition:rolling" -> new Grid(count==1?139:133,27,2,2,true);
        case "create:crushing" -> crushingGrid(count,width);
        case "create:cutting" -> new Grid(118,48,2,3,true);
        case "create:pressing" -> new Grid(131,50,2,2,false);
        case "create:deploying","create:filling" -> new Grid(132,51,2,3,true);
        case "create:emptying" -> new Grid(132,27,2,3,false);
        case "rubberworks:compressing" -> count<=2?new Grid(132,23,2,1,19,19,true):new Grid(114,count<=3?23:4,3,2,false);
        case "vintageimprovements:vibrating" -> new Grid(104,count>6?6:25-(count/3-1)*19/2,3,3,false);
        case "vintageimprovements:centrifugation" -> count<=4?new Grid(128,56,2,2,false):new Grid(112,94-((Math.min(count,9)+2)/3)*19,3,3,false);
        case "createdieselgenerators:distillation" -> new Grid(130,127,1,6,19,-23,false);
        case "tfmg:distillation" -> new Grid(105,63+count*10,1,6,19,-24,false);
        default -> new Grid(132,51,2,3,19,-19,true);
    };}
    private static Grid crushingGrid(int count,int width){
        var nativeLayout=net.createmod.catnip.layout.LayoutHelper.centeredHorizontal(Math.min(count,9),1,18,18,1);
        return new Grid(width/2+nativeLayout.getX()+1,87+nativeLayout.getY(),9,1,false);
    }
    private static boolean vat(String id){return id.equals("tfmg:vat")||id.equals("tfmg:vat_machine_recipe");}
    private static Grid vatGrid(boolean input,boolean fluid,int count,int other,boolean expanded){
        if(expanded)return input?new Grid(19,fluid?85:43,4,fluid?1:2,21,21,false)
                :new Grid(fluid?153:110,43,fluid?1:2,4,21,21,false);
        if(!input)return new Grid(fluid?150:128,106,1,4,21,-21,false);
        int movement=count==1?2:count==4?0:1;
        return new Grid(55-count*10+movement,other==0?72:fluid?85:64,4,1,21,21,false);
    }
    public static void layout(IRecipeLayoutBuilder builder,ProcessingRecipe<?,?> recipe,int width){
        var data=AdvancedProcessing.get(recipe);String id=id(recipe);var in=input(id,data.ingredients().size(),outputCount(recipe));var out=output(id,outputCount(recipe),width);
        boolean vat=vat(id),expanded=vat&&(itemInputs(recipe)>4||itemOutputs(recipe)>4);
        int inItems=itemInputs(recipe),inFluids=data.ingredients().size()-inItems,outItems=itemOutputs(recipe),outFluids=data.results().size()+data.pools().size()-outItems;
        int itemIndex=0,fluidIndex=0;
        int count=data.ingredients().size(),index=0;
        for(var entry:data.ingredients()){
            IRecipeSlotBuilder slot;
            if(vat){
                boolean fluid=entry.left().isPresent();int n=fluid?fluidIndex++:itemIndex++;
                slot=vatGrid(true,fluid,fluid?inFluids:inItems,fluid?inItems:inFluids,expanded)
                        .slot(builder,RecipeIngredientRole.INPUT,n,fluid?inFluids:inItems);
            }
            else if(id.equals("create:deploying")&&index==1)slot=builder.addSlot(RecipeIngredientRole.INPUT,51,5);
            else if(id.equals("create:filling")&&entry.left().isPresent())slot=builder.addSlot(RecipeIngredientRole.INPUT,27,8);
            else if(id.equals("create:filling"))slot=builder.addSlot(RecipeIngredientRole.INPUT,27,51);
            else slot=in.slot(builder,RecipeIngredientRole.INPUT,index,NativeRecipeTypes.ACTIONS.contains(id)?1:count);
            slot.setBackground(CreateRecipeCategory.getRenderedSlot(),-1,-1);final int number=index++;
            String name=vat?(entry.left().isPresent()?"inFluid":"inItem")+number:"in"+number;
            entry.ifLeft(v->{
                slot.setFluidRenderer(v.ingredient().amount(),false,16,16);
                for(var fluid:v.ingredient().getFluids())slot.addFluidStack(fluid.getFluid(),fluid.getAmount(),fluid.getComponentsPatch());
                MachineJeiSlots.decorate(MachineRecipes.MIXING,slot,name,v.chance(),v.settings(),true,v.ingredient().amount(),true);
            }).ifRight(v->{
                slot.addItemStacks(Arrays.stream(v.ingredient().getItems()).map(s->s.copyWithCount(v.count())).toList());
                MachineJeiSlots.decorate(MachineRecipes.MIXING,slot,name,v.chance(),v.settings(),true,v.count(),false);
            });
        }
        count=data.results().size()+data.pools().size();index=0;itemIndex=0;fluidIndex=0;
        for(var entry:data.results()){
            IRecipeSlotBuilder slot;
            boolean fluid=entry.left().isPresent();
            if(vat)slot=vatGrid(false,fluid,fluid?outFluids:outItems,0,expanded)
                    .slot(builder,RecipeIngredientRole.OUTPUT,fluid?fluidIndex++:itemIndex++,fluid?outFluids:outItems);
            else if(id.equals("create:emptying"))slot=fluid?builder.addSlot(RecipeIngredientRole.OUTPUT,132,8)
                    :out.slot(builder,RecipeIngredientRole.OUTPUT,itemIndex++,outItems);
            else slot=out.slot(builder,RecipeIngredientRole.OUTPUT,index,count);
            slot.setBackground(CreateRecipeCategory.getRenderedSlot(),-1,-1);final int number=index++;
            String name=vat?(fluid?"outFluid":"outItem")+number:fluid&&id.equals("create:emptying")?"fluidOutput":"out"+number;
            entry.ifLeft(v->{
                var stack=v.stack();slot.setFluidRenderer(stack.getAmount(),false,16,16).addFluidStack(stack.getFluid(),stack.getAmount(),stack.getComponentsPatch());
                MachineJeiSlots.decorate(MachineRecipes.MIXING,slot,name,v.chance(),v.settings(),false,stack.getAmount(),true);
            }).ifRight(v->{slot.addItemStack(v.stack());MachineJeiSlots.decorate(MachineRecipes.MIXING,slot,name,v.chance(),v.settings(),false,v.stack().getCount(),false);});
        }
        for(int pool=0;pool<data.pools().size();pool++){
            var slot=vat?vatGrid(false,false,outItems,0,expanded).slot(builder,RecipeIngredientRole.OUTPUT,itemIndex++,outItems)
                    :out.slot(builder,RecipeIngredientRole.OUTPUT,id.equals("create:emptying")?itemIndex++:index++,id.equals("create:emptying")?outItems:count);
            MachineJeiSlots.pool(slot.setSlotName(vat?"outItemPool"+pool:id.equals("create:emptying")?"outPool"+pool:"pool"+pool)
                    .setBackground(CreateRecipeCategory.getRenderedSlot(0f),-1,-1),data.pools().get(pool));
        }
        if(NativeRecipeTypes.BASIN.contains(id)||id.equals("createdieselgenerators:distillation")){
            int y=id.equals("createdieselgenerators:distillation")?171:81;
            if(!recipe.getRequiredHeat().testBlazeBurner(HeatLevel.NONE))builder.addSlot(RecipeIngredientRole.RENDER_ONLY,134,y).addItemStack(AllBlocks.BLAZE_BURNER.asStack());
            if(!recipe.getRequiredHeat().testBlazeBurner(HeatLevel.KINDLED))builder.addSlot(RecipeIngredientRole.CATALYST,153,y).addItemStack(AllItems.BLAZE_CAKE.asStack());
        }
    }
    public static void extras(IRecipeExtrasBuilder builder,ProcessingRecipe<?,?> recipe,int width){
        var data=AdvancedProcessing.get(recipe);String id=id(recipe);
        if(vat(id)){
            int inItems=itemInputs(recipe),inFluids=data.ingredients().size()-inItems,outItems=itemOutputs(recipe),outFluids=outputCount(recipe)-outItems;
            boolean expanded=inItems>4||outItems>4;
            vatGrid(true,false,inItems,inFluids,expanded).extras(builder,RecipeIngredientRole.INPUT,inItems,"inItem");
            vatGrid(true,true,inFluids,inItems,expanded).extras(builder,RecipeIngredientRole.INPUT,inFluids,"inFluid");
            vatGrid(false,false,outItems,0,expanded).extras(builder,RecipeIngredientRole.OUTPUT,outItems,"outItem");
            vatGrid(false,true,outFluids,0,expanded).extras(builder,RecipeIngredientRole.OUTPUT,outFluids,"outFluid");
            return;
        }
        // Action input positions are fixed native hand/spout positions and never overflow.
        int outputs=outputCount(recipe);
        if(!NativeRecipeTypes.ACTIONS.contains(id))input(id,data.ingredients().size(),outputs).extras(builder,RecipeIngredientRole.INPUT,data.ingredients().size());
        if(id.equals("create:emptying"))output(id,itemOutputs(recipe),width).extras(builder,RecipeIngredientRole.OUTPUT,itemOutputs(recipe),"out");
        else output(id,outputs,width).extras(builder,RecipeIngredientRole.OUTPUT,outputs);
    }
    private NativeProcessingJei(){}
}
