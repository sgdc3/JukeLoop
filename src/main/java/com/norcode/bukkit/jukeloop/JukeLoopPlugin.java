package com.norcode.bukkit.jukeloop;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Jukebox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.material.Attachable;
import org.bukkit.material.Diode;
import org.bukkit.material.PressurePlate;
import org.bukkit.material.Redstone;
import org.bukkit.material.RedstoneTorch;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JukeLoopPlugin extends JavaPlugin implements Listener {
    //private Updater updater;
    private static Pattern locRegex = Pattern.compile("(\\w+)_(\\-?\\d+)_(\\-?\\d+)_(\\-?\\d+)");

    public static BlockFace[] directions = new BlockFace[] { BlockFace.EAST,
            BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.DOWN,
            BlockFace.UP };

    public static ArrayList<Material> playlistOrder;
    public static HashMap<Material, Integer> recordDurations = new HashMap<Material, Integer>();
    private BukkitTask checkTask = null;
    public boolean debugMode = false;
	Location l;

    private BukkitTask saveTask = null;

	static {
         resetRecordDurations();
    };

	private static void resetRecordDurations() {
		// set record durations
		recordDurations.put(Material.GOLD_RECORD, (2 * 60) + 58);
		recordDurations.put(Material.GREEN_RECORD, (3 * 60) + 5);
		recordDurations.put(Material.RECORD_3, (5 * 60) + 45);
		recordDurations.put(Material.RECORD_4, (3 * 60) + 5);
		recordDurations.put(Material.RECORD_5, (2 * 60) + 54);
		recordDurations.put(Material.RECORD_6, (3 * 60) + 17);
		recordDurations.put(Material.RECORD_7, (1 * 60) + 36);
		recordDurations.put(Material.RECORD_8, (2 * 60) + 30);
		recordDurations.put(Material.RECORD_9, (3 * 60) + 8);
		recordDurations.put(Material.RECORD_10, (4 * 60) + 11);
		recordDurations.put(Material.RECORD_11, (1 * 60) + 11);
		recordDurations.put(Material.RECORD_12, (3 * 60) + 55);
		playlistOrder = new ArrayList<Material>(recordDurations.size());
		for (Material m : recordDurations.keySet()) {
			playlistOrder.add(m);
		}
	}
    void debug(String string) {
        // TODO Auto-generated method stub
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info(string);
        }
    }

    /*
    public void doUpdater() {
        String autoUpdate = getConfig().getString("auto-update", "notify-only").toLowerCase();
        if (autoUpdate.equals("true")) {
            updater = new Updater(this, 48295, this.getFile(), Updater.UpdateType.DEFAULT, true);
        } else if (autoUpdate.equals("false")) {
            getLogger().info("Auto-updater is disabled.  Skipping check.");
        } else {
            updater = new Updater(this, 48295, this.getFile(), Updater.UpdateType.NO_DOWNLOAD, true);
        }
    }
    */

	public void loadDurations() {
		ConfigurationSection cfg = getConfig().getConfigurationSection("record-durations");
		for (String key: cfg.getKeys(false)) {
			Material mat = null;
			try {
				Integer matId = Integer.parseInt(key);
				mat = Material.getMaterial(matId);
			} catch (IllegalArgumentException ex) {
				mat = Material.getMaterial(key);
			}
			if (mat == null) {
				getLogger().warning("Unknown record: " + key);
				continue;
			}
			recordDurations.put(mat, cfg.getInt(key));
			if (!playlistOrder.contains(mat)) {
				playlistOrder.add(mat);
			}
		}
		loadData();
		debugMode = getConfig().getBoolean("debug", false);
	}

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        //doUpdater();
        loadDurations();
        getServer().getPluginManager().registerEvents(this, this);
        checkTask = getServer().getScheduler().runTaskTimer(this,
            new Runnable() {
                @Override
                public void run() {
                    ArrayList<Location> toRemove = new ArrayList<Location>();
                    LoopingJukebox jb;
                    for (Entry<Location, LoopingJukebox> e : LoopingJukebox.jukeboxMap.entrySet()) {
                        debug("Checking " + e.getKey() + "->" + e.getValue());
                        jb = e.getValue();
                        if (jb == null || jb.isDead) {
                            toRemove.add(e.getKey());
                            debug("Removing: " + e.getKey() + " from active jukeboxen");
                        } else {
                            jb.doLoop();
                        }
                    }
                    for (Location l : toRemove) {
                        LoopingJukebox.jukeboxMap.remove(l);
                    }
                }
        }, 40, 40);

        saveTask = getServer().getScheduler().runTaskTimer(this,
            new Runnable() {
                @Override
                public void run() {
                    saveData();
                }
            }, 20*60*5, 20*60*5);
    }

    private Location parseLocation(String s) {
        Matcher m = locRegex.matcher(s);
        if (m.matches()) {
            try {
                World w = getServer().getWorld(m.group(1));
                if (w == null) {
                   w = getServer().createWorld(new WorldCreator(m.group(1)));
                }
                return new Location(w, Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)));
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Invalid entry: " + s);
            }
        }
        return null;
    }

    private void loadData() {
        Location l = null;
        for (String s : getConfig().getStringList("jukeboxes")) {
            l = parseLocation(s);
            LoopingJukebox box = LoopingJukebox.getAt(this, l);
			if (box != null) {
            	LoopingJukebox.jukeboxMap.put(l, box);
            	debug("initialized " + l + "->" + box);
			}
        }
        debug("map has " + LoopingJukebox.jukeboxMap.size() + " entries.");
    }

    private void saveData() {
        List<String> boxlist = new ArrayList<String>(LoopingJukebox.jukeboxMap
                .keySet().size());
        for (Location l : LoopingJukebox.jukeboxMap.keySet()) {
            boxlist.add(l.getWorld().getName() + "_" + l.getBlockX() + "_"
                    + l.getBlockY() + "_" + l.getBlockZ());
        }
        getConfig().set("jukeboxes", boxlist);
        saveConfig();
    }

    @Override
    public void onDisable() {
        saveTask.cancel();
        checkTask.cancel();
        saveData();
        super.onDisable();
    }

    private static BlockFace[] allSides = new BlockFace[] {
        BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH,
        BlockFace.DOWN, BlockFace.UP
    };

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockRedstoneEvent(BlockRedstoneEvent event) {
        boolean pressed = false;
        switch (event.getBlock().getType()) {
            case REDSTONE_TORCH_OFF:
                RedstoneTorch torch = (RedstoneTorch) Material.REDSTONE_TORCH_OFF.getNewData(event.getBlock().getData());
                for (BlockFace bf: allSides) {
                    if (bf.equals(torch.getAttachedFace())) continue;
                    if (event.getBlock().getRelative(bf).getType().equals(Material.JUKEBOX)) {
                        loopIfJukebox(event.getBlock().getRelative(bf));
                    }
                }
                break;
            case REDSTONE_BLOCK:
            case REDSTONE_WIRE:
                if (event.getOldCurrent() == 0 && event.getNewCurrent() >= 1) {
                    for (BlockFace bf: allSides) {
                        if (event.getBlock().getRelative(bf).getType().equals(Material.JUKEBOX)) {
                            loopIfJukebox(event.getBlock().getRelative(bf));
                        }
                    }
                }
                break;
            case DIODE_BLOCK_OFF:
                Diode diode = (Diode) Material.DIODE_BLOCK_OFF.getNewData(event.getBlock().getData());
                Block b = event.getBlock().getRelative(diode.getFacing());
                if (b.getType().equals(Material.JUKEBOX)) {
                    loopIfJukebox(b);
                }
                break;
            case LEVER:
            case STONE_BUTTON:
            case WOOD_BUTTON:
                Attachable lever = (Attachable) Material.LEVER.getNewData(event.getBlock().getData());
                Redstone redstone = (Redstone) Material.LEVER.getNewData(event.getBlock().getData());
                if (redstone.isPowered()) {
                    for (BlockFace bf: allSides) {
                        b = event.getBlock().getRelative(bf);
                        if (b.getType().equals(Material.JUKEBOX)) {
                            loopIfJukebox(b);
                        }
                    }
                    Block other = event.getBlock().getRelative(lever.getAttachedFace());
                    for (BlockFace bf: allSides) {
                        b = other.getRelative(bf);
                        if (b.getType().equals(Material.JUKEBOX)) {
                            loopIfJukebox(b);
                        }
                    }
                }
                break;
            case STONE_PLATE:
            case WOOD_PLATE:
                PressurePlate plate = (PressurePlate) event.getBlock().getType().getNewData(event.getBlock().getData());
                pressed = plate.isPressed();
            case GOLD_PLATE:
            case IRON_PLATE:
                if (event.getBlock().getType().equals(Material.GOLD_PLATE) || event.getBlock().getType().equals(Material.IRON_PLATE)) {
                    pressed = event.getBlock().getData() > 0;
                }
                if (pressed) {
                    for (BlockFace bf: allSides) {
                        b = event.getBlock().getRelative(bf);
                        if (b.getType().equals(Material.JUKEBOX)) {
                            loopIfJukebox(b);
                        }
                    }
                    for (BlockFace bf: allSides) {
                        b = event.getBlock().getRelative(bf);
                        if (b.getType().equals(Material.JUKEBOX)) {
                            loopIfJukebox(b);
                        }
                    }
                }
                break;
            case DAYLIGHT_DETECTOR:
                if (event.getBlock().isBlockPowered()) {
                    for (BlockFace bf: allSides) {
                        if (bf.equals(BlockFace.UP)) continue;
                        if (event.getBlock().getRelative(bf).getType().equals(Material.JUKEBOX)) {
                            loopIfJukebox(event.getBlock().getRelative(bf));
                        }
                    }
                }
                break;
            default:
                break;
        }
    }
    

    public void loopIfJukebox(Block b) {
        LoopingJukebox jb = LoopingJukebox.getAt(this, b.getLocation());
        if (jb != null && jb.getJukebox().isPlaying()) {
            jb.onEject();
            jb.doLoop();
        }
    }

    /*
    @EventHandler(ignoreCancelled=true)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getPlayer().hasPermission("jukeloop.admin")) {
            final Player player = event.getPlayer()
            if (updater != null) {
                getServer().getScheduler().runTaskLaterAsynchronously(this, new Runnable() {
                    public void run() {
                        Player player = getServer().getPlayer(playerName);
                        if (player != null && player.isOnline()) {
                            switch (updater.getResult()) {
                            case UPDATE_AVAILABLE:
                                player.sendMessage("An update is available for JukeLoop, visit http://dev.bukkit.org/server-mods/jukeloop/ to get it.");
                                break;
                            case SUCCESS:
                                player.sendMessage("An update for JukeLoop has been downloaded and will take effect when the server restarts.");
                                break;
                            default:
                                // nothing
                            }
                        }
                    }
                }, 20);
            }
        }
    }
    */

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (label.equals("jukeloop")) {
			if (args.length > 0) {
				if (sender.hasPermission("jukeloop.reload") && args[0].equalsIgnoreCase("reload")) {
					// reload
					reloadConfig();
					resetRecordDurations();
					loadDurations();
					sender.sendMessage("[JukeLoop] Reloaded.");
					return true;
				}
			}
		}
		return false;
	}



    @EventHandler(ignoreCancelled = true)
    public void onInteractJukebox(PlayerInteractEvent e) {
        if (!e.getPlayer().hasPermission("jukeloop.use"))
            return;
        if (e.getAction() == Action.LEFT_CLICK_BLOCK
                && e.getClickedBlock().getType() == Material.JUKEBOX) {
            if (((Jukebox) e.getClickedBlock().getState()).isPlaying()) {
                LoopingJukebox jb = LoopingJukebox.getAt(this, e
                        .getClickedBlock().getLocation());
                if (jb != null && jb.getJukebox().isPlaying()) {
                    jb.onEject();
                    jb.doLoop();
                    e.setCancelled(true);
                }
            }
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK
                && e.getClickedBlock().getType() == Material.JUKEBOX
                && recordDurations.containsKey(e.getItem().getType())) {
            Jukebox box = (Jukebox) e.getClickedBlock().getState();
            LoopingJukebox jb = LoopingJukebox.getAt(this, box.getLocation());
            Material record = e.getItem().getType();
            if (!box.isPlaying()) {
                e.setCancelled(true);
                box.setPlaying(record);
                PlayerInventory inventory = e.getPlayer().getInventory();
                if(inventory.getItemInMainHand().equals(e.getItem())) {
                    inventory.setItemInMainHand(new ItemStack(Material.AIR));
                } else if (inventory.getItemInOffHand().equals(e.getItem())) {
                    inventory.setItemInOffHand(new ItemStack(Material.AIR));
                }
                jb.onInsert(record);
            } else {
                jb.onEject();
            }
        }
    }
}
