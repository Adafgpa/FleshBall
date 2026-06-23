package com.graveyard.fleshball;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FleshBallPlugin extends JavaPlugin {
    private final Map<UUID, FleshBallCluster> activeClusters = new ConcurrentHashMap<>();
    // NEW: We need to store last positions to calculate velocity manually
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        this.getCommand("fleshball").setExecutor(new FleshBallCommand(this));

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, FleshBallCluster> entry : activeClusters.entrySet()) {
                    FleshBallCluster cluster = entry.getValue();
                    Entity core = cluster.getCenterCore();

                    // 1. SAFETY CHECK: Do this FIRST before using core
                    if (core == null || !core.isValid()) {
                        cluster.despawn();
                        activeClusters.remove(entry.getKey());
                        lastLocations.remove(entry.getKey()); // Clean up the map too
                        continue;
                    }

                    // 2. VELOCITY CALCULATION
                    Location currentLoc = core.getLocation();
                    Location lastLoc = lastLocations.getOrDefault(core.getUniqueId(), currentLoc);
                    
                    // Velocity = current - last
                    Vector velocity = currentLoc.toVector().subtract(lastLoc.toVector());
                    
                    // 3. TICK PHYSICS
                    cluster.tickCluster(velocity);
                    
                    // 4. UPDATE TRACKING
                    lastLocations.put(core.getUniqueId(), currentLoc);
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    public void registerCluster(UUID coreId, FleshBallCluster cluster) {
        activeClusters.put(coreId, cluster);
    }

    public FleshBallCluster getCluster(UUID coreId) {
        return activeClusters.get(coreId);
    }
}