package net.quiwh.cwi.pollution;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** Sparse quantities, not entities. Both cells and mixture entries have hard storage/work limits. */
public final class GasClouds extends SavedData {
    public static final int MAX_CELLS=16384,MAX_ENTRIES=65536,MAX_GASES_PER_CELL=32;
    private static final int UPDATE_BUDGET=2048,UPDATE_ENTRY_BUDGET=8192;
    private static final double TRACE=.05;
    static final long MAX_AMOUNT=1_000_000_000;
    private final Map<Long,Cell> cells=new LinkedHashMap<>();
    private final ArrayDeque<Long> work=new ArrayDeque<>();
    private final Map<Long,ChunkClock> chunkClocks=new HashMap<>();
    private final GasVisuals visuals=new GasVisuals();
    private int entries;
    private long lastUpdate=Long.MIN_VALUE;
    private static final class ChunkClock { int cells;long resumed=Long.MIN_VALUE; }
    private static final class Cell {
        final Map<GasProfile,Double> amounts=new LinkedHashMap<>();
        long decayTick,moveTick;
        Cell(long tick){decayTick=tick;moveTick=tick;}
    }
    public static GasClouds get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(GasClouds::new,GasClouds::load),"cwi_gas_pollution");}
    public static GasClouds existing(ServerLevel level){return level.getDataStorage().get(new Factory<>(GasClouds::new,GasClouds::load),"cwi_gas_pollution");}
    private static long chunkKey(long key){return ChunkPos.asLong(BlockPos.getX(key)>>4,BlockPos.getZ(key)>>4);}
    private void addCell(long key,Cell cell) {
        cells.put(key,cell);work.addLast(key);
        chunkClocks.computeIfAbsent(chunkKey(key),k->new ChunkClock()).cells++;
    }
    /** Chunk lifecycle events reset only the clock, without scanning cells or loading chunks. */
    public void resetMovement(ChunkPos chunk,long now) {
        var clock=chunkClocks.get(chunk.toLong());if(clock!=null)clock.resumed=now;
    }
    private Cell fresh(long key,long now) {
        var cell=cells.get(key);
        if(cell!=null&&now>cell.decayTick) {
            var it=cell.amounts.entrySet().iterator();
            while(it.hasNext()) {var e=it.next();double value=e.getKey().decay(e.getValue(),now-cell.decayTick);
                if(value<TRACE){it.remove();entries--;}else e.setValue(value);
            }
            cell.decayTick=now;setDirty();
        }
        return cell;
    }
    /** Collective recipe preflight, including the extra mixture entries needed by this whole batch. */
    public boolean canEmitAll(ServerLevel level,BlockPos pos,Map<GasProfile,Long> amounts) {
        var cell=cells.get(pos.asLong());
        if(cell==null&&cells.size()>=MAX_CELLS)return false;
        int additional=0;
        for(var e:amounts.entrySet()) {
            if(cell==null||!cell.amounts.containsKey(e.getKey()))additional++;
            double old=cell==null?0:e.getKey().decay(cell.amounts.getOrDefault(e.getKey(),0D),Math.max(0,level.getGameTime()-cell.decayTick));
            if(e.getValue()>Math.floor(MAX_AMOUNT-old))return false;
        }
        return entries+additional<=MAX_ENTRIES&&(cell==null?0:cell.amounts.size())+additional<=MAX_GASES_PER_CELL;
    }
    /** Simulation performs no allocation, mutation or save-data creation. Refused amounts stay upstream. */
    public int emit(ServerLevel level,BlockPos pos,GasProfile gas,int amount,boolean simulate) {
        if(gas==null||amount<=0||GasPassages.volume(level,pos)<.01)return 0;
        long key=pos.asLong(),now=level.getGameTime();var cell=cells.get(key);
        if(cell==null&&cells.size()>=MAX_CELLS)return 0;
        boolean newEntry=cell==null||!cell.amounts.containsKey(gas);
        if(newEntry&&(entries>=MAX_ENTRIES||(cell!=null&&cell.amounts.size()>=MAX_GASES_PER_CELL)))return 0;
        double old=cell==null?0:gas.decay(cell.amounts.getOrDefault(gas,0D),Math.max(0,now-cell.decayTick));
        int accepted=(int)Math.min(amount,Math.max(0,Math.floor(MAX_AMOUNT-old)));
        if(accepted<=0||simulate)return accepted;
        cell=fresh(key,now);
        if(cell==null){cell=new Cell(now);addCell(key,cell);}
        if(!cell.amounts.containsKey(gas))entries++;
        cell.amounts.merge(gas,(double)accepted,Double::sum);setDirty();return accepted;
    }
    public double amount(BlockPos pos,GasProfile gas,long now){var c=fresh(pos.asLong(),now);return c==null?0:c.amounts.getOrDefault(gas,0D);}
    public void outlet(ServerLevel l,BlockPos p,Direction d,GasProfile g,int a){visuals.record(l,p,d,g,a);}
    public void outlet(ServerLevel l,BlockPos p,Direction d,GasProfile g,int a,Vec3 opening){visuals.record(l,p,d,g,a,opening);}
    public double concentration(ServerLevel level,Vec3 point,GasProfile gas) {
        if(!GasPassages.containsAir(level,point))return 0;
        var pos=BlockPos.containing(point);return amount(pos,gas,level.getGameTime())/(1000*GasPassages.volume(level,pos));
    }
    /** Snapshot of only the gases touching these samples; exposure never scans every registered gas. */
    public Set<GasProfile> at(ServerLevel level,List<Vec3> points) {
        var result=new LinkedHashSet<GasProfile>();
        for(var p:points)if(GasPassages.containsAir(level,p)){var c=fresh(BlockPos.containing(p).asLong(),level.getGameTime());if(c!=null)result.addAll(c.amounts.keySet());}
        return result;
    }
    public void tick(ServerLevel level) {
        long now=level.getGameTime();visuals.tick(level);
        if(now==lastUpdate||now%10!=0)return;lastUpdate=now;
        int count=Math.min(UPDATE_BUDGET,work.size()),processedEntries=0;
        var deltas=new HashMap<Long,Map<GasProfile,Double>>();var reserved=new HashSet<Long>();
        var reservedEntries=new HashMap<Long,Set<GasProfile>>();int reservedCount=0;
        var visible=new ArrayList<Long>();
        for(int n=0;n<count;n++) {
            long key=work.removeFirst();var cell=fresh(key,now);if(cell==null)continue;
            if(processedEntries+cell.amounts.size()>UPDATE_ENTRY_BUDGET){work.addFirst(key);break;}
            processedEntries+=cell.amounts.size();
            if(cell.amounts.isEmpty()) {
                // Preserve an incoming reservation until deltas commit.
                if(deltas.containsKey(key))work.addLast(key);else {
                    cells.remove(key);long chunk=chunkKey(key);if(--chunkClocks.get(chunk).cells==0)chunkClocks.remove(chunk);setDirty();
                }continue;
            }
            work.addLast(key);var pos=BlockPos.of(key);
            long resumed=chunkClocks.get(chunkKey(key)).resumed;
            long elapsed=cell.moveTick==Long.MIN_VALUE?10:Math.max(0,now-cell.moveTick);
            if(resumed!=Long.MIN_VALUE)elapsed=Math.min(elapsed,Math.max(0,now-resumed));cell.moveTick=now;
            double volume=GasPassages.volume(level,pos);if(volume<.01){cell.moveTick=Long.MIN_VALUE;continue;}visible.add(key);if(elapsed==0)continue;
            var openings=new ArrayList<Direction>();for(var d:Direction.values())if(GasPassages.connects(level,pos,d))openings.add(d);if(openings.isEmpty())continue;
            var neighborVolumes=new EnumMap<Direction,Double>(Direction.class);
            for(var direction:openings)neighborVolumes.put(direction,GasPassages.volume(level,pos.relative(direction)));
            for(var e:cell.amounts.entrySet()) {
                var gas=e.getKey();double amount=e.getValue(),weightSum=0;for(var d:openings)weightSum+=weight(gas,d);
                // Match a normal ten-tick pass and retain delayed queue time without unbounded substeps.
                double fraction=-Math.expm1(Math.log1p(-gas.spreadRate()*.5)*(elapsed/10.0));
                for(var d:openings) {
                    var target=pos.relative(d);long targetKey=target.asLong();
                    if(!cells.containsKey(targetKey)&&!reserved.contains(targetKey)&&cells.size()+reserved.size()>=MAX_CELLS)continue;
                    var neighbor=fresh(targetKey,now);double neighborAmount=neighbor==null?0:neighbor.amounts.getOrDefault(gas,0D);
                    boolean newEntry=neighbor==null||!neighbor.amounts.containsKey(gas);
                    var pendingTypes=reservedEntries.getOrDefault(targetKey,Set.of());
                    boolean reserve=newEntry&&!pendingTypes.contains(gas);
                    if(reserve&&(entries+reservedCount>=MAX_ENTRIES||(neighbor==null?0:neighbor.amounts.size())+pendingTypes.size()>=MAX_GASES_PER_CELL))continue;
                    double w=weight(gas,d),gradient=Math.max(0,1-(neighborAmount/neighborVolumes.get(d))/(amount/volume*w));
                    double transfer=amount*fraction*(w/weightSum)*gradient;
                    // Never overshoot the pair's weighted equilibrium, including narrow air volumes.
                    double equilibrium=(amount*w*neighborVolumes.get(d)-neighborAmount*volume)/(volume+w*neighborVolumes.get(d));
                    transfer=Math.min(transfer,Math.max(0,equilibrium));
                    double queued=deltas.getOrDefault(targetKey,Map.of()).getOrDefault(gas,0D);
                    if(transfer<=0||neighborAmount+queued+transfer>MAX_AMOUNT)continue;
                    if(!cells.containsKey(targetKey))reserved.add(targetKey);
                    if(reserve){reservedEntries.computeIfAbsent(targetKey,k->new HashSet<>()).add(gas);reservedCount++;}
                    deltas.computeIfAbsent(key,k->new HashMap<>()).merge(gas,-transfer,Double::sum);
                    deltas.computeIfAbsent(targetKey,k->new HashMap<>()).merge(gas,transfer,Double::sum);
                }
            }
        }
        for(var e:deltas.entrySet()) {
            var cell=fresh(e.getKey(),now);if(cell==null){cell=new Cell(now);addCell(e.getKey(),cell);}
            for(var delta:e.getValue().entrySet()) {
                if(!cell.amounts.containsKey(delta.getKey()))entries++;
                cell.amounts.merge(delta.getKey(),delta.getValue(),(a,b)->Math.max(0,a+b));
            }
        }
        if(!deltas.isEmpty())setDirty();particles(level,now,visible);visuals.flushClouds(level,now);
    }
    private static double weight(GasProfile gas,Direction direction) {
        if(direction.getAxis()!=Direction.Axis.Y)return 1;
        double bias=direction==Direction.UP?gas.buoyancy():-gas.buoyancy();return bias>=0?1+3*bias:1/(1-3*bias);
    }
    private void particles(ServerLevel level,long now,List<Long> visible) {
        if(visible.isEmpty()||level.players().isEmpty())return;
        for(var player:level.players()) {
            int left=visuals.cloudCapacity(player,now),start=level.random.nextInt(visible.size());
            // One candidate per occupied block coalesces adjacent mixtures instead of drawing every gas there.
            for(int n=0;n<visible.size()&&left>0;n++) {
                long key=visible.get((start+n)%visible.size());var pos=BlockPos.of(key);
                double density=GasVisuals.distanceDensity(player.distanceToSqr(Vec3.atCenterOf(pos)));if(density==0||visuals.nearOutlet(pos))continue;
                double volume=GasPassages.volume(level,pos);if(volume<.01)continue;var cell=fresh(key,now);
                double sum=0;GasProfile selected=null;
                for(var e:cell.amounts.entrySet()) {double c=e.getValue()/(1000*volume);double weight=Math.min(1,c)*e.getKey().particleDensity();if(c<.02||weight==0)continue;
                    sum+=weight;if(level.random.nextDouble()*sum<weight)selected=e.getKey();}
                if(selected==null||level.random.nextDouble()>Math.min(1,sum)*density)continue;
                var point=new Vec3(pos.getX()+level.random.nextDouble(),pos.getY()+level.random.nextDouble(),pos.getZ()+level.random.nextDouble());if(!GasPassages.containsAir(level,point))continue;
                visuals.queueCloud(player,selected,point,new Vec3((level.random.nextDouble()-.5)*.006,selected.buoyancy()*.007,(level.random.nextDouble()-.5)*.006),now);left--;
            }
        }
    }
    public static GasClouds load(CompoundTag tag,HolderLookup.Provider registries) {
        var result=new GasClouds();
        for(var value:tag.getList("cells",Tag.TAG_COMPOUND)) {
            if(result.cells.size()>=MAX_CELLS||result.entries>=MAX_ENTRIES)break;
            var data=(CompoundTag)value;long key=data.getLong("pos");if(result.cells.containsKey(key))continue;
            var cell=new Cell(data.getLong("tick"));cell.moveTick=Long.MIN_VALUE;
            var gases=tag.getInt("version")>=2?data.getCompound("gases"):data;
            for(var name:gases.getAllKeys()) {
                var gas=GasProfiles.byId(name);double amount=gases.getDouble(name);
                if(gas==null||!Double.isFinite(amount)||amount<=0)continue;
                boolean known=cell.amounts.containsKey(gas);
                if(!known&&(cell.amounts.size()>=MAX_GASES_PER_CELL||result.entries>=MAX_ENTRIES))continue;
                cell.amounts.put(gas,Math.min(MAX_AMOUNT,cell.amounts.getOrDefault(gas,0D)+Math.min(MAX_AMOUNT,amount)));
                if(!known)result.entries++;
            }
            result.addCell(key,cell);
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        var list=new ListTag();for(var e:cells.entrySet()) {
            var cell=e.getValue();var data=new CompoundTag();data.putLong("pos",e.getKey());data.putLong("tick",cell.decayTick);var gases=new CompoundTag();
            cell.amounts.forEach((g,a)->{if(a>0)gases.putDouble(g.id(),a);});data.put("gases",gases);list.add(data);
        }
        tag.putInt("version",2);tag.put("cells",list);return tag;
    }
}
