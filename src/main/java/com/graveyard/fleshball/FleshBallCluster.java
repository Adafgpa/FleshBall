package com.graveyard.fleshball;

import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.List;

public class FleshBallCluster {
    private final Entity centerCore;
    private final List<DisplayCorpse> clusterCorpses = new ArrayList<>();
    private final double maxRadius;
    private final int targetCount;

    private double currentAngle = 0.0;
    private double rotationSpeed = 0.0;

    public FleshBallCluster(Entity centerCore, int targetCount, double maxRadius) {
        this.centerCore = centerCore;
        this.targetCount = targetCount;
        this.maxRadius = maxRadius;
        // Call the new bowl generator: targetCount, r (depth), R (outer radius)
        generateBowlMatrix(this.targetCount,this.maxRadius); 
    }

    public void generateBowlMatrix(int targetCount, double maxRadius) {
        this.clusterCorpses.clear(); 
        
        // 1. Calculate the dynamic curvature coefficient 'k'
        // This ensures y = 0 when currentRadius = maxRadius
        double k = 2.0 / (maxRadius * maxRadius);
        
        // The Golden Angle in radians for uniform distribution
        double goldenRatioPhi = Math.PI * (3.0 - Math.sqrt(5.0)); 

        for (int i = 0; i < targetCount; i++) {
            // 2. Uniform Area Fraction (0.0 at center to 1.0 at outer rim)
            double fraction = (double) i / (targetCount - 1);
            double currentRadius = maxRadius * Math.sqrt(fraction);
            double theta = i * goldenRatioPhi;

            // 3. Coordinate Generation based on the dynamic curve: y = k * r^2 - 2
            double mappedX = currentRadius * Math.cos(theta);
            double mappedZ = currentRadius * Math.sin(theta);
            double mappedY = k * (currentRadius * currentRadius) - 2.0;

            Vector structuralOffset = new Vector(mappedX, mappedY, mappedZ);

            // 4. True Outward Surface Normal Vector via Gradient Calculus
            // The partial derivatives of (k*x^2 + k*z^2 - y - 2 = 0) yield <2kx, -1, 2kz>
            double nX = 2.0 * k * mappedX;
            double nY = -1.0;
            double nZ = 2.0 * k * mappedZ;
            Vector outwardNormal = new Vector(nX, nY, nZ).normalize();

            // Pass the perfectly scaled coordinates down to the Corpse!
            clusterCorpses.add(new DisplayCorpse(centerCore, structuralOffset, outwardNormal));
        }
    }

    public Entity getCenterCore() {
        return this.centerCore;
    }

    // NEW: Getters and setters for controlling rotation intensity dynamically
    public double getRotationSpeed() {
        return this.rotationSpeed;
    }

    public void setRotationSpeed(double rotationSpeed) {
        this.rotationSpeed = rotationSpeed;
    }

    // UPDATED: Advances the orbit tracker and forwards the angle to the corpses
    public void tickCluster(Vector currentCoreVelocity) {
        // 1. Advance the accumulation angle by our rotation speed
        this.currentAngle += this.rotationSpeed;

        // 2. Pass the dynamic angle directly into the physics calculator
        for (DisplayCorpse corpse : clusterCorpses) {
            corpse.tickPhysics(currentCoreVelocity, this.currentAngle);
        }
    }
    
    // Instantiates the structural matrix
    public void spawn() {
        // Run your updated mathematical distribution logic using the cached instance fields!
        generateBowlMatrix(this.targetCount, this.maxRadius);
        
        // Loop through and physically spawn every corpse display entity into the world
        for (DisplayCorpse corpse : clusterCorpses) {
            corpse.spawn(); // <--- Add this live execution line!
        }
    }

    // Crucial for memory management: cleans up all child entities
    public void despawn() {
        for (DisplayCorpse corpse : clusterCorpses) {
            corpse.despawn();
        }
    }
}