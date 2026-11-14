package net.quiwh.cwi.pollution;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/** Immutable startup data. No script callbacks run during simulation or exposure. */
public record GasProfile(String id, double spreadRate, int halfLifeTicks, double buoyancy, int color,
        float opacity, double particleDensity, double oxygenFraction, boolean living, Map<GasRoute,Protection> routes,
        List<Effect> effects, List<Damage> damage) {
    public GasProfile { routes=Map.copyOf(routes);effects=List.copyOf(effects);damage=List.copyOf(damage); }
    public double decay(double amount,long ticks) { return ticks<=0?amount:amount*Math.pow(.5,ticks/(double)halfLifeTicks); }
    public record Protection(List<ResourceLocation> enchantments,List<EquipmentSlot> slots,int resistance,int durability) {
        public Protection { enchantments=List.copyOf(enchantments);slots=List.copyOf(slots); }
    }
    public record Dose(double gain,double recovery,List<Double> thresholds,String legacyKey,String legacyOwnerKey) {
        public Dose { thresholds=List.copyOf(thresholds); }
    }
    public record Effect(String id,ResourceLocation effect,GasRoute route,double threshold,int level,int duration,Dose dose) {}
    public record Damage(GasRoute route,double threshold,double perSecond,boolean excess) {}

    public static final class RouteBuilder {
        private List<ResourceLocation> enchantments;
        private List<EquipmentSlot> slots=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
        private int resistance=4,durability=1;
        private boolean sealed;
        RouteBuilder(GasRoute route) { enchantments=List.of(ResourceLocation.parse(switch(route) {
            case INHALATION->"cwi:gas_filtration";case EYES->"cwi:ocular_shielding";case SKIN->"cwi:chemical_sealing";
        })); }
        private void check() { if(sealed)throw new IllegalStateException("Gas routes are startup-only"); }
        public RouteBuilder protectedBy(String id) { return protectedBy(List.of(id)); }
        public RouteBuilder protectedBy(List<String> ids) {
            check();if(ids.isEmpty()||ids.size()>16)throw new IllegalArgumentException("protectedBy needs 1-16 enchantment IDs; use noProtection to disable");
            enchantments=ids.stream().map(ResourceLocation::parse).distinct().toList();return this;
        }
        public RouteBuilder noProtection() { check();enchantments=List.of();return this; }
        public RouteBuilder protectionSlots(List<String> names) {
            check();if(names.isEmpty())throw new IllegalArgumentException("protectionSlots must contain armor slots");
            slots=names.stream().map(name->switch(name) {
                case "head"->EquipmentSlot.HEAD;case "chest"->EquipmentSlot.CHEST;case "legs"->EquipmentSlot.LEGS;case "feet"->EquipmentSlot.FEET;
                default->throw new IllegalArgumentException("Gas protection slot must be head, chest, legs or feet: "+name);
            }).distinct().toList();return this;
        }
        public RouteBuilder requiredResistance(double value) { check();resistance=integer(value,1,64,"requiredResistance");return this; }
        public RouteBuilder durabilityCost(double value) { check();durability=integer(value,0,1_000_000,"durabilityCost");return this; }
        Protection build() { sealed=true;return new Protection(enchantments,slots,resistance,durability); }
    }
    public static final class Builder {
        private final String id;
        private double spread=.25,buoyancy=0,density=.5,oxygen;
        private int halfLife=800,color=0xFFFFFF;
        private float opacity=.25F;
        private boolean living,sealed;
        private final Map<GasRoute,RouteBuilder> routes=new EnumMap<>(GasRoute.class);
        private final List<Effect> effects=new ArrayList<>();
        private final List<Damage> damage=new ArrayList<>();
        public Builder(String id) { this.id=ResourceLocation.parse(id).toString();for(var r:GasRoute.values())routes.put(r,new RouteBuilder(r)); }
        private void check() { if(sealed)throw new IllegalStateException("Gas profiles are startup-only"); }
        public Builder spreadRate(double v) { check();spread=range(v,0,1,"spreadRate");return this; }
        public Builder halfLife(double v) { check();halfLife=integer(v,1,Integer.MAX_VALUE,"halfLife (ticks)");return this; }
        public Builder buoyancy(double v) { check();buoyancy=range(v,-1,1,"buoyancy");return this; }
        public Builder opacity(double v) { check();opacity=(float)range(v,0,1,"opacity");return this; }
        public Builder particleDensity(double v) { check();density=range(v,0,1,"particleDensity");return this; }
        /** Oxygen content by volume: 0 for inert/toxic gas, 0.2095 for air, 1 for oxygen. */
        public Builder oxygenFraction(double v) { check();oxygen=range(v,0,1,"oxygenFraction");return this; }
        public Builder color(double v) { check();color=integer(v,0,0xFFFFFF,"color");return this; }
        public Builder targets(String v) { check();if(!v.equals("players")&&!v.equals("living"))throw new IllegalArgumentException("targets must be players or living");living=v.equals("living");return this; }
        public Builder route(String name,Consumer<RouteBuilder> callback) { check();callback.accept(routes.get(GasRoute.parse(name)));return this; }
        public Builder effect(String effect,Map<String,Object> options) {
            check();keys(options,Set.of("id","route","threshold","level","duration","dose"));
            if(effects.size()>=32)throw new IllegalArgumentException("At most 32 effects per gas");
            String ruleId=text(options,"id","effect_"+effects.size());
            if(!ruleId.matches("[a-z0-9_.-]{1,80}")||effects.stream().anyMatch(e->e.id.equals(ruleId)))throw new IllegalArgumentException("Gas effect IDs must be unique stable names");
            Dose dose=null;
            if(options.containsKey("dose")) {
                if(!(options.get("dose") instanceof Map<?,?> raw))throw new IllegalArgumentException("dose must be an object");
                @SuppressWarnings("unchecked") var d=(Map<String,Object>)raw;
                keys(d,Set.of("gainPerSecond","recoveryPerSecond","levelThresholds","legacyKey","legacyOwnerKey"));
                if(!(d.get("levelThresholds") instanceof List<?> levels)||levels.isEmpty()||levels.size()>4)throw new IllegalArgumentException("dose needs 1-4 levelThresholds");
                var thresholds=new ArrayList<Double>();double previous=0;
                for(var level:levels) {double v=number(level,"dose threshold");if(v<=previous||v>1e9)throw new IllegalArgumentException("dose thresholds must increase and be positive");thresholds.add(v);previous=v;}
                dose=new Dose(range(num(d,"gainPerSecond",1),0,1e6,"gainPerSecond"),range(num(d,"recoveryPerSecond",1),0,1e6,"recoveryPerSecond"),thresholds,
                        legacyKey(d,"legacyKey"),legacyKey(d,"legacyOwnerKey"));
                if(dose.legacyOwnerKey()!=null&&dose.legacyKey()==null)throw new IllegalArgumentException("legacyOwnerKey requires legacyKey");
                if(!options.containsKey("id"))throw new IllegalArgumentException("Accumulated dose requires a stable id");
            }
            effects.add(new Effect(ruleId,ResourceLocation.parse(effect),GasRoute.parse(text(options,"route","inhalation")),
                    range(num(options,"threshold",.1),0,1e9,"threshold"),integer(num(options,"level",1),1,256,"level"),
                    duration(options.getOrDefault("duration",40)),dose));return this;
        }
        public Builder damage(Map<String,Object> options) {
            check();keys(options,Set.of("route","threshold","damagePerSecond","scaling"));
            if(damage.size()>=16)throw new IllegalArgumentException("At most 16 damage rules per gas");
            String scaling=text(options,"scaling","fixed");if(!scaling.equals("fixed")&&!scaling.equals("excess"))throw new IllegalArgumentException("scaling must be fixed or excess");
            damage.add(new Damage(GasRoute.parse(text(options,"route","inhalation")),range(num(options,"threshold",.1),0,1e9,"threshold"),
                range(num(options,"damagePerSecond",1),0,1000,"damagePerSecond"),scaling.equals("excess")));return this;
        }
        public GasProfile build() {
            check();sealed=true;var policies=new EnumMap<GasRoute,Protection>(GasRoute.class);
            routes.forEach((r,b)->policies.put(r,b.build()));
            return new GasProfile(id,spread,halfLife,buoyancy,color,opacity,density,oxygen,living,policies,effects,damage);
        }
    }
    private static String legacyKey(Map<String,Object> options,String name) {
        var value=text(options,name,null);
        if(value!=null&&(!value.matches("[a-z0-9_.:/-]{1,160}")||value.equals("cwi:gas_doses")||value.equals("cwi:gas_effect_owners")))
            throw new IllegalArgumentException("Invalid legacy gas data key: "+value);
        return value;
    }
    private static String text(Map<String,Object> m,String k,String fallback) {var v=m.get(k);if(v==null)return fallback;if(!(v instanceof String))throw new IllegalArgumentException(k+" must be a string");return (String)v;}
    private static double num(Map<String,Object> m,String k,double fallback) {return m.containsKey(k)?number(m.get(k),k):fallback;}
    private static double number(Object v,String name) {if(!(v instanceof Number n))throw new IllegalArgumentException(name+" must be a number");return range(n.doubleValue(),-Double.MAX_VALUE,Double.MAX_VALUE,name);}
    private static double range(double v,double min,double max,String name) {if(!Double.isFinite(v)||v<min||v>max)throw new IllegalArgumentException(name+" must be between "+min+" and "+max);return v;}
    private static int integer(double v,int min,int max,String name) {range(v,min,max,name);if(v!=Math.rint(v))throw new IllegalArgumentException(name+" requires a whole number");return (int)v;}
    private static int duration(Object value) {
        if(value instanceof Number)return integer(number(value,"duration"),1,1_200_000,"duration");
        if(value instanceof String s && s.matches("[0-9]+(?:\\.[0-9]+)?[tsm]")) {
            double scale=s.endsWith("t")?1:s.endsWith("s")?20:1200;
            return integer(Double.parseDouble(s.substring(0,s.length()-1))*scale,1,1_200_000,"duration");
        }
        throw new IllegalArgumentException("duration must be ticks or a time string such as 2s");
    }
    private static void keys(Map<String,Object> options,Set<String> allowed) {for(var key:options.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Unknown gas rule setting: "+key);}
}
