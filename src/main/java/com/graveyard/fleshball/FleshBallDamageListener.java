package com.graveyard.fleshball;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Interaction; // Updated import
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class FleshBallDamageListener implements Listener {
    private final FleshBallPlugin plugin;

    public FleshBallDamageListener(FleshBallPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCorpseDamage(EntityDamageEvent event) {
        // 1. Check if the entity hit is one of our optimized invisible tracking Interaction hitboxes
        if (!(event.getEntity() instanceof Interaction)) return;
        Interaction hitbox = (Interaction) event.getEntity();

        NamespacedKey coreKey = new NamespacedKey(plugin, "associated_core");
        if (!hitbox.getPersistentDataContainer().has(coreKey, PersistentDataType.STRING)) return;

        // 2. Cancel the event immediately so the interaction doesn't misbehave
        event.setCancelled(true);

        // 3. Extract the linked Core UUID
        String uuidStr = hitbox.getPersistentDataContainer().get(coreKey, PersistentDataType.STRING);
        if (uuidStr == null) return;
        UUID coreUuid = UUID.fromString(uuidStr);

        // 4. Locate the corresponding cluster tracking profile
        FleshBallCluster cluster = plugin.getActiveClusters().get(coreUuid);
        if (cluster == null) return;

        Entity core = cluster.getCenterCore();
        if (core == null || !core.isValid() || !(core instanceof LivingEntity)) return;

        LivingEntity livingCore = (LivingEntity) core;

        // 5. Calculate modified damage based on your coefficient
        double originalDamage = event.getDamage();
        double transferredDamage = originalDamage * cluster.getDamageTransferCoefficient();

        // 6. Pass the damage smoothly down to the core boss entity
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent edbe = (EntityDamageByEntityEvent) event;
            // Retains information on who hit it (useful for death messages/aggro)
            livingCore.damage(transferredDamage, edbe.getDamager());
        } else {
            livingCore.damage(transferredDamage);
        }
    }
}