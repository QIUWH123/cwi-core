import java.util.Arrays;
import net.caffeinemc.mods.sodium.client.model.light.*;
import net.caffeinemc.mods.sodium.client.render.frapi.material.MaterialFinderImpl;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.*;
import net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext;
import net.fabricmc.fabric.api.renderer.v1.material.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;

/** Native Sodium import and shade path, with a controlled dark surrounding-light provider. */
public final class SodiumEmissiveFixture {
    static int checks;
    public static void main(String[] args) throws Exception {
        EmissiveFixture.captureFactory = FastCapture::new;
        EmissiveFixture.main(args);
        var glow = new BakedQuad(EmissiveFixture.vertices(), -1, Direction.DOWN,
                EmissiveFixture.sprite(EmissiveFixture.load("{\"cwi\":{\"emissive\":true}}",16)),true);
        var plain = new BakedQuad(EmissiveFixture.vertices(), -1, Direction.DOWN,
                EmissiveFixture.sprite(EmissiveFixture.load("{}",16)),true);
        var finder = new MaterialFinderImpl();
        var material = finder.find();
        var context = new Context();
        var g = new Mesh(); g.fromVanilla(glow,material,null);
        require(g.material().disableDiffuse() && g.material().ambientOcclusion() == TriState.FALSE,
                "native Sodium import recognizes CWI shade/AO flags");
        context.shade(g);
        require(context.unitBrightness() && lights(g,LightTexture.FULL_BRIGHT),
                "native Sodium block shade bypasses dark lighting, directional shade and AO");
        var p = new Mesh(); p.fromVanilla(plain,material,null); context.shade(p);
        require(!p.material().disableDiffuse() && !context.unitBrightness() && lights(p,0),
                "ordinary Sodium block keeps native material and dark lighting");
        context.shade(g); context.shade(p);
        require(!context.unitBrightness(), "a reused Sodium worker does not leak emissive brightness to the next ordinary quad");
        System.out.println("SodiumEmissiveFixture PASS: " + checks + " Sodium checks plus native rendering suite");
    }
    static boolean lights(Mesh q,int expected) { for(int i=0;i<4;i++) if(q.lightmap(i)!=expected) return false;return true; }
    static void require(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
    static final class FastCapture extends EmissiveFixture.Capture implements net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter {
        public void push(org.lwjgl.system.MemoryStack stack,long pointer,int count,com.mojang.blaze3d.vertex.VertexFormat format) {
            int stride=format.getVertexSize();
            int colorOffset=format.getOffset(com.mojang.blaze3d.vertex.VertexFormatElement.COLOR);
            int lightOffset=format.getOffset(com.mojang.blaze3d.vertex.VertexFormatElement.UV2);
            for(int i=0;i<count;i++) {
                long vertex=pointer+(long)i*stride;
                int color=org.lwjgl.system.MemoryUtil.memGetInt(vertex+colorOffset);
                red.add(color&255);green.add(color>>>8&255);alpha.add(color>>>24&255);
                light.add(org.lwjgl.system.MemoryUtil.memGetInt(vertex+lightOffset));
            }
        }
    }
    static final class Mesh extends MutableQuadViewImpl {
        Mesh(){data=new int[EncodingFormat.TOTAL_STRIDE];clear();}
        public void emitDirectly(){}
    }
    static final class Context extends AbstractBlockRenderContext {
        Context(){
            lighters = new LightPipelineProvider(null) {
                @Override public LightPipeline getLighter(LightMode mode){
                    return (quad,pos,data,cull,face,shade,enhanced) -> {Arrays.fill(data.br,.2f); Arrays.fill(data.lm,0);};
                }
            };
        }
        protected void processQuad(MutableQuadViewImpl quad){}
        void shade(Mesh mesh){shadeQuad(mesh,LightMode.SMOOTH,false,ShadeMode.ENHANCED);}
        boolean unitBrightness(){for(float b:quadLightData.br)if(b!=1)return false;return true;}
    }
}
