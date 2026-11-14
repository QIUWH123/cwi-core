package net.quiwh.cwi.compat.jei;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import java.util.*;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.quiwh.cwi.interaction.application.*;

/** Native Create layout and chance frames, with searchable conditional outputs. */
public final class ApplicationJei {
    public record Preview(ItemStack stack, float chance, List<Component> conditions, boolean block) {}
    public static List<Preview> outputs(ItemApplicationRecipe recipe) {
        var o = ((ApplicationExtension)recipe).cwi$interaction();
        var result = new ArrayList<Preview>();
        for (int i=0;i<recipe.getRollableResults().size();i++) {
            var entry=recipe.getRollableResults().get(i);
            if(i==0 && entry.getStack().getItem() instanceof BlockItem && !o.blockMode.equals("replace")) continue;
            result.add(new Preview(entry.getStack(),entry.getChance(),List.of(),i==0 && entry.getStack().getItem() instanceof BlockItem));
        }
        for(var drop:o.drops) result.add(new Preview(drop.stack(),drop.chance(),List.of(),false));
        actions(o.actions,1,List.of(),result);
        return result;
    }
    private static void actions(List<ApplicationOptions.Action> actions,float chance,List<Component> conditions,List<Preview> outputs) {
        for(var action:actions) {
            if(action instanceof ApplicationOptions.DropAction d) outputs.add(new Preview(d.drop().stack(),chance*d.drop().chance(),conditions,false));
            if(action instanceof ApplicationOptions.Place p && !p.state().isAir()) outputs.add(new Preview(new ItemStack(p.state().getBlock()),chance,conditions,true));
            if(action instanceof ApplicationOptions.Conditional c) {
                var yes=new ArrayList<>(conditions);
                if(c.sneaking()!=null) yes.add(Component.translatable(c.sneaking()?"cwi.jei.application.sneaking":"cwi.jei.application.not_sneaking").withStyle(ChatFormatting.LIGHT_PURPLE));
                actions(c.thenActions(),chance*c.chance(),yes,outputs);
                var no=new ArrayList<>(conditions);
                if(c.sneaking()!=null) no.add(Component.translatable("cwi.jei.application.otherwise").withStyle(ChatFormatting.LIGHT_PURPLE));
                actions(c.elseActions(),c.sneaking()==null?chance*(1-c.chance()):chance,no,outputs);
            }
        }
    }
    public static void layout(IRecipeLayoutBuilder builder,ItemApplicationRecipe recipe) {
        var o=((ApplicationExtension)recipe).cwi$interaction();
        var input=builder.addSlot(RecipeIngredientRole.INPUT,27,38).setSlotName("cwi_application_block")
                .setBackground(CreateRecipeCategory.getRenderedSlot(),-1,-1);
        if(o.block!=null) input.addItemStacks(o.block.items()); else input.addIngredients(recipe.getProcessedItem());
        input.addRichTooltipCallback((view,tips)->{
            if(!o.state.isEmpty()) tips.add(Component.translatable("cwi.jei.application.state",o.state.toString()).withStyle(ChatFormatting.GRAY));
            if(o.blockMode.equals("keep") || o.blockMode.equals("remove")) tips.add(Component.translatable("cwi.jei.application."+o.blockMode).withStyle(ChatFormatting.GOLD));
        });
        var held=builder.addSlot(RecipeIngredientRole.INPUT,51,5).setSlotName("cwi_application_held")
                .setBackground(CreateRecipeCategory.getRenderedSlot(),-1,-1);
        if(!o.emptyHand) held.addIngredients(recipe.getRequiredHeldItem());
        held.addRichTooltipCallback((view,tips)->heldTooltips(recipe).forEach(tips::add));
        var previews=outputs(recipe);
        for(int i=0;i<previews.size();i++) {
            var p=previews.get(i);
            var slot=(previews.size()>6?builder.addSlot(RecipeIngredientRole.OUTPUT):builder.addSlot(RecipeIngredientRole.OUTPUT,132+i%2*19,38-i/2*19))
                    .setSlotName("cwi_application_output_"+i).setBackground(CreateRecipeCategory.getRenderedSlot(p.chance()),-1,-1).addItemStack(p.stack());
            slot.addRichTooltipCallback((view,tips)->{
                if(p.chance()!=1) tips.add(ProcessingChance.tooltip(p.chance()));
                if(p.block()) tips.add(Component.translatable("cwi.jei.application.block_result").withStyle(ChatFormatting.AQUA));
                else tips.add(Component.translatable("cwi.jei.application.whole_stack").withStyle(ChatFormatting.AQUA));
                p.conditions().forEach(tips::add);
            });
        }
    }
    public static void extras(IRecipeExtrasBuilder builder,ItemApplicationRecipe recipe) {
        if(outputs(recipe).size()>6) builder.addScrollGridWidget(builder.getRecipeSlots().getSlots(RecipeIngredientRole.OUTPUT),2,3).setPosition(132,1);
        if (((ApplicationExtension)recipe).cwi$interaction().emptyHand) {
            // Empty JEI ingredient slots have no ingredient tooltip to decorate.
            builder.addTooltipArea(50,4,18,18).setTooltip(heldTooltips(recipe));
        }
    }
    private static List<Component> heldTooltips(ItemApplicationRecipe recipe) {
        var o=((ApplicationExtension)recipe).cwi$interaction();
        var tips=new ArrayList<Component>();
        if(o.emptyHand) tips.add(Component.translatable("cwi.jei.application.empty_hand").withStyle(ChatFormatting.GOLD));
        if(o.sneaking!=null) tips.add(Component.translatable(o.sneaking?"cwi.jei.application.sneaking":"cwi.jei.application.not_sneaking").withStyle(ChatFormatting.LIGHT_PURPLE));
        if(o.cooldown>0) tips.add(Component.translatable("cwi.jei.application.cooldown",o.cooldown).withStyle(ChatFormatting.AQUA));
        if(o.damageHeld!=null) tips.add(Component.translatable("cwi.jei.application.tool_damage",o.damageHeld).withStyle(ChatFormatting.GOLD));
        else if(o.consumeHeld!=null) tips.add(Component.translatable("cwi.jei.application.consume",o.consumeHeld).withStyle(ChatFormatting.GOLD));
        else if(recipe.shouldKeepHeldItem()) tips.add(Component.translatable("create.recipe.deploying.not_consumed").withStyle(ChatFormatting.GOLD));
        if(o.damagePlayer>0) tips.add(Component.translatable("cwi.jei.application.player_damage",o.damagePlayer).withStyle(ChatFormatting.RED));
        return tips;
    }
    private ApplicationJei() {}
}
