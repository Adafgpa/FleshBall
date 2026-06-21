package com.graveyard.fleshball;

import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.List;

public class FleshBallCluster {
    private final Entity centerCore;
    private final List<DisplayCorpse> clusterCorpses = new ArrayList<>();
    private final double sphereRadius = 2.5;
    private final int targetCount;

    public FleshBallCluster(Entity centerCore, int targetCount) {
        this.centerCore = centerCore;
        this.targetCount = targetCount;
        // Call the new bowl generator: targetCount, r (depth), R (outer radius)
        generateBowlMatrix(targetCount, 2.0, 5.0); 
    }

    public void generateBowlMatrix(int targetCount, double r, double R) {
        this.clusterCorpses.clear(); 
        
        double goldenRatioPhi = Math.PI * (3.0 - Math.sqrt(5.0)); 

        for (int i = 0; i < targetCount; i++) {
            // 1. Base Spherical Distribution (Fibonacci)
            double normalizedY = 1.0 - ((double) i / (targetCount - 1)) * 2.0;
            double phi = Math.acos(normalizedY); // 0 to PI
            double theta = i * goldenRatioPhi;

            // 2. Apply the Convex Bowl Transformation
            double u = phi / Math.PI;
            double mappedY = -r * u;
            double H = R * Math.pow(1.0 - u, 2.0);

            double mappedX = H * Math.cos(theta);
            double mappedZ = H * Math.sin(theta);

            Vector structuralOffset = new Vector(mappedX, mappedY, mappedZ);

            // 3. Calculate the true outward normal vector of the bowl surface
            double nX = r * Math.cos(theta);
            double nY = -2.0 * R * (1.0 - u);
            double nZ = r * Math.sin(theta);
            Vector outwardNormal = new Vector(nX, nY, nZ).normalize();

            // Pass the generated normal down to the Corpse!
            clusterCorpses.add(new DisplayCorpse(centerCore, structuralOffset, outwardNormal));
        }
    }

    public Entity getCenterCore() {
        return this.centerCore;
    }

    // Executes the control loop for the physics engine
    public void tickCluster(Vector currentCoreVelocity) {
        for (DisplayCorpse corpse : clusterCorpses) {
            corpse.tickPhysics(currentCoreVelocity);
        }
    }
    
    // Instantiates the structural matrix
    public void spawn() {
        for (DisplayCorpse corpse : clusterCorpses) {
            corpse.spawn();
        }
    }

    // Crucial for memory management: cleans up all child entities
    public void despawn() {
        for (DisplayCorpse corpse : clusterCorpses) {
            corpse.despawn();
        }
    }
}