package com.graveyard.fleshball;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import main.java.com.graveyard.fleshball.LimbNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class DisplayCorpse {
    private final Entity centralCore;
    private final Vector nominalOffset;
    
    // Physics variables
    private Vector currentPos;
    private Vector velocity = new Vector(0, 0, 0);
    private final double omega0 = 6.0;
    private final double zeta = 0.5;
    private double timeElapsed = 0.0;
    private final double randomPhase;
    
    private ArmorStand anchorVehicle; 
    
    private LimbNode torso;
    private LimbNode head;
    private LimbNode leftArm;
    private LimbNode rightArm;
    private LimbNode leftLeg;
    private LimbNode rightLeg;
    private final List<LimbNode> limbs = new ArrayList<>();

    private final Vector outwardNormal;
    private Quaternionf baseOutwardRotation;

    // 1. Define VIRTUAL JOINT positions relative to the Torso center (0,0,0)
    // Based on Torso dimensions: Width=0.8, Height=0.25, Depth=0.5
    private final Vector3f jointLeftShoulder  = new Vector3f(-0.40f,  0.10f, 0.0f);
    private final Vector3f jointRightShoulder = new Vector3f( 0.40f,  0.10f, 0.0f);
    private final Vector3f jointLeftHip       = new Vector3f(-0.25f, -0.125f, 0.0f);
    private final Vector3f jointRightHip      = new Vector3f( 0.25f, -0.125f, 0.0f);
    
    // Head offset is rigid (Torso top Y=0.125 + half head height 0.25)
    private final Vector3f offsetHead         = new Vector3f( 0.00f,  0.375f, 0.0f);

    public DisplayCorpse(Entity centralCore, Vector nominalOffset, Vector outwardNormal) {
        this.centralCore = centralCore;
        this.nominalOffset = nominalOffset;
        this.outwardNormal = outwardNormal; 
        this.currentPos = centralCore.getLocation().toVector().add(nominalOffset);
        this.randomPhase = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);

        calculateBaseNormalRotation();
    }

    private void calculateBaseNormalRotation() {
        // 1. Z-axis: The Normal vector (outward)
        Vector3f zAxis = new Vector3f(
                (float) outwardNormal.getX(),
                (float) outwardNormal.getY(),
                (float) outwardNormal.getZ()
        ).normalize();

        // 2. Reference Up vector
        Vector3f worldUp = new Vector3f(0f, 1f, 0f);
        if (Math.abs(zAxis.dot(worldUp)) > 0.99f) {
            worldUp.set(0f, 0f, 1f); 
        }

        // 3. X-axis: Tangent to the surface
        // X = WorldUp x Z
        Vector3f xAxis = worldUp.cross(zAxis).normalize();

        // 4. Y-axis: The "Body Up" vector
        // To ensure Y points 'up' relative to the surface and stays orthogonal:
        // Y = Z x X
        Vector3f yAxis = zAxis.cross(xAxis).normalize();

        // 5. Construct the matrix from the axes
        org.joml.Matrix3f rotMatrix = new org.joml.Matrix3f(
            xAxis.x, xAxis.y, xAxis.z,
            yAxis.x, yAxis.y, yAxis.z,
            zAxis.x, zAxis.y, zAxis.z
        );
        
        // 6. Set the quaternion from the rotation matrix
        this.baseOutwardRotation = new Quaternionf().setFromNormalized(rotMatrix);
    }

    public void spawn() {
        Location spawnLoc = centralCore.getLocation().add(nominalOffset);
        spawnLoc.setDirection(new Vector(0, 0, 1)); // Clean neutral forward alignment

        anchorVehicle = spawnLoc.getWorld().spawn(spawnLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setSmall(true); 
            stand.setGravity(false);
        });

        // Instantiate using your direct target dimensions
        torso    = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0.8f, 0.25f, 0.5f));
        head     = new LimbNode(spawnLoc, new ItemStack(Material.ZOMBIE_HEAD), new Vector3f(0.5f, 0.5f, 0.5f));
        leftArm  = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0.2f, 0.6f, 0.2f));
        rightArm = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0.2f, 0.6f, 0.2f));
        leftLeg  = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0.2f, 0.6f, 0.2f));
        rightLeg = new LimbNode(spawnLoc, Material.COBBLESTONE, new Vector3f(0.2f, 0.6f, 0.2f));

        limbs.addAll(List.of(torso, head, leftArm, rightArm, leftLeg, rightLeg));
    }

    public void tickPhysics(Vector coreVelocity) {
        if (anchorVehicle == null || !anchorVehicle.isValid()) return;

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
        newLoc.setDirection(new Vector(0, 0, 1));
        anchorVehicle.teleport(newLoc);

        for (LimbNode limb : limbs) {
            limb.getEntity().teleport(newLoc);
        }

        animateFlailing();
    }
    private void animateFlailing() {
        float speed = (float) this.velocity.length();
        float wave = (float) Math.sin((timeElapsed * (8.0 + speed)) + randomPhase);
        float swingAngle = wave * (0.2f + (speed * 0.15f));

        // 1. Torso's Total Rotation
        // Start with the base frame (the bowl normal alignment), then apply the local swing
        Quaternionf localWrithe = new Quaternionf()
                .rotateX(swingAngle)
                .rotateZ(swingAngle * 0.3f);
        Quaternionf torsoRotation = new Quaternionf(baseOutwardRotation).mul(localWrithe);

        // Get the Anchor's position as the World Origin
        Vector3f anchorPos = new Vector3f(
            (float) anchorVehicle.getLocation().getX(), 
            (float) anchorVehicle.getLocation().getY(), 
            (float) anchorVehicle.getLocation().getZ()
        );

        // 2. Transform the Joints
        // This is the core step: A joint position defined in torso-local space
        // is rotated by the torso's orientation and added to the anchor.
        Vector3f worldLeftShoulder  = new Vector3f(jointLeftShoulder).rotate(torsoRotation).add(anchorPos);
        Vector3f worldRightShoulder = new Vector3f(jointRightShoulder).rotate(torsoRotation).add(anchorPos);
        Vector3f worldLeftHip       = new Vector3f(jointLeftHip).rotate(torsoRotation).add(anchorPos);
        Vector3f worldRightHip      = new Vector3f(jointRightHip).rotate(torsoRotation).add(anchorPos);
        
        // Update Torso display
        torso.updateTransformation(new Vector3f(anchorPos), torsoRotation);

        // 3. Update Head
        head.updateTransformation(new Vector3f(anchorPos).add(new Vector3f(offsetHead).rotate(torsoRotation)), torsoRotation);

        // Now we are ready for the next step: 
        // Calculating how the limbs dangle FROM these worldLeftShoulder, worldRightShoulder, etc.
        // 2. Limb gravity dangle (pointing towards absolute -Y)
        // We compute this once for all limbs, or you can randomize it per-limb for extra "flail" <--
        Quaternionf limbRotation = new Quaternionf()
                .rotateX((float) Math.PI)   // Initial dangle to point down
                .rotateX(swingAngle * 1.2f) // Velocity-based drag
                .rotateZ(swingAngle * 0.4f);

        // This vector represents the limb's center point in local 'dangle' space
        // Since our limb block is 0.6 tall, the center is 0.3 units below the socket
        Vector3f limbDangleLocal = new Vector3f(0f, -0.3f, 0f);

        // 3. Update Limbs
        // Each limb takes its specific socket (from the Torso-rotated joint) 
        // and adds the dangled offset.
        
        // Left Arm
        leftArm.updateTransformation(
            worldLeftShoulder.add(new Vector3f(limbDangleLocal).rotate(limbRotation)), 
            limbRotation
        );
        
        // Right Arm
        rightArm.updateTransformation(
            worldRightShoulder.add(new Vector3f(limbDangleLocal).rotate(limbRotation)), 
            limbRotation
        );
        
        // Left Leg
        leftLeg.updateTransformation(
            worldLeftHip.add(new Vector3f(limbDangleLocal).rotate(limbRotation)), 
            limbRotation
        );
        
        // Right Leg
        rightLeg.updateTransformation(
            worldRightHip.add(new Vector3f(limbDangleLocal).rotate(limbRotation)), 
            limbRotation
        );
    }

    public void despawn() {
        for (LimbNode limb : limbs) { 
            limb.destroy(); 
        }
        if (anchorVehicle != null && anchorVehicle.isValid()) { 
            anchorVehicle.remove(); 
        }
    }
}