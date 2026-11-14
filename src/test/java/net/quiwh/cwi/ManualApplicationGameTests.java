package net.quiwh.cwi;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class ManualApplicationGameTests {
    @SuppressWarnings("unchecked")
    static ManualApplicationRecipe recipe(GameTestHelper h, String extra, String results) {
        var json = JsonParser.parseString("""
                {"ingredients":[{"item":"minecraft:iron_block"},{"item":"minecraft:stick"}],
                 "results":%s,"cwi_interaction":%s}
                """.formatted(results, extra));
        var codec = (com.mojang.serialization.MapCodec<ManualApplicationRecipe>) AllRecipeTypes.ITEM_APPLICATION.getSerializer().codec();
        return codec.codec().parse(h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void manualMetadataSurvivesNativeCodec(GameTestHelper h) {
        var recipe = recipe(h, "{\"empty_hand\":true,\"sneaking\":true,\"block_mode\":\"keep\"}", "[]");
        @SuppressWarnings("unchecked")
        var codec = (com.mojang.serialization.MapCodec<ManualApplicationRecipe>) AllRecipeTypes.ITEM_APPLICATION.getSerializer().codec();
        var encoded = codec.codec().encodeStart(h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), recipe).getOrThrow();
        h.assertTrue(encoded.getAsJsonObject().has("cwi_interaction"), "native recipe codec must retain manual interaction rules");
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void emptyHandCanTransformOnlyWhileSneaking(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        var saved = List.copyOf(manager.getRecipes());
        var recipe = recipe(h, "{\"empty_hand\":true,\"sneaking\":true}", "[{\"id\":\"minecraft:gold_block\"}]");
        var recipes = new ArrayList<RecipeHolder<?>>(saved);
        recipes.add(new RecipeHolder<>(ResourceLocation.parse("cwi:manual_contract/empty"), recipe));
        var pos = h.absolutePos(new BlockPos(1, 1, 1));
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        try {
            manager.replaceRecipes(recipes);
            h.getLevel().setBlockAndUpdate(pos, Blocks.IRON_BLOCK.defaultBlockState());
            ManualApplicationRecipe.manualApplicationRecipesApplyInWorld(click(player, pos, InteractionHand.MAIN_HAND));
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.IRON_BLOCK), "standing player must not match sneak requirement");
            player.setShiftKeyDown(true);
            var event = click(player, pos, InteractionHand.MAIN_HAND);
            ManualApplicationRecipe.manualApplicationRecipesApplyInWorld(event);
            h.assertTrue(event.isCanceled() && h.getLevel().getBlockState(pos).is(Blocks.GOLD_BLOCK),
                    "sneaking with empty hand must apply the manual recipe exactly once");
        } finally { manager.replaceRecipes(saved); }
        h.succeed();
    }

    static PlayerInteractEvent.RightClickBlock click(net.minecraft.world.entity.player.Player player, BlockPos pos, InteractionHand hand) {
        return new PlayerInteractEvent.RightClickBlock(player, hand, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void failedBlockChanceRetainsBlockAndWholeStackDrops(GameTestHelper h) {
        var r=recipe(h,"{\"drops\":[{\"stack\":{\"id\":\"minecraft:flint\",\"count\":4},\"chance\":0.5}]}",
                "[{\"id\":\"minecraft:gold_block\",\"chance\":0.000001}]");
        withRecipe(h,r,(player,pos)->{
            player.setItemInHand(InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK,64));
            h.getLevel().random.setSeed(12345);
            int success=0,failed=0;
            var area=new net.minecraft.world.phys.AABB(pos).inflate(2);
            for(int i=0;i<30;i++) {
                var event=click(player,pos,InteractionHand.MAIN_HAND);
                ManualApplicationRecipe.manualApplicationRecipesApplyInWorld(event);
                h.assertTrue(event.isCanceled() && h.getLevel().getBlockState(pos).is(Blocks.IRON_BLOCK),"failed transformation keeps the original block");
                var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area);
                if(drops.isEmpty()) failed++; else {
                    success++;
                    h.assertTrue(drops.size()==1 && drops.getFirst().getItem().getCount()==4,"chance drops the whole stack, never 1 to 3 items");
                    drops.forEach(net.minecraft.world.entity.Entity::discard);
                }
            }
            h.assertTrue(success>0 && failed>0,"test seed exercises successful and unsuccessful chance branches");
        });
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void costsCooldownOffhandAndCreativeAreRespected(GameTestHelper h) {
        var r=recipe(h,"{\"block_mode\":\"keep\",\"damage_held\":10,\"cooldown\":10,\"damage_player\":1}","[]");
        // The held ingredient is part of the native format and remains replaceable.
        r.getIngredients().set(1,net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.IRON_SWORD));
        withRecipe(h,r,(player,pos)->{
            var sword=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
            sword.setDamageValue(sword.getMaxDamage()-2);
            player.setItemInHand(InteractionHand.OFF_HAND,sword);
            player.setItemInHand(InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
            ManualApplicationRecipe.manualApplicationRecipesApplyInWorld(click(player,pos,InteractionHand.OFF_HAND));
            h.assertTrue(player.getOffhandItem().isEmpty() && player.getMainHandItem().is(net.minecraft.world.item.Items.STICK),"2 durability can pay damage 10, breaking the correct hand");
            var second=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
            player.setItemInHand(InteractionHand.OFF_HAND,second);
            ManualApplicationRecipe.manualApplicationRecipesApplyInWorld(click(player,pos,InteractionHand.OFF_HAND));
            h.assertTrue(second.getDamageValue()==0,"cooldown blocks a second attempt even when a new stack is held");
        });
        var consumed=recipe(h,"{\"block_mode\":\"keep\",\"consume_held\":2,\"damage_player\":1}","[]");
        withRecipe(h,consumed,(player,pos)->{
            player.setItemInHand(InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
            ManualApplicationRecipe.manualApplicationRecipesApplyInWorld(click(player,pos,InteractionHand.MAIN_HAND));
            h.assertTrue(player.getMainHandItem().getCount()==1 && player.getHealth()==20,"insufficient costs do not consume or hurt");
            player.getMainHandItem().setCount(3);
            ManualApplicationRecipe.manualApplicationRecipesApplyInWorld(click(player,pos,InteractionHand.MAIN_HAND));
            h.assertTrue(player.getMainHandItem().getCount()==1 && player.getHealth()==19,"pay item and player costs once");
            var creative=h.makeMockPlayer(GameType.CREATIVE);
            creative.setItemInHand(InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
            ManualApplicationRecipe.manualApplicationRecipesApplyInWorld(click(creative,pos,InteractionHand.MAIN_HAND));
            h.assertTrue(creative.getMainHandItem().getCount()==1 && creative.getHealth()==20,"creative players bypass item and health costs");
        });
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void conditionalActionsAndStateMatching(GameTestHelper h) {
        var r=recipe(h,"""
                {"block":"minecraft:iron_block","block_mode":"keep","empty_hand":true,
                 "actions":[{"type":"if","chance":1,"sneaking":true,
                 "then":[{"type":"set_block","block":"minecraft:oak_log[axis=x]","copy_properties":false},
                         {"type":"drop","stack":{"id":"minecraft:diamond","count":2}}],
                 "else":[{"type":"set_block","block":"minecraft:emerald_block"}]}]}
                ""","[]");
        withRecipe(h,r,(player,pos)->{
            ManualApplicationRecipe.manualApplicationRecipesApplyInWorld(click(player,pos,InteractionHand.MAIN_HAND));
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.EMERALD_BLOCK),"else action runs while not sneaking");
            h.getLevel().setBlockAndUpdate(pos,Blocks.IRON_BLOCK.defaultBlockState());player.setShiftKeyDown(true);
            ManualApplicationRecipe.manualApplicationRecipesApplyInWorld(click(player,pos,InteractionHand.MAIN_HAND));
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.OAK_LOG) && h.getLevel().getBlockState(pos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)==Direction.Axis.X,
                    "grouped branch applies explicit state and its drop together");
        });h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void manualPacketsValidationAndAutomation(GameTestHelper h) {
        var r=recipe(h,"{\"block_mode\":\"keep\",\"sneaking\":false,\"priority\":12}","[]");
        @SuppressWarnings("unchecked")
        var serializer=(net.minecraft.world.item.crafting.RecipeSerializer<ManualApplicationRecipe>)AllRecipeTypes.ITEM_APPLICATION.getSerializer();
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            serializer.streamCodec().encode(buffer,r);var copy=serializer.streamCodec().decode(buffer);
            var o=((net.quiwh.cwi.interaction.application.ApplicationExtension)copy).cwi$interaction();
            h.assertTrue(o!=null && Boolean.FALSE.equals(o.sneaking) && o.priority==12 && buffer.readableBytes()==0,"packet retains manual conditions and consumes exactly its payload");
            var json=serializer.codec().codec().encodeStart(h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),copy).getOrThrow();
            h.assertTrue(serializer.codec().codec().parse(h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),json).result().isPresent(),"packet copy can be encoded back to native JSON");
        } finally {buffer.release();}
        var holder=new RecipeHolder<>(ResourceLocation.parse("cwi:manual_contract/automation"),r);
        h.assertTrue(!AllRecipeTypes.CAN_BE_AUTOMATED.test(holder),"manual interaction never becomes an item deployer recipe");
        h.assertTrue(!AllRecipeTypes.CAN_BE_AUTOMATED.test(ManualApplicationRecipe.asDeploying(holder)),"generated JEI deploying representation is filtered too");
        for(String invalid:List.of("{\"cooldown\":-1}","{\"typo\":true}","{\"damage_held\":2,\"consume_held\":1}",
                "{\"state\":{\"axis\":\"bad\"},\"block\":\"minecraft:oak_log\"}","{\"drops\":[{\"stack\":{\"id\":\"minecraft:diamond\"},\"chance\":1.1}]}")) {
            boolean rejected=false;try{recipe(h,invalid,"[]");}catch(RuntimeException expected){rejected=true;}
            h.assertTrue(rejected,"invalid metadata must be rejected: "+invalid);
        }
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void priorityChoosesOneRecipeAndCanceledClicksDoNothing(GameTestHelper h) {
        var low=recipe(h,"{\"priority\":-1,\"consume_held\":1}","[{\"id\":\"minecraft:gold_block\"}]");
        var high=recipe(h,"{\"priority\":8,\"consume_held\":2}","[{\"id\":\"minecraft:diamond_block\"}]");
        var manager=h.getLevel().getRecipeManager();var saved=List.copyOf(manager.getRecipes());
        var pos=h.absolutePos(new BlockPos(1,1,1));var player=h.makeMockPlayer(GameType.SURVIVAL);
        try {
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("cwi:manual_contract/a_low"),low),
                    new RecipeHolder<>(ResourceLocation.parse("cwi:manual_contract/z_high"),high)));
            h.getLevel().setBlockAndUpdate(pos,Blocks.IRON_BLOCK.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK,5));
            var canceled=click(player,pos,InteractionHand.MAIN_HAND);canceled.setCanceled(true);
            ManualApplicationRecipe.manualApplicationRecipesApplyInWorld(canceled);
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.IRON_BLOCK)&&player.getMainHandItem().getCount()==5,"canceled interactions have no effect");
            ManualApplicationRecipe.manualApplicationRecipesApplyInWorld(click(player,pos,InteractionHand.MAIN_HAND));
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.DIAMOND_BLOCK)&&player.getMainHandItem().getCount()==3,"highest priority wins and pays exactly one cost");
        }finally{manager.replaceRecipes(saved);}
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void explicitOutputStatesOverrideCopiedInputProperties(GameTestHelper h) {
        var r=recipe(h,"""
                {"block":"minecraft:oak_log","state":{"axis":"z"},"empty_hand":true,"block_mode":"keep",
                 "actions":[{"type":"set_block","block":"minecraft:stripped_oak_log[axis=x]"}]}
                ""","[]");
        withRecipe(h,r,(player,pos)->{
            var axis=net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS;
            h.getLevel().setBlockAndUpdate(pos,Blocks.OAK_LOG.defaultBlockState().setValue(axis,Direction.Axis.Y));
            var wrong=click(player,pos,InteractionHand.MAIN_HAND);
            ManualApplicationRecipe.manualApplicationRecipesApplyInWorld(wrong);
            h.assertTrue(!wrong.isCanceled()&&h.getLevel().getBlockState(pos).is(Blocks.OAK_LOG),"incorrect input state does not match or intercept vanilla");
            h.getLevel().setBlockAndUpdate(pos,Blocks.OAK_LOG.defaultBlockState().setValue(axis,Direction.Axis.Z));
            ManualApplicationRecipe.manualApplicationRecipesApplyInWorld(click(player,pos,InteractionHand.MAIN_HAND));
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.STRIPPED_OAK_LOG)&&h.getLevel().getBlockState(pos).getValue(axis)==Direction.Axis.X,
                    "an explicit output axis takes precedence over the copied input axis");
        });h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void unbreakableToolsAndConsumedItemRemainders(GameTestHelper h) {
        var r=recipe(h,"{\"block_mode\":\"keep\"}","[]");
        r.getIngredients().set(1,net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.IRON_SWORD));
        withRecipe(h,r,(player,pos)->{
            var sword=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
            sword.set(net.minecraft.core.component.DataComponents.UNBREAKABLE,new net.minecraft.world.item.component.Unbreakable(true));
            player.setItemInHand(InteractionHand.MAIN_HAND,sword);
            ManualApplicationRecipe.manualApplicationRecipesApplyInWorld(click(player,pos,InteractionHand.MAIN_HAND));
            h.assertTrue(sword.getCount()==1&&sword.getDamageValue()==0,"native default cost retains an unbreakable tool");
        });
        var consumed=recipe(h,"{\"block_mode\":\"keep\",\"consume_held\":1}","[]");
        consumed.getIngredients().set(1,net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.WATER_BUCKET));
        withRecipe(h,consumed,(player,pos)->{
            player.setItemInHand(InteractionHand.OFF_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
            ManualApplicationRecipe.manualApplicationRecipesApplyInWorld(click(player,pos,InteractionHand.OFF_HAND));
            h.assertTrue(player.getOffhandItem().is(net.minecraft.world.item.Items.BUCKET),"consumption returns the crafting remainder to the correct hand");
        });h.succeed();
    }

    static void withRecipe(GameTestHelper h,ManualApplicationRecipe recipe,java.util.function.BiConsumer<net.minecraft.world.entity.player.Player,BlockPos> body) {
        var manager=h.getLevel().getRecipeManager();var saved=List.copyOf(manager.getRecipes());
        var pos=h.absolutePos(new BlockPos(1,1,1));
        try {
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("cwi:manual_contract/native"),recipe)));
            h.getLevel().setBlockAndUpdate(pos,Blocks.IRON_BLOCK.defaultBlockState());
            body.accept(h.makeMockPlayer(GameType.SURVIVAL),pos);
        } finally {manager.replaceRecipes(saved);}
    }

    public static void verifyScripts(GameTestHelper h) {
        for(String name:List.of("tool","empty","conditional","costs","legacy")) {
            var holder=h.getLevel().getRecipeManager().byKey(ResourceLocation.parse("cwi:manual_contract/script_"+name)).orElseThrow();
            h.assertTrue(holder.value() instanceof ManualApplicationRecipe,"native Create recipe was generated: "+name);
            var o=((net.quiwh.cwi.interaction.application.ApplicationExtension)holder.value()).cwi$interaction();
            if(name.equals("legacy")) h.assertTrue(o==null && AllRecipeTypes.CAN_BE_AUTOMATED.test(holder),"ordinary recipe retains Create automation");
            else h.assertTrue(o!=null && !AllRecipeTypes.CAN_BE_AUTOMATED.test(holder),"extended script recipe remains manual-only");
            if(name.equals("tool")) h.assertTrue(o.damageHeld==2 && o.cooldown==4 && o.priority==7 && o.drops.size()==2 && o.drops.getFirst().stack().getCount()==3,"script arguments keep exact amounts and intervals");
            if(name.equals("empty")) h.assertTrue(o.emptyHand && o.sneaking && o.blockMode.equals("remove"),"zero-argument constructor and empty hand helpers work together");
        }
    }
}
