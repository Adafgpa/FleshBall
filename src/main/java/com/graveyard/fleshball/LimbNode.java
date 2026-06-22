package com.graveyard.fleshball;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class LimbNode {
    private final Display displayEntity;
    private final Vector3f halfScaleCorrection = new Vector3f(0, 0, 0);

    // Constructor 1: For the Block Limbs (Cobblestone)
    public LimbNode(Location spawnLoc, Material blockMaterial, Vector3f scale) {
        // Block displays spawn from the corner. We need to save this correction
        // to shift the pivot point internally during live matrix updates.
        this.halfScaleCorrection.set(
                -scale.x() / 2f,
                -scale.y() / 2f,
                -scale.z() / 2f
        );

        this.displayEntity = spawnLoc.getWorld().spawn(spawnLoc, BlockDisplay.class, entity -> {
            entity.setBlock(org.bukkit.Bukkit.createBlockData(blockMaterial));
            applyInitialTransform(entity, scale);
        });
    }

    // Constructor 2: For the Item Head (Player Head)
    public LimbNode(Location spawnLoc, ItemStack visualItem, Vector3f scale) {
        // Items naturally pivot from their true center, no correction needed!
        this.displayEntity = spawnLoc.getWorld().spawn(spawnLoc, ItemDisplay.class, entity -> {
            entity.setItemStack(visualItem);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
            applyInitialTransform(entity, scale);
        });
    }

    private void applyInitialTransform(Display entity, Vector3f scale) {
        // Initially spawn at the shared origin, correcting if it's a block
        entity.setTransformation(new Transformation(
                new Vector3f(halfScaleCorrection),
                new Quaternionf(), 
                scale,
                new Quaternionf()
        ));
        entity.setInterpolationDuration(3); 
        entity.setTeleportDuration(3);
    }

    public Display getEntity() {
        return this.displayEntity;
    }

    /**
     * Updates both translation and rotation dynamically.
     * Combines your corner-centering pivot adjustment with the joint physics.
     */
    
    public void updateTransformation(Vector3f dynamicTranslation, Quaternionf newRotation) {
        Transformation current = displayEntity.getTransformation();
        displayEntity.setInterpolationDelay(0);
        
        // No extra correction here! dynamicTranslation is already the world-space
        // center point of the limb node.
        displayEntity.setTransformation(new Transformation(
                dynamicTranslation,     
                newRotation,            
                current.getScale(), 
                current.getRightRotation()
        ));
    }

    public void destroy() {
        if (displayEntity != null && displayEntity.isValid()) {
            displayEntity.remove();
        }
    }
}