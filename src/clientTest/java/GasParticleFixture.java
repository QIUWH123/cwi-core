import java.lang.reflect.Field;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.quiwh.cwi.pollution.GasParticleOptions;
import net.quiwh.cwi.pollution.client.GasParticle;

/** Native provider motion, fade and expiry without a graphics/window context. */
public final class GasParticleFixture {
    private static int checks;
    private static final class Sprites implements SpriteSet {
        int picks;
        @Override public TextureAtlasSprite get(int age,int lifetime) { return null; }
        @Override public TextureAtlasSprite get(RandomSource random) { picks++; return null; }
    }
    private static final class GasLevel extends ClientLevel {
        boolean wet;
        private GasLevel() { super(null,null,null,null,0,0,null,null,false,0); }
        @Override public FluidState getFluidState(BlockPos pos) {
            return (wet ? Fluids.WATER : Fluids.EMPTY).defaultFluidState();
        }
    }
    private static Field field(String name) throws Exception {
        Field field=Particle.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static void check(boolean condition,String reason) {
        if(!condition) throw new AssertionError(reason);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        MinimalNativeBootstrap.initialize();
        var dist=net.neoforged.fml.loading.FMLLoader.class.getDeclaredField("dist");
        dist.setAccessible(true);dist.set(null,net.neoforged.api.distmarker.Dist.CLIENT);
        SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        var level=(GasLevel)unsafe.allocateInstance(GasLevel.class);
        for(var gas:java.util.List.of(new GasParticleOptions(0x3366CC,.3F,-1),new GasParticleOptions(0xF0E0D0,.2F,1),new GasParticleOptions(0xFFFFFF,.5F,-.25F))) {
            var sprites=new Sprites();
            var particle=new GasParticle.DynamicProvider(sprites).createParticle(gas,level,2,3,4,.002,gas.buoyancy()*.007,.001);
            check(field("hasPhysics").getBoolean(particle),"native particles enable collision "+gas);
            field("hasPhysics").setBoolean(particle,false); // Collision geometry is covered in native world tests.
            int lifetime=particle.getLifetime();
            check(lifetime>=30 && lifetime<=50,"bounded lifetime "+gas);
            check(particle.getRenderType()==ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT,"translucent campfire layer "+gas);
            check(sprites.picks==1,"native sprite selected "+gas);
            check(field("alpha").getFloat(particle)==0,"puffs start transparent instead of popping into the cloud "+gas);
            float initialSize=((GasParticle)particle).getQuadSize(0);
            check(initialSize>=.15F && initialSize<=.21F,"puffs are already visible-sized as they leave the pipe "+gas);
            check(Math.abs(field("rCol").getFloat(particle)-((gas.color()>>16)&255)/255F)<1e-6,"profile colour "+gas);
            double before=particle.getPos().y;
            particle.tick();
            check(Math.signum(particle.getPos().y-before)==Math.signum(gas.buoyancy()),"profile rising/settling motion "+gas);
            check(field("alpha").getFloat(particle)>0 && field("alpha").getFloat(particle)<gas.opacity(),"puffs fade in gradually "+gas);
            check(((GasParticle)particle).getQuadSize(.5F)>((GasParticle)particle).getQuadSize(0),"growth interpolates between ticks "+gas);
            for(int i=1;i<lifetime/2;i++) particle.tick();
            check(((GasParticle)particle).getQuadSize(0)>initialSize*2,"puffs expand slowly into a cloud "+gas);
            float middleAlpha=field("alpha").getFloat(particle);
            for(int i=lifetime/2;i<lifetime;i++) particle.tick();
            check(((GasParticle)particle).getQuadSize(0)>=.75F && ((GasParticle)particle).getQuadSize(0)<=1,
                    "mature puffs are larger but have bounded screen coverage "+gas);
            check(field("alpha").getFloat(particle)<middleAlpha,"late puffs fade out "+gas);
            check(particle.isAlive() && field("alpha").getFloat(particle)==0,"fades completely before removal "+gas);
            particle.tick();check(!particle.isAlive(),"native expiry "+gas);
            var jet=new GasParticle.DynamicProvider(sprites).createParticle(gas,level,0,3,0,.09,0,0);
            field("hasPhysics").setBoolean(jet,false);
            jet.tick();
            check(jet.getPos().x>.08,"pipe direction controls initial flow "+gas);
            for(int i=1;i<30;i++)jet.tick();
            check(field("xd").getDouble(jet)<.025,"outward flow slows as it disperses "+gas);
            check(Math.signum(field("yd").getDouble(jet))==Math.signum(gas.buoyancy()),"gas buoyancy takes over after the initial jet "+gas);
            var submerged=new GasParticle.DynamicProvider(sprites).createParticle(gas,level,0,0,0,0,0,0);
            field("hasPhysics").setBoolean(submerged,false);
            level.wet=true;submerged.tick();level.wet=false;
            check(!submerged.isAlive(),"gas disappears in fluid "+gas);
        }
        // Removing per-puff variation would make every normalized growth/motion sample identical.
        double minRetention=1,maxRetention=0,minGrowth=1,maxGrowth=0,minOpacity=1,maxOpacity=0;
        for(int n=0;n<128;n++) {
            var particle=(GasParticle)new GasParticle.DynamicProvider(new Sprites())
                    .createParticle(new GasParticleOptions(0x3366CC,.5F,-1),level,0,3,0,.15,0,0);
            field("hasPhysics").setBoolean(particle,false);
            float start=particle.getQuadSize(0);
            double lastAcceleration=0;
            for(int tick=1;tick<=20;tick++) {
                double beforeZ=field("zd").getDouble(particle);
                double friction=field("friction").getFloat(particle);
                particle.tick();
                double acceleration=field("zd").getDouble(particle)-beforeZ*friction;
                check(Math.abs(acceleration-lastAcceleration)<.0002,"sideways drift changes smoothly without random twitching: puff="+n
                        +", tick="+tick+", previous="+lastAcceleration+", current="+acceleration);
                lastAcceleration=acceleration;
            }
            double retention=field("xd").getDouble(particle)/.15;
            check(retention>=.08 && retention<=.37,"jet loses about 65-90 percent of its speed in one second");
            minRetention=Math.min(minRetention,retention);maxRetention=Math.max(maxRetention,retention);
            double opacity=field("alpha").getFloat(particle);
            minOpacity=Math.min(minOpacity,opacity);maxOpacity=Math.max(maxOpacity,opacity);
            float middle=particle.getQuadSize(0);
            while(particle.isAlive())particle.tick();
            double growth=(middle-start)/(particle.getQuadSize(0)-start);
            minGrowth=Math.min(minGrowth,growth);maxGrowth=Math.max(maxGrowth,growth);
        }
        check(maxRetention-minRetention>.1,"puffs have varied drag");
        check(maxGrowth-minGrowth>.15,"puffs expand at different speeds independently of their final size");
        check(maxOpacity-minOpacity>.04,"puffs have varied opacity");
        net.quiwh.cwi.pollution.client.GasParticle.reset();
        var provider=new GasParticle.DynamicProvider(new Sprites());
        var options=new net.quiwh.cwi.pollution.GasParticleOptions(0x3366CC,.25F,-.5F);
        var live=new java.util.ArrayList<Particle>();
        for(int i=0;i<64;i++) {var p=provider.createParticle(options,level,0,3,0,0,0,0);check(p!=null,"capacity accepts first 64 puffs");live.add(p);}
        check(provider.createParticle(options,level,0,3,0,0,0,0)==null,"global cap rejects puff 65");
        live.getFirst().remove();check(provider.createParticle(options,level,0,3,0,0,0,0)!=null,"native removal releases a slot");
        check(Math.abs(field("bCol").getFloat(live.getLast())-.8F)<1e-6,"server supplied tint reaches dynamic particle");
        GasParticle.reset();check(provider.createParticle(options,level,0,3,0,0,0,0)!=null,"resource/world reset releases all slots");
        System.out.println("Native gas particle providers: "+checks+" checks passed");
    }
}
