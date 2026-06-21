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

public class DisplayCorpse {
    private final Entity centralCore;
    private final Vector nominalOffset;
    
    // Physics variables (Kept exactly from your math)
    private Vector currentPos;
    private Vector velocity = new Vector(0, 0, 0);
    private final double omega0 = 7.0;
    private final double zeta = 0.4;
    private double timeElapsed = 0.0;

    // Entity variables
    private ArmorStand torsoVehicle; 
    private final List<LimbNode> limbs = new ArrayList<>();

    public DisplayCorpse(Entity centralCore, Vector nominalOffset) {
        this.centralCore = centralCore;
        this.nominalOffset = nominalOffset;
        this.currentPos = centralCore.getLocation().toVector().add(nominalOffset);
    }

    public void spawn() {
        Location spawnLoc = centralCore.getLocation().add(nominalOffset);

        // 1. Spawn the Torso Vehicle (ArmorStand is mathematically the most stable Bukkit passenger vehicle)
        torsoVehicle = spawnLoc.getWorld().spawn(spawnLoc, ArmorStand.class, stand -> {
            stand.setVisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
        });

        // 2. Generate Limbs (Using basic materials for testing, you can change to custom heads/leather later)
        // Note: The translation Vector3f(x, y, z) pushes the limb away from the torso center
        
        // Head (Shifted up)
        LimbNode head = new LimbNode(spawnLoc, new ItemStack(Material.ZOMBIE_HEAD), new Vector3f(0f, 0.5f, 0f), new Vector3f(1f, 1f, 1f));
        // Left Arm (Shifted left and slightly down)
        LimbNode leftArm = new LimbNode(spawnLoc, new ItemStack(Material.ROTTEN_FLESH), new Vector3f(-0.4f, 0.2f, 0f), new Vector3f(0.5f, 1.5f, 0.5f));
        // Right Arm (Shifted right and slightly down)
        LimbNode rightArm = new LimbNode(spawnLoc, new ItemStack(Material.ROTTEN_FLESH), new Vector3f(0.4f, 0.2f, 0f), new Vector3f(0.5f, 1.5f, 0.5f));

        limbs.add(head);
        limbs.add(leftArm);
        limbs.add(rightArm);

        // 3. Mount the limbs to the Torso
        for (LimbNode limb : limbs) {
            torsoVehicle.addPassenger(limb.getEntity());
        }
    }

    public void tickPhysics(Vector coreVelocity) {
        if (torsoVehicle == null || !torsoVehicle.isValid()) return;

        // 1. Calculate Spring Physics
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

        // 2. Move the Torso (The limbs will automatically follow because they are passengers!)
        Location newLoc = new Location(coreLoc.getWorld(), currentPos.getX(), currentPos.getY(), currentPos.getZ());
        
        // Optional: Make the torso face the core or the direction of movement
        newLoc.setDirection(this.velocity.clone().normalize());
        torsoVehicle.teleport(newLoc);

        // 3. Animate the Limbs (Make them flail based on velocity)
        animateFlailing();
    }

    private void animateFlailing() {
        // Calculate a flail angle based on time and how fast the corpse is moving
        float speed = (float) this.velocity.length();
        float flailAngle = (float) Math.sin(timeElapsed * (5.0 + speed)) * (0.5f + (speed * 0.2f));

        // Create a quaternion rotation (e.g., rotating around the X axis to swing arms)
        Quaternionf armRotation = new Quaternionf().rotateX(flailAngle);

        // In a real scenario, you'd apply different rotations to different limbs.
        // For this example, we'll just flail all of them slightly.
        for (LimbNode limb : limbs) {
            limb.updateRotation(armRotation);
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