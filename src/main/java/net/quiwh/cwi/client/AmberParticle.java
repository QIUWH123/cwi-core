package net.quiwh.cwi.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.quiwh.cwi.block.PetrifiedWood;

/** Three-stage amber drips, with Darker Depths' timings, color and gravity. */
public final class AmberParticle extends TextureSheetParticle {
    public enum Stage { DRIPPING, FALLING, LANDING }
    private final Stage stage;
    private AmberParticle(ClientLevel level, double x, double y, double z, Stage stage, SpriteSet sprites) {
        super(level, x, y, z); this.stage = stage;
        setSize(.01f, .01f); setColor(.97f, .56f, .22f); pickSprite(sprites);
        gravity = stage == Stage.DRIPPING ? .06f * .02f * .01f : stage == Stage.FALLING ? .01f : .06f;
        lifetime = stage == Stage.DRIPPING ? 100 : (int) ((stage == Stage.FALLING ? 64 : 28) / (random.nextDouble() * .8 + .2));
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_OPAQUE; }
    @Override public int getLightColor(float partialTick) { return 240; }
    @Override public void tick() {
        xo = x; yo = y; zo = z;
        if (lifetime-- <= 0) {
            remove();
            if (stage == Stage.DRIPPING) level.addParticle(PetrifiedWood.FALLING_AMBER.get(), x, y, z, xd, yd, zd);
            return;
        }
        yd -= gravity; move(xd, yd, zd);
        if (stage == Stage.DRIPPING) { xd *= .02; yd *= .02; zd *= .02; }
        if (stage == Stage.FALLING && onGround) {
            remove(); level.addParticle(PetrifiedWood.LANDING_AMBER.get(), x, y, z, 0, 0, 0);
        }
        xd *= .98f; yd *= .98f; zd *= .98f;
    }
    public record Provider(SpriteSet sprites, Stage stage) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new AmberParticle(level, x, y, z, stage, sprites);
        }
    }
}
