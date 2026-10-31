package net.quiwh.cwi.machine.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;

/** Native 1.21 providers retaining the original pack effects' animation, motion and lighting. */
final class MachineParticle extends TextureSheetParticle {
    enum Kind { SMOKE, FLAME, BURST, STEAM }
    private final SpriteSet sprites;
    private final Kind kind;

    private MachineParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,
                            SpriteSet sprites,Kind kind) {
        super(level,x,y,z);
        this.sprites=sprites;
        this.kind=kind;
        switch(kind) {
            case SMOKE -> {
                scale(3); setSize(.25f,.25f); lifetime=18; gravity=.000003f;
                xd=0; yd=.1f; zd=0;
            }
            case FLAME, BURST -> {
                setSize(.2f,.2f); quadSize*=2; lifetime=20; friction=.96f; gravity=-.1f;
                hasPhysics=true; xd=xd*.01f+vx; yd=yd*.01f+vy; zd=zd*.01f+vz;
            }
            case STEAM -> {
                setSize(.5f,.5f); quadSize*=1.2f; lifetime=Math.max(1,28+random.nextInt(6)-3);
                gravity=.1f; hasPhysics=true; xd=vx; yd=vy; zd=vz;
            }
        }
        setSpriteFromAge(sprites);
    }

    @Override public void tick() {
        super.tick();
        if(removed) return;
        if(kind==Kind.FLAME) {
            xd+=random.nextFloat()/500f*(random.nextBoolean()?1:-1);
            zd+=random.nextFloat()/500f*(random.nextBoolean()?1:-1);
        }
        int frames=kind==Kind.SMOKE?10:kind==Kind.STEAM?8:11;
        int interval=kind==Kind.STEAM?10:2;
        setSprite(sprites.get(age/interval%frames+1,frames));
        if((kind==Kind.FLAME||kind==Kind.BURST) && level.isWaterAt(BlockPos.containing(x,y,z))) {
            level.addParticle(ParticleTypes.BUBBLE,x,y,z,xd,yd,zd);
            remove();
        }
    }
    @Override protected int getLightColor(float partialTick) {
        return kind==Kind.FLAME||kind==Kind.BURST?0xF000F0:super.getLightColor(partialTick);
    }
    @Override public ParticleRenderType getRenderType() {
        return kind==Kind.FLAME||kind==Kind.BURST?ParticleRenderType.PARTICLE_SHEET_OPAQUE:ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    record Provider(SpriteSet sprites,Kind kind) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel level,double x,double y,double z,
                                                  double vx,double vy,double vz) {
            return new MachineParticle(level,x,y,z,vx,vy,vz,sprites,kind);
        }
    }
}
