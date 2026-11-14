package net.quiwh.cwi.compat.processing;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.quiwh.cwi.machine.*;
import net.quiwh.cwi.machine.recipe.MachineRecipe;

/** One persisted native operation. Selection and native environmental checks remain in adapters. */
public final class NativeWork {
    private ResourceLocation id;
    private ProcessingRecipe<?,?> recipe;
    private MachineRecipe transaction;
    private VesselOperation operation;
    private String fingerprint;
    private CompoundTag saved;
    private CompoundTag feedbackItem=new CompoundTag();
    private int batch=1;
    private double duration=1;
    public boolean active(){return id!=null;}
    public ProcessingRecipe<?,?> recipe(){return recipe;}
    public double progress(){return operation==null?0:operation.completed();}
    public double duration(){return duration;}
    public boolean restore(ServerLevel level){
        if(id==null)return false;
        var found=level.getRecipeManager().byKey(id).map(RecipeHolder::value).orElse(null);
        if(!(found instanceof ProcessingRecipe<?,?> p)){clear();return false;}
        if(recipe!=p){
            var data=AdvancedProcessing.get(p);var projected=data.transaction(defaultDuration(p),p.getRequiredHeat());
            var print=fingerprint(p,level);
            if(fingerprint!=null&&!fingerprint.equals(print)){clear();return false;}
            recipe=p;transaction=projected;fingerprint=print;
        }
        if(operation==null&&saved!=null)operation=VesselOperation.load(saved);
        return operation!=null;
    }
    public boolean start(ServerLevel level,RecipeHolder<? extends ProcessingRecipe<?,?>> holder,IItemHandlerModifiable items,MutableRecipeFluids fluids){
        return start(level,holder,items,fluids,1,defaultDuration(holder.value()));
    }
    public static int defaultDuration(ProcessingRecipe<?,?> recipe){
        int duration=recipe.getProcessingDuration();
        if(duration!=0)return Math.max(1,duration);
        String type=net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString();
        if(type.equals("createaddition:rolling"))return com.mrh0.createaddition.config.CommonConfig.ROLLING_MILL_PROCESSING_DURATION.get();
        if(type.equals("create:compacting"))return 120;
        return Set.of("create:mixing","createmetallurgy:alloying","vintageimprovements:centrifugation","vintageimprovements:vibrating","rubberworks:compressing").contains(type)?100:1;
    }
    public boolean start(ServerLevel level,RecipeHolder<? extends ProcessingRecipe<?,?>> holder,IItemHandlerModifiable items,MutableRecipeFluids fluids,int copies,double time){
        var data=AdvancedProcessing.get(holder.value());
        var projected=data.transaction(defaultDuration(holder.value()),holder.value().getRequiredHeat());
        if(!VesselTransaction.eligible(projected,items,fluids,level,copies))return false;
        id=holder.id();recipe=holder.value();transaction=projected;fingerprint=fingerprint(recipe,level);batch=copies;duration=Math.max(1,time);
        feedbackItem=items.getSlots()==0?new CompoundTag():(CompoundTag)items.getStackInSlot(0).saveOptional(level.registryAccess());
        operation=new VesselOperation(level.random.nextLong());return true;
    }
    public boolean step(ServerLevel level,IItemHandlerModifiable items,MutableRecipeFluids fluids,double advance){
        if(!restore(level))return false;
        operation.prepareAdvance(advance);
        return VesselTransaction.step(transaction,operation,items,fluids,level,batch);
    }
    public boolean finished(){return operation!=null&&operation.completed()>=1;}
    public net.minecraft.world.item.ItemStack sourceItem(ServerLevel level){return net.minecraft.world.item.ItemStack.parseOptional(level.registryAccess(),feedbackItem);}
    public void clear(){feedbackItem=new CompoundTag();id=null;recipe=null;transaction=null;operation=null;fingerprint=null;saved=null;batch=1;duration=1;}
    public CompoundTag save(){
        var tag=new CompoundTag();if(id==null)return tag;tag.putString("Recipe",id.toString());
        tag.put("FeedbackItem",feedbackItem.copy());tag.putInt("Batch",batch);tag.putDouble("Duration",duration);
        if(fingerprint!=null)tag.putString("Fingerprint",fingerprint);
        if(operation!=null)tag.put("Operation",operation.save());else if(saved!=null)tag.put("Operation",saved.copy());return tag;
    }
    public void load(CompoundTag tag){clear();if(!tag.contains("Recipe"))return;
        feedbackItem=tag.getCompound("FeedbackItem").copy();
        id=ResourceLocation.tryParse(tag.getString("Recipe"));fingerprint=tag.getString("Fingerprint");saved=tag.getCompound("Operation").copy();
        batch=Math.clamp(tag.getInt("Batch"),1,64);double time=tag.getDouble("Duration");duration=Double.isFinite(time)?Math.max(1,time):1;}
    @SuppressWarnings({"rawtypes","unchecked"})
    private static String fingerprint(ProcessingRecipe<?,?> recipe,ServerLevel level){
        var codec=(com.mojang.serialization.Codec)recipe.getSerializer().codec().codec();
        var json=(com.google.gson.JsonElement)codec.encodeStart(level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),recipe).getOrThrow();
        var object=json.getAsJsonObject();object.remove("priority");
        if(object.has("output_pools"))for(var pool:object.getAsJsonArray("output_pools"))pool.getAsJsonObject().remove("show_outputs");
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    public static List<RecipeHolder<? extends ProcessingRecipe<?,?>>> candidates(ServerLevel level,RecipeType<?> type,Predicate<ProcessingRecipe<?,?>> matches){
        var values=new ArrayList<RecipeHolder<? extends ProcessingRecipe<?,?>>>();
        for(var holder:level.getRecipeManager().getAllRecipesFor((RecipeType)type)){
            var h=(RecipeHolder<?>)holder;if(h.value() instanceof ProcessingRecipe<?,?> p&&matches.test(p))values.add((RecipeHolder)h);}
        values.sort(Comparator.<RecipeHolder<? extends ProcessingRecipe<?,?>>>comparingInt(h->AdvancedProcessing.priority(h.value())).reversed());return values;
    }
    public static NativeWork of(SmartBlockEntity entity){return ((NativeWorkOwner)entity).cwi$work();}
}
