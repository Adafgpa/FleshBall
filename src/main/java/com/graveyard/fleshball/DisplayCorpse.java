package com.graveyard.fleshball;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class DisplayCorpse {
    private final Entity centralCore;
    private final Vector nominalOffset;
    
    // Physics variables
    private Vector currentPos;
    private Vector velocity = new Vector(0, 0, 0);
    private final double omega0 = 6.0; // Slightly looser spring
    private final double zeta = 0.5;   // Slightly more damping
    private double timeElapsed = 0.0;
    
    // NEW: Random phase so they don't sync up
    private final double randomPhase;

    private ArmorStand torsoVehicle; 
    private final List<LimbNode> limbs = new ArrayList<>();

    public DisplayCorpse(Entity centralCore, Vector nominalOffset) {
        this.centralCore = centralCore;
        this.nominalOffset = nominalOffset;
        this.currentPos = centralCore.getLocation().toVector().add(nominalOffset);
        // Assign a random starting phase between 0 and 2*PI
        this.randomPhase = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
    }

    public void spawn() {
        Location spawnLoc = centralCore.getLocation().add(nominalOffset);

        // 1. Spawn the Invisible Anchor (Small armor stands center passengers better)
        torsoVehicle = spawnLoc.getWorld().spawn(spawnLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setSmall(true); 
            stand.setGravity(false);
        });

        // 2. Build the Cobblestone Mannequin!
        // Vectors are (X: Left/Right, Y: Up/Down, Z: Forward/Back)
        
        // Torso: A flattened cobblestone block
        LimbNode torso = new LimbNode(spawnLoc, Material.COBBLESTONE, 
                new Vector3f(0f, 0.7f, 0f), new Vector3f(0.5f, 0.8f, 0.25f));
        
        // Head: A player head sitting on top of the torso
        LimbNode head = new LimbNode(spawnLoc, new ItemStack(Material.ZOMBIE_HEAD), 
                new Vector3f(0f, 1.3f, 0f), new Vector3f(0.6f, 0.6f, 0.6f));
        
        // Arms: Thin cobblestone sticks on the sides
        LimbNode leftArm = new LimbNode(spawnLoc, Material.COBBLESTONE, 
                new Vector3f(-0.4f, 0.7f, 0f), new Vector3f(0.15f, 0.7f, 0.15f));
        
        LimbNode rightArm = new LimbNode(spawnLoc, Material.COBBLESTONE, 
                new Vector3f(0.4f, 0.7f, 0f), new Vector3f(0.15f, 0.7f, 0.15f));
        
        // Legs: Thin cobblestone sticks below the torso
        LimbNode leftLeg = new LimbNode(spawnLoc, Material.COBBLESTONE, 
                new Vector3f(-0.15f, 0.1f, 0f), new Vector3f(0.15f, 0.7f, 0.15f));
        
        LimbNode rightLeg = new LimbNode(spawnLoc, Material.COBBLESTONE, 
                new Vector3f(0.15f, 0.1f, 0f), new Vector3f(0.15f, 0.7f, 0.15f));

        limbs.addAll(List.of(torso, head, leftArm, rightArm, leftLeg, rightLeg));

        // 3. Mount everything to the invisible Armor Stand
        for (LimbNode limb : limbs) {
            torsoVehicle.addPassenger(limb.getEntity());
        }
    }

    public void tickPhysics(Vector coreVelocity) {
        if (torsoVehicle == null || !torsoVehicle.isValid()) return;

        Location coreLoc = centralCore.getLocation();
        Vector targetPos = coreLoc.toVector().add(nominalOffset);
        
        Vector z = currentPos.clone().subtract(targetPos);
        Vector relVelocity = this.velocity.clone().subtract(coreVelocity);
        
        Vector acceleration = relVelocity.multiply(-2.0 * zeta * omega0)
                                .add(z.multiply(-omega0 * omega0));
        
        double dt = 0.05; 
        this.velocity.add(acceleration.multiply(dt));
        this.currentPos.add(this.velocity.clone().multiply(dt));
        timeElapsed += dt;

        Location newLoc = new Location(coreLoc.getWorld(), currentPos.getX(), currentPos.getY(), currentPos.getZ());
        
        // Face the direction of movement (or face the core if at rest)
        if (this.velocity.lengthSquared() > 0.01) {
            newLoc.setDirection(this.velocity.clone().normalize());
        } else {
            newLoc.setDirection(z.normalize().multiply(-1)); 
        }
        
        torsoVehicle.teleport(newLoc);
        animateFlailing();
    }

    private void animateFlailing() {
        float speed = (float) this.velocity.length();
        // Incorporate the randomPhase so every corpse is out of sync!
        float flailAngle = (float) Math.sin((timeElapsed * (8.0 + speed)) + randomPhase) * (0.3f + (speed * 0.15f));

        // Rotate limbs slightly on the X and Z axis to simulate chaotic writhing
        Quaternionf chaoticRotation = new Quaternionf()
                .rotateX(flailAngle)
                .rotateZ(flailAngle * 0.5f);

        for (LimbNode limb : limbs) {
            limb.updateRotation(chaoticRotation);
        }
    }

    public void despawn() {
        for (LimbNode limb : limbs) {
            limb.destroy();
        }
        if (torsoVehicle != null) {
            torsoVehicle.remove();
        }
    }
}