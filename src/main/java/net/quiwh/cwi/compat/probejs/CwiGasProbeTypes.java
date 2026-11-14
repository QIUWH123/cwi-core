package net.quiwh.cwi.compat.probejs;

import dev.latvian.mods.kubejs.fluid.FluidBuilder;
import moe.wolfgirl.probejs.typescript.Documents;
import moe.wolfgirl.probejs.typescript.document.Types;
import moe.wolfgirl.probejs.typescript.document.members.*;
import net.quiwh.cwi.pollution.GasProfile;

/** Runtime mixin methods are explicitly represented even when discovery reflects pre-mixin types. */
public final class CwiGasProbeTypes {
    public static void transform(Documents.ClassDocument entry) {
        var type=entry.classInfo().clazz();
        if(type==FluidBuilder.class) {
            if(entry.document().members.stream().noneMatch(m->m instanceof MethodDecl method&&method.name.equals("gas"))) {
                entry.document().members.add(new moe.wolfgirl.probejs.typescript.document.builders.MethodBuilder("gas").param("configure",Types.lambda(b->b.param("gas",Types.clazz(GasProfile.Builder.class)).returns(Types.VOID)))
                        .returnType(Types.clazz(FluidBuilder.class)).build());
            }
        }
        if(type==FluidBuilder.class||type==net.quiwh.cwi.compat.GasConfigurationEvent.class) {
            for(var member:entry.document().members)if(member instanceof MethodDecl method) {
                int index=method.name.equals("gas")?0:method.name.equals("modify")?1:-1;
                if(index>=0&&method.params.size()>index)method.params.set(index,new ParamDecl("configure",Types.lambda(b->b.param("gas",Types.clazz(GasProfile.Builder.class)).returns(Types.VOID))));
            }
        }
        if(type!=GasProfile.Builder.class)return;
        var effect=Types.raw("{ id?: string; route?: 'inhalation' | 'eyes' | 'skin'; threshold?: number; level?: number; duration?: number | string; dose?: {gainPerSecond: number; recoveryPerSecond: number; levelThresholds: number[]; legacyKey?: string; legacyOwnerKey?: string} }");
        var damage=Types.raw("{ route?: 'inhalation' | 'eyes' | 'skin'; threshold?: number; damagePerSecond: number; scaling?: 'fixed' | 'excess' }");
        for(var member:entry.document().members)if(member instanceof MethodDecl method&&!method.params.isEmpty()) {
            if(method.name.equals("route")&&method.params.size()==2) {
                method.params.set(0,new ParamDecl("route",Types.raw("'inhalation' | 'eyes' | 'skin'")));
                method.params.set(1,new ParamDecl("configure",Types.lambda(b->b.param("route",Types.clazz(GasProfile.RouteBuilder.class)).returns(Types.VOID))));
            }
            int index=method.name.equals("effect")?1:method.name.equals("damage")?0:-1;
            if(index>=0&&method.params.size()>index)method.params.set(index,new ParamDecl(method.params.get(index).name,index==1?effect:damage));
        }
    }
    private CwiGasProbeTypes(){}
}
