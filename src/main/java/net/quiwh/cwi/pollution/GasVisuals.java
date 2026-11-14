package net.quiwh.cwi.pollution;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Transient, bounded outlet feedback. Server gas quantities never depend on these visuals. */
final class GasVisuals {
    private static final int MAX_OUTLETS=512, PARTICLES_PER_WINDOW=16;
    private record Outlet(BlockPos pipe,Direction direction,GasProfile gas,Vec3 opening) {}
    private static final class Emission {
        final Outlet outlet;
        final Vec3 mouth, normal;
        final BlockPos output;
        final long[] nearbyCells;
        long tick;
        int pending;
        boolean open;
        Emission(Outlet outlet) {
            this.outlet=outlet;
            normal=Vec3.atLowerCornerOf(outlet.direction.getNormal());
            mouth=outlet.opening==null?Vec3.atCenterOf(outlet.pipe).add(normal.scale(.515)):outlet.opening;
            output=outlet.pipe.relative(outlet.direction);
            var nearby=new LongOpenHashSet();var origin=BlockPos.containing(mouth);
            for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)for(int z=-2;z<=2;z++) {
                var pos=origin.offset(x,y,z);
                if(Vec3.atCenterOf(pos).distanceToSqr(mouth)<4)nearby.add(pos.asLong());
            }
            nearbyCells=nearby.toLongArray();
        }
        Vec3 sideways(double a,double b) {
            return switch(outlet.direction.getAxis()) {
                case X -> new Vec3(0,a,b);
                case Y -> new Vec3(a,0,b);
                case Z -> new Vec3(a,b,0);
            };
        }
    }
    private record CloudPuff(GasProfile gas,Vec3 point,Vec3 velocity,long tick) {}
    private static final class Budget {
        long window=Long.MIN_VALUE, tick=Long.MIN_VALUE;
        int used, tickUsed, nextOutlet;
        long creditTick=Long.MIN_VALUE;
        double credit=1;
        final ArrayDeque<CloudPuff> clouds=new ArrayDeque<>();
    }
    private final Map<Outlet,Emission> outlets=new LinkedHashMap<>();
    private final ArrayList<Emission> active=new ArrayList<>();
    private final LongOpenHashSet suppressedCells=new LongOpenHashSet();
    private final Map<ServerPlayer,Budget> budgets=new WeakHashMap<>();
    private boolean outletsChanged;
    private long lastTick=Long.MIN_VALUE;

    void record(ServerLevel level,BlockPos pipe,Direction direction,GasProfile gas,int amount) {
        record(level,pipe,direction,gas,amount,null);
    }
    void record(ServerLevel level,BlockPos pipe,Direction direction,GasProfile gas,int amount,Vec3 opening) {
        if(amount<=0 || level.players().isEmpty())return;
        var outlet=new Outlet(pipe.immutable(),direction,gas,opening);
        var emission=outlets.get(outlet);
        if(emission==null) {
            if(outlets.size()>=MAX_OUTLETS)return;
            emission=new Emission(outlet);outlets.put(outlet,emission);outletsChanged=true;
        }
        long now=level.getGameTime();
        if(emission.tick!=now)emission.pending=0;
        emission.tick=now;emission.pending=Math.min(10000,emission.pending+amount);
    }
    void tick(ServerLevel level) {
        long now=level.getGameTime();
        if(lastTick==now)return;
        lastTick=now;
        if(outlets.values().removeIf(emission->now<emission.tick || now-emission.tick>10))outletsChanged=true;
        if(outletsChanged) {
            active.clear();active.addAll(outlets.values());suppressedCells.clear();
            for(var emission:active)for(long key:emission.nearbyCells)suppressedCells.add(key);
            outletsChanged=false;
        }
        for(var emission:active)emission.open=emission.pending>0 && now-emission.tick<=1
                && GasPassages.acceptsOutlet(level,emission.output,emission.outlet.direction);
        for(var player:level.players()) {
            var budget=budget(player,now);
            int start=active.isEmpty()?0:budget.nextOutlet%active.size();
            for(int i=0;i<active.size() && remaining(player,now)>0;i++) {
                int index=(start+i)%active.size();var emission=active.get(index);
                if(!emission.open)continue;
                double density=distanceDensity(player.distanceToSqr(emission.mouth));
                if(density==0)continue;
                budget.nextOutlet=(index+1)%active.size();
                double desired=Math.min(1,emission.pending/125.0)*density*emission.outlet.gas.particleDensity();
                int count=(int)desired+(level.random.nextDouble()<desired-(int)desired?1:0);
                for(int n=0;n<count && remaining(player,now)>0;n++) {
                    double a=(level.random.nextDouble()-.5)*.06,b=(level.random.nextDouble()-.5)*.06;
                    var point=emission.mouth.add(emission.sideways(a,b));
                    if(!GasPassages.containsAir(level,point))continue;
                    // Uniform disk in the plane gives a narrow outward cone without per-tick trigonometry.
                    do {a=(level.random.nextDouble()*2-1)*.267949;b=(level.random.nextDouble()*2-1)*.267949;}
                    while(a*a+b*b>.0717968);
                    var velocity=emission.normal.add(emission.sideways(a,b)).normalize()
                            .scale(.12+level.random.nextDouble()*.06);
                    send(level,player,emission.outlet.gas,point,velocity,now);
                }
            }
        }
        for(var emission:active)emission.pending=0;
        flushClouds(level,now);
    }
    boolean nearOutlet(BlockPos pos) {return suppressedCells.contains(pos.asLong());}
    static double distanceDensity(double distanceSquared) {
        if(distanceSquared>24*24)return 0;
        if(distanceSquared<=12*12)return 1;
        return 1-.75*(Math.sqrt(distanceSquared)-12)/12;
    }
    private Budget budget(ServerPlayer player,long now) {
        var budget=budgets.computeIfAbsent(player,key->new Budget());
        long window=Math.floorDiv(now,20);
        if(now<budget.tick){budget.clouds.clear();budget.credit=1;budget.creditTick=now;}
        if(budget.creditTick==Long.MIN_VALUE)budget.creditTick=now;
        else if(now>budget.creditTick){budget.credit=Math.min(2,budget.credit+(now-budget.creditTick)*.8);budget.creditTick=now;}
        if(budget.window!=window || now<budget.tick) {budget.window=window;budget.used=0;}
        if(budget.tick!=now) {budget.tick=now;budget.tickUsed=0;}
        return budget;
    }
    int remaining(ServerPlayer player,long now) {
        var budget=budget(player,now);
        // Credit refills smoothly. Idle outlets can emit immediately instead of aliasing with
        // a fixed no-particle tick; the window and one-per-tick caps still bound busy streams.
        return budget.credit+1e-9<1?0:Math.min(PARTICLES_PER_WINDOW-budget.used,1-budget.tickUsed);
    }
    int cloudCapacity(ServerPlayer player,long now) {return PARTICLES_PER_WINDOW-budget(player,now).clouds.size();}
    void queueCloud(ServerPlayer player,GasProfile gas,Vec3 point,Vec3 velocity,long now) {
        var budget=budget(player,now);
        if(budget.clouds.size()<PARTICLES_PER_WINDOW)budget.clouds.addLast(new CloudPuff(gas,point,velocity,now));
    }
    void flushClouds(ServerLevel level,long now) {
        for(var player:level.players()) {
            var budget=budget(player,now);
            while(!budget.clouds.isEmpty() && now-budget.clouds.peekFirst().tick>=10)budget.clouds.removeFirst();
            while(!budget.clouds.isEmpty() && remaining(player,now)>0) {
                var puff=budget.clouds.removeFirst();
                if(player.distanceToSqr(puff.point)>24*24 || nearOutlet(BlockPos.containing(puff.point))
                        || !GasPassages.containsAir(level,puff.point))continue;
                send(level,player,puff.gas,puff.point,puff.velocity,now);
            }
        }
    }
    private void send(ServerLevel level,ServerPlayer player,GasProfile gas,Vec3 point,Vec3 velocity,long now) {
        if(remaining(player,now)>0 && level.sendParticles(player,GasPollution.particle(gas),false,
                point.x,point.y,point.z,0,velocity.x,velocity.y,velocity.z,1)) {
            var budget=budgets.get(player);budget.used++;budget.tickUsed++;budget.credit=Math.max(0,budget.credit-1);
        }
    }
}
