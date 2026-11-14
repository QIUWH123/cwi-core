package net.quiwh.cwi.compat.processing;
import java.util.*;
/** Explicit capabilities; unsupported metadata never silently becomes decorative. */
public final class NativeRecipeTypes {
    public static final Set<String> BASIN=Set.of("create:mixing","create:compacting","createmetallurgy:melting","createmetallurgy:alloying");
    public static final Set<String> ITEMS=Set.of("create:milling","create:crushing","create:cutting","createaddition:rolling","vintageimprovements:vibrating");
    public static final Set<String> TOWERS=Set.of("tfmg:distillation","createdieselgenerators:distillation");
    public static final Set<String> ACTIONS=Set.of("create:pressing","create:filling","create:deploying","create:emptying");
    public static final Set<String> ALL;
    static {var all=new HashSet<String>();all.addAll(BASIN);all.addAll(ITEMS);all.addAll(TOWERS);all.addAll(ACTIONS);
        all.addAll(Set.of("tfmg:vat","tfmg:vat_machine_recipe","vintageimprovements:centrifugation","rubberworks:compressing"));ALL=Set.copyOf(all);}
    public static boolean supports(String id){return ALL.contains(id);}
    public static boolean fluidInput(String id){return !ITEMS.contains(id)&&!Set.of("create:pressing","create:deploying","create:emptying").contains(id);}
    public static boolean itemInput(String id){return !TOWERS.contains(id)&&!id.equals("rubberworks:compressing");}
    public static boolean fluidOutput(String id){return !ITEMS.contains(id)&&!Set.of("create:pressing","create:deploying","create:filling").contains(id);}
    public static boolean itemOutput(String id){return !TOWERS.contains(id);}
    public static void entry(String id,boolean output,boolean fluid,net.quiwh.cwi.machine.recipe.EntrySettings settings){
        if(fluid?output?!fluidOutput(id):!fluidInput(id):output?!itemOutput(id):!itemInput(id))throw new IllegalArgumentException("Unsupported recipe IO channel: "+id);
        if(ACTIONS.contains(id)&&settings.periodic())throw new IllegalArgumentException("Action recipes do not support periodic IO");
        if((ITEMS.contains(id)||TOWERS.contains(id)||id.equals("rubberworks:compressing")||ACTIONS.contains(id)&&!id.equals("create:deploying"))&&(settings.catalyst()||settings.tool()))throw new IllegalArgumentException("No retained catalyst/tool channel: "+id);
        if(settings.energy()>0)throw new IllegalArgumentException("Energy tool costs are not supported by native machine adapters");
    }
    public static void validate(String id,AdvancedProcessing data){
        if(!supports(id)){
            if(!id.startsWith("cwi:")&&(data.execution()||data.priority()!=0))throw new IllegalArgumentException("Advanced processing is unsupported for "+id);
            return;
        }
        for(var input:data.ingredients())input.ifLeft(v->entry(id,false,true,v.settings())).ifRight(v->entry(id,false,false,v.settings()));
        for(var output:data.results())output.ifLeft(v->entry(id,true,true,v.settings())).ifRight(v->entry(id,true,false,v.settings()));
        if(!itemOutput(id)&&!data.pools().isEmpty())throw new IllegalArgumentException("This machine has no item output pools: "+id);
        if(ACTIONS.contains(id)){
            for(var input:data.ingredients())input.ifLeft(v->{if(v.chance()!=1)throw new IllegalArgumentException("Action fluid inputs require a guaranteed cost");}).ifRight(v->{
                if(v.count()!=1||v.chance()!=1)throw new IllegalArgumentException("Action item inputs require count 1 and a guaranteed cost");});
            if(id.equals("create:deploying")){
                if(data.ingredients().size()!=2||data.ingredients().getFirst().map(v->true,v->v.settings().catalyst()||v.settings().tool()))throw new IllegalArgumentException("Deployer tool/catalyst must be the second, held ingredient");
            }
            for(var output:data.results())output.ifLeft(v->{if(v.chance()!=1)throw new IllegalArgumentException("Action fluid outputs require a guaranteed result");});
        }
    }
    private NativeRecipeTypes(){}
}
