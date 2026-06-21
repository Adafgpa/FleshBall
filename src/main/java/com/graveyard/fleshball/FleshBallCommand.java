    package com.graveyard.fleshball;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import com.graveyard.fleshball.FleshBallCluster;
import com.graveyard.fleshball.FleshBallPlugin;

public class FleshBallCommand implements CommandExecutor {
    private final FleshBallPlugin plugin;

    public FleshBallCommand(FleshBallPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2 || !args[0].equalsIgnoreCase("spawn")) {
            player.sendMessage(ChatColor.RED + "Usage: /fleshball spawn <coreEntityType> [density]");
            return true;
        }

        // 1. Parse the Core Entity Type
        String typeName = args[1].toUpperCase();
        EntityType coreType;
        try {
            coreType = EntityType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid entity type! Try ZOMBIE, BAT, or ARMOR_STAND.");
            return true;
        }

        // 2. Parse optional density parameter
        int density = 14;
        if (args.length >= 3) {
            try {
                density = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.YELLOW + "Invalid density number. Defaulting to 14.");
            }
        }

        Location spawnLoc = player.getLocation();

        // 3. Spawn the physical tracking core
        Entity coreEntity = spawnLoc.getWorld().spawnEntity(spawnLoc, coreType);
        
        // Apply vanilla modifications to keep the core clean and invisible
        coreEntity.setPersistent(false);
        if (coreEntity instanceof org.bukkit.entity.LivingEntity) {
            org.bukkit.entity.LivingEntity living = (org.bukkit.entity.LivingEntity) coreEntity;
            living.setAI(false); // Disables natural AI so it only moves via vehicles/velocity
            living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.INVISIBILITY, 
                    Integer.MAX_VALUE, 0, false, false
            ));
        }

        // 4. Initialize the packet matrix
        FleshBallCluster cluster = new FleshBallCluster(coreEntity, density);
        plugin.registerCluster(coreEntity.getUniqueId(), cluster);

        // 5. TRIGGER THE VISUALS! Send the initial spawn packets to all players in the world
        cluster.spawn();

        // 6. Send success message last
        player.sendMessage(ChatColor.GREEN + "Spawning The Sepulcher Swarm with a " + 
                ChatColor.GOLD + coreType.name() + ChatColor.GREEN + " core (" + density + " nodes)!");

        return true; // ONLY ONE RETURN AT THE VERY END
    }
}

