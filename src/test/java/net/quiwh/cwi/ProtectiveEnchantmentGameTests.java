package net.quiwh.cwi;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.equipment.EquipmentUpgradeRecipe;
import net.quiwh.cwi.equipment.UpgradeIngredient;
import net.quiwh.cwi.interaction.InteractionRecipe;
import net.quiwh.cwi.interaction.InteractionRecipes;
import net.quiwh.cwi.interaction.WorldHazards;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class ProtectiveEnchantmentGameTests {
    static final List<String> IDS=List.of("chemical_sealing","heat_shielding","cold_insulation","electrical_insulation",
            "sure_footing","swimming_efficiency","particulate_filtration","gas_filtration","radiation_shielding",
            "pressure_protection","ocular_shielding");
    static net.minecraft.core.Holder<Enchantment> enchantment(GameTestHelper h,String name) {
        return h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,ResourceLocation.parse("cwi:"+name)));
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void allElevenAreNativeWithoutEquipmentTagRequirements(GameTestHelper h) {
        for(String id:IDS){
            var e=enchantment(h,id);
            h.assertTrue(e.value().getMaxLevel()==4,"Core enchantment has four levels: "+id);
            for(String other:IDS)if(!id.equals(other))h.assertTrue(Enchantment.areCompatible(e,enchantment(h,other)),"protective upgrades coexist");
        }
        h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void upgradeRecipesAloneDetermineProtectiveEquipment(GameTestHelper h) {
        var e=enchantment(h,"chemical_sealing");
        var recipe=new EquipmentUpgradeRecipe(Ingredient.of(Items.DIAMOND_BOOTS,Items.IRON_BOOTS),ItemStack.EMPTY,
                List.of(new UpgradeIngredient(Ingredient.of(Items.FLINT),1,true,0)),e,4,.01);
        var bad=CraftingInput.of(2,1,List.of(new ItemStack(Items.DIAMOND_BOOTS),new ItemStack(Items.FLINT)));
        h.assertTrue(recipe.matches(bad,h.getLevel())&&recipe.assemble(bad,h.getLevel().registryAccess()).getEnchantmentLevel(e)==1,
                "explicit recipe accepts diamond boots without a second tag whitelist");
        var unrelated=CraftingInput.of(2,1,List.of(new ItemStack(Items.GOLDEN_BOOTS),new ItemStack(Items.FLINT)));
        h.assertTrue(!recipe.matches(unrelated,h.getLevel()),"recipe input alone rejects unrelated equipment");
        var good=CraftingInput.of(2,1,List.of(new ItemStack(Items.IRON_BOOTS),new ItemStack(Items.FLINT)));
        var upgraded=recipe.assemble(good,h.getLevel().registryAccess());
        h.assertTrue(recipe.matches(good,h.getLevel())&&upgraded.getEnchantmentLevel(e)==1,"native upgrade applies to every declared input");
        var changed=new EquipmentUpgradeRecipe(Ingredient.of(Items.IRON_BOOTS),new ItemStack(Items.DIAMOND_BOOTS),recipe.ingredients(),e,4,.01);
        h.assertTrue(changed.matches(good,h.getLevel()) && changed.assemble(good,h.getLevel().registryAccess()).is(Items.DIAMOND_BOOTS),
                "recipe-selected alternate output needs no additional item tag");
        h.assertTrue(recipe.getResultItem(h.getLevel().registryAccess()).is(Items.DIAMOND_BOOTS),
                "result preview follows the recipe's first equipment candidate");
        var custom=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("kubejs:golden_hammer"));
        h.assertTrue(custom!=Items.AIR,"test KubeJS hammer exists");
        var toolRecipe=new EquipmentUpgradeRecipe(Ingredient.of(custom),ItemStack.EMPTY,recipe.ingredients(),e,4,.01);
        var toolInput=CraftingInput.of(2,1,List.of(new ItemStack(custom),new ItemStack(Items.FLINT)));
        h.assertTrue(toolRecipe.matches(toolInput,h.getLevel()) && toolRecipe.assemble(toolInput,h.getLevel().registryAccess()).getEnchantmentLevel(e)==1,
                "KubeJS-created items use recipe eligibility without armor/item tags");
        var capped=new EquipmentUpgradeRecipe(recipe.input(),ItemStack.EMPTY,recipe.ingredients(),e,20,.01);
        var maximum=new ItemStack(Items.IRON_BOOTS); maximum.enchant(e,4);
        h.assertTrue(capped.maxLevel()==4 && !capped.matches(CraftingInput.of(2,1,List.of(maximum,new ItemStack(Items.FLINT))),h.getLevel()),
                "new Core upgrades respect native IV even if a recipe requests a higher cap");
        h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void chemicalHazardScalesEffectsAndFinalPulseBreaksBoots(GameTestHelper h) {
        var manager=h.getLevel().getRecipeManager();var saved=List.copyOf(manager.getRecipes());
        try{
            var json=JsonParser.parseString("""
                {"type":"cwi:fluid_hazard","fluid":{"fluid":"minecraft:water"},"damage":8,"interval":1,
                 "fire_ticks":40,"effects":[{"id":"minecraft:slowness","duration":40}],
                 "protection":"cwi:acid","required_resistance":4,"durability_cost":1,"protection_slots":["feet"]}
                """).getAsJsonObject();
            var recipe=(InteractionRecipe)Recipe.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),json).getOrThrow();
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("cwi:contract/acid"),recipe)));InteractionRecipes.invalidate();
            var pig=new ContactPig(h.getLevel());var boots=new ItemStack(Items.IRON_BOOTS);boots.enchant(enchantment(h,"chemical_sealing"),2);
            pig.setItemSlot(EquipmentSlot.FEET,boots);WorldHazards.tick(pig,0);
            h.assertTrue(pig.damage==4&&pig.getRemainingFireTicks()==20&&pig.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN).getDuration()==20,"half resistance scales damage, new ignition and effects");
            h.assertTrue(boots.getDamageValue()==1,"one wear charge per exposure pulse");
            boots.enchant(enchantment(h,"chemical_sealing"),4);boots.setDamageValue(boots.getMaxDamage()-1);
            pig.damage=0;pig.setRemainingFireTicks(0);pig.removeAllEffects();WorldHazards.tick(pig,1);
            h.assertTrue(pig.damage==0&&pig.getRemainingFireTicks()==0&&!pig.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"breaking equipment protects final pulse fully");
            h.assertTrue(pig.getItemBySlot(EquipmentSlot.FEET).isEmpty(),"normal durability breakage removes boots");
        }finally{manager.replaceRecipes(saved);InteractionRecipes.invalidate();}
        h.succeed();
    }
    public static List<String> verifyScripts(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        var chemical = (InteractionRecipe) manager.byKey(ResourceLocation.parse("cwi:example/chemical_protection")).orElseThrow().value();
        var heat = (InteractionRecipe) manager.byKey(ResourceLocation.parse("cwi:example/heat_protection")).orElseThrow().value();
        var a = ((net.quiwh.cwi.interaction.InteractionRules.FluidHazard) chemical.rule()).protection();
        var b = ((net.quiwh.cwi.interaction.InteractionRules.InventoryHazard) heat.rule()).protection();
        h.assertTrue(a.type() == net.quiwh.cwi.equipment.protection.ProtectiveEnchantments.Type.CHEMICAL_SEALING
                && a.required() == 4 && a.durability() == 1 && a.slots().equals(List.of(EquipmentSlot.FEET)), "chemical builder fields reach native rules");
        h.assertTrue(b.type() == net.quiwh.cwi.equipment.protection.ProtectiveEnchantments.Type.HEAT_SHIELDING
                && b.required() == 4 && b.durability() == 2 && b.slots().equals(List.of(EquipmentSlot.CHEST)), "heat builder fields reach native rules");
        var upgrade = (EquipmentUpgradeRecipe) manager.byKey(ResourceLocation.parse("cwi:example/chemical_sealing_manual_only")).orElseThrow().value();
        h.assertTrue(upgrade.enchantment().is(enchantment(h, "chemical_sealing").unwrapKey().orElseThrow()), "script upgrade resolves native Core enchantment");
        return List.of(chemical.definition().toString(), heat.definition().toString());
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void protectedInventoryHazardKeepsExistingFireAndEffects(GameTestHelper h) {
        var manager=h.getLevel().getRecipeManager();var saved=List.copyOf(manager.getRecipes());
        try {
            var json=JsonParser.parseString("""
                {"type":"cwi:inventory_hazard","input":{"item":"minecraft:blaze_powder"},"damage":8,"interval":1,
                 "fire_ticks":40,"effects":[{"id":"minecraft:slowness","duration":40}],
                 "protection":"cwi:heat","required_resistance":4,"durability_cost":1,"protection_slots":["chest"]}
                """).getAsJsonObject();
            var recipe=(InteractionRecipe)Recipe.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),json).getOrThrow();
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("cwi:contract/heat"),recipe))); InteractionRecipes.invalidate();
            var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var chest=new ItemStack(Items.IRON_CHESTPLATE);chest.enchant(enchantment(h,"heat_shielding"),4);
            player.setItemSlot(EquipmentSlot.CHEST,chest);player.getInventory().setItem(0,new ItemStack(Items.BLAZE_POWDER));
            player.setRemainingFireTicks(100);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,100,0));
            WorldHazards.tick(player,0);
            h.assertTrue(player.getHealth()==20&&player.getRemainingFireTicks()==100
                    &&player.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN).getDuration()==100,
                    "full protection blocks only new exposure, preserving existing fire/effects");
            h.assertTrue(chest.getDamageValue()==1,"inventory exposure applies configured wear");
        } finally {manager.replaceRecipes(saved);InteractionRecipes.invalidate();}
        h.succeed();
    }
    static final class ContactPig extends Pig {
        float damage;
        ContactPig(Level level){super(EntityType.PIG,level);setFluidTypeHeight(Fluids.WATER.getFluidType(),.2);}
        @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){damage+=amount;return true;}
    }
}
