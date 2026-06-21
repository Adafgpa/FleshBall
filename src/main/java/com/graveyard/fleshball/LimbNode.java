package com.graveyard.fleshball;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class LimbNode {
    private final ItemDisplay displayEntity;
    private final Vector3f defaultTranslation;

    public LimbNode(Location spawnLoc, ItemStack visualItem, Vector3f translationOffset, Vector3f scale) {
        this.defaultTranslation = translationOffset;

        // Spawn the Bukkit API Display Entity
        this.displayEntity = spawnLoc.getWorld().spawn(spawnLoc, ItemDisplay.class, entity -> {
            entity.setItemStack(visualItem);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND); // Standardizes the pivot point
            
            // Set initial transformation
            entity.setTransformation(new Transformation(
                    translationOffset,                   // Position relative to parent
                    new Quaternionf(),                   // Left rotation (Yaw/Pitch/Roll)
                    scale,                               // Scale of the limb
                    new Quaternionf()                    // Right rotation (usually identity)
            ));
            
            // Optimization: Set interpolation so the client smoothly animates rotations
            entity.setInterpolationDuration(5);
            entity.setTeleportDuration(5); 
        });
    }

    public ItemDisplay getEntity() {
        return this.displayEntity;
    }

    // Call this method to make the limb flail or rotate!
    public void updateRotation(Quaternionf newRotation) {
        Transformation current = displayEntity.getTransformation();
        
        displayEntity.setInterpolationDelay(0); // Start animating immediately
        displayEntity.setTransformation(new Transformation(
                current.getTranslation(), 
                newRotation,               // Apply the new quaternion rotation
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