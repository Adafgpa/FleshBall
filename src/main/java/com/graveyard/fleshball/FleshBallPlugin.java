package com.graveyard.fleshball;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FleshBallPlugin extends JavaPlugin {
    private final Map<UUID, FleshBallCluster> activeClusters = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        // Register your command!
        this.getCommand("fleshball").setExecutor(new FleshBallCommand(this));

        // The Master Scheduler
        new BukkitRunnable() {
            @Override
            public void run() {
                for (FleshBallCluster cluster : activeClusters.values()) {
                    Entity core = cluster.getCenterCore();
                    if (core == null || !core.isValid()) {
                        cluster.despawn();
                        activeClusters.remove(core.getUniqueId());
                        continue;
                    }

                    cluster.tickCluster(core.getVelocity());
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    // This is the method your Command class was looking for!
    public void registerCluster(UUID coreId, FleshBallCluster cluster) {
        activeClusters.put(coreId, cluster);
    }
}