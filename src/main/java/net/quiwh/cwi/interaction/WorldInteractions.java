package net.quiwh.cwi.interaction;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class WorldInteractions {
    public static void register() {
        NeoForge.EVENT_BUS.addListener(WorldInteractions::beforeEntity);
        NeoForge.EVENT_BUS.addListener(WorldInteractions::afterEntity);
        NeoForge.EVENT_BUS.addListener(WorldInteractions::afterPlayer);
        NeoForge.EVENT_BUS.addListener(WorldInteractions::afterDamage);
        NeoForge.EVENT_BUS.addListener(WorldInteractions::preventNetherPortal);
        NeoForge.EVENT_BUS.addListener(ContainerLocks::interact);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.TagsUpdatedEvent event) -> InteractionRecipes.invalidate());
    }
    private static void preventNetherPortal(BlockEvent.PortalSpawnEvent event) {
        event.setCanceled(true);
    }
    private static void beforeEntity(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof ItemEntity item) ItemImpacts.beforeTick(item);
    }
    private static void afterEntity(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof ItemEntity item) ItemImpacts.afterTick(item);
        else if (event.getEntity() instanceof LivingEntity living && !(living instanceof Player))
            WorldHazards.tick(living, living.level().getGameTime());
    }
    private static void afterPlayer(PlayerTickEvent.Post event) {
        WorldHazards.tick(event.getEntity(), event.getEntity().level().getGameTime());
    }
    private static void afterDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() > 0 && event.getSource().getEntity() instanceof Player player)
            WorldHazards.afterAttack(event.getEntity(), player);
    }
    private WorldInteractions() {}
}
