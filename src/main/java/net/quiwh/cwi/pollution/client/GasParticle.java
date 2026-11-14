package net.quiwh.cwi.pollution.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.quiwh.cwi.pollution.GasParticleOptions;

/** Soft, drifting campfire sprites. Physics is visual only; server cells own exposure. */
public final class GasParticle extends TextureSheetParticle {
    private final float initialAlpha;
    private final float initialSize, expandedSize;
    private final float growthTicks, fadeInTicks;
    private final double buoyancy;
    private final int driftInterval;
    private double driftX, driftZ, targetDriftX, targetDriftZ;
    private static final java.util.Set<GasParticle> LIVE=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static ClientLevel currentLevel;
    private GasParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,SpriteSet sprites,GasParticleOptions gas) {
        super(level,x,y,z);
        initialSize=.15F+random.nextFloat()*.06F;expandedSize=.75F+random.nextFloat()*.25F;
        setSize(.15F,.15F);lifetime=30+random.nextInt(21);
        growthTicks=lifetime*(.55F+random.nextFloat()*.30F);
        fadeInTicks=3+random.nextInt(4);
        hasPhysics=true;friction=.90F+random.nextFloat()*.045F;gravity=0;
        xd=vx;yd=vy;zd=vz;
        buoyancy=gas.buoyancy()*.00055;
        driftInterval=6+random.nextInt(5);chooseDrift();
        initialAlpha=gas.opacity()*(.85F+random.nextFloat()*.25F);alpha=0;
        setColor(((gas.color()>>16)&255)/255F,((gas.color()>>8)&255)/255F,(gas.color()&255)/255F);
        pickSprite(sprites);LIVE.add(this);
    }
    public static void reset(){LIVE.clear();currentLevel=null;}
    private static boolean available(ClientLevel level) {
        if(currentLevel!=level){LIVE.clear();currentLevel=level;}
        return LIVE.size()<64;
    }
    @Override public void remove(){super.remove();LIVE.remove(this);}
    public record DynamicProvider(SpriteSet sprites) implements ParticleProvider<GasParticleOptions> {
        public Particle createParticle(GasParticleOptions options,ClientLevel level,double x,double y,double z,double vx,double vy,double vz) {
            return available(level)?new GasParticle(level,x,y,z,vx,vy,vz,sprites,options):null;
        }
    }
    @Override public void tick() {
        super.tick();
        if(removed) return;
        if(age%driftInterval==0)chooseDrift();
        driftX+=(targetDriftX-driftX)*.12;driftZ+=(targetDriftZ-driftZ)*.12;
        float dispersed=smooth(age/20F);
        xd+=driftX*dispersed;zd+=driftZ*dispersed;
        yd+=buoyancy*(.25+.75*dispersed);
        alpha=initialAlpha*smooth(age/fadeInTicks)*(1-smooth((age/(float)lifetime-.2F)/.8F));
        if(!level.getFluidState(BlockPos.containing(x,y,z)).isEmpty()) remove();
    }
    @Override public float getQuadSize(float partialTick) {
        return initialSize+(expandedSize-initialSize)*smooth((age+partialTick)/growthTicks);
    }
    private void chooseDrift() {
        targetDriftX=(random.nextDouble()-.5)*.0012;
        targetDriftZ=(random.nextDouble()-.5)*.0012;
    }
    private static float smooth(float value) {
        value=Math.clamp(value,0,1);return value*value*(3-2*value);
    }
    @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
}
