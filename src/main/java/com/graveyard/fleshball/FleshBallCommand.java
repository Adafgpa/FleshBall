package com.graveyard.fleshball;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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

        if (!player.isOp() && !player.hasPermission("fleshball.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to execute this command!");
            return true;
        }

        // Updated usage string to indicate the optional radius parameter
        if (args.length < 2 || !args[0].equalsIgnoreCase("spawn")) {
            player.sendMessage(ChatColor.RED + "Usage: /fleshball spawn <me|EntityType> [density] [radius]");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // Subcommand 1: SPIN CONTROL
        if (subCommand.equals("spin")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /fleshball spin <speed>");
                return true;
            }

            FleshBallCluster cluster = plugin.getCluster(player.getUniqueId());
            if (cluster == null) {
                player.sendMessage(ChatColor.RED + "You do not have an active fleshball cluster to spin!");
                return true;
            }

            try {
                double speed = Double.parseDouble(args[1]);
                cluster.setRotationSpeed(speed);
                player.sendMessage(ChatColor.GREEN + "Fleshball cluster rotation speed updated to: " + speed);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid speed format. Please provide a numeric value (e.g., 0.05).");
            }
            return true;
        }

        // Subcommand 2: SPAWN STRUCTURAL LOGIC
        if (subCommand.equals("spawn")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /fleshball spawn <me|EntityType> [density] [radius]");
                return true;
            }

            Entity coreEntity;
            String typeName = args[1].toUpperCase();

            if (typeName.equals("ME")) {
                coreEntity = player;
            } else {
                try {
                    EntityType coreType = EntityType.valueOf(typeName);
                    coreEntity = player.getWorld().spawnEntity(player.getLocation(), coreType);
                    
                    coreEntity.setPersistent(false);
                    if (coreEntity instanceof LivingEntity) {
                        LivingEntity living = (LivingEntity) coreEntity;
                        living.setAI(false);
                        living.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
                    }
                } catch (IllegalArgumentException e) {
                    player.sendMessage(ChatColor.RED + "Invalid entity type! Use 'me' or a valid EntityType.");
                    return true;
                }
            }

            int density = 14;
            if (args.length >= 3) {
                try {
                    density = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.YELLOW + "Invalid density. Defaulting to 14.");
                }
            }

            double radius = 4.0;
            if (args.length >= 4) {
                try {
                    radius = Double.parseDouble(args[3]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.YELLOW + "Invalid radius. Defaulting to 4.0.");
                }
            }

            FleshBallCluster cluster = new FleshBallCluster(coreEntity, density, radius);
            cluster.spawn();
            plugin.registerCluster(coreEntity.getUniqueId(), cluster);

            player.sendMessage(ChatColor.GREEN + "Swarm initialized with core: " + coreEntity.getType() + " | Radius: " + radius);
            return true;
        }

        player.sendMessage(ChatColor.RED + "Unknown subcommand. Use /fleshball spawn or /fleshball spin.");
        return true;
    }
}