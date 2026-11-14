import net.quiwh.cwi.mixin.emissive.EmissiveMixinPlugin;
public final class EmissiveWithoutSodiumFixture {
    public static void main(String[] args) throws Exception {
        MinimalNativeBootstrap.initialize();
        var plugin=new EmissiveMixinPlugin();
        if(plugin.shouldApplyMixin("unused","net.quiwh.cwi.mixin.emissive.SodiumBlockRenderContextMixin"))throw new AssertionError("Sodium absent");
        if(!plugin.shouldApplyMixin("unused","net.quiwh.cwi.mixin.emissive.ItemRendererMixin"))throw new AssertionError("native rendering disabled");
        try{Class.forName("net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext");throw new AssertionError("Sodium present in isolated classpath");}catch(ClassNotFoundException expected){}
        System.out.println("EmissiveWithoutSodiumFixture PASS: optional loader isolation");
    }
}
