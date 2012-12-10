package com.norcode.bukkit.jukeloop;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Jukebox;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class JukeLoopPlugin extends JavaPlugin implements Listener {
	
	
	public static BlockFace[] directions = new BlockFace[] { 
		BlockFace.EAST, 
		BlockFace.WEST, 
		BlockFace.NORTH, 
		BlockFace.SOUTH 
	};
	public static ArrayList<Material> playlistOrder;
	public static HashMap<Material, Integer> recordDurations = new HashMap<Material, Integer>();
	static {
		recordDurations.put(Material.GOLD_RECORD,(2 * 60) + 58); 
		recordDurations.put(Material.GREEN_RECORD,(3 * 60) + 5);
		recordDurations.put(Material.RECORD_3,(5 * 60) + 45); 
		recordDurations.put(Material.RECORD_4,(3 * 60) + 5);  
        recordDurations.put(Material.RECORD_5, (2 * 60) + 54); 
        recordDurations.put(Material.RECORD_6, (3 * 60) + 17); 
        recordDurations.put(Material.RECORD_7, (1 * 60) + 36); 
        recordDurations.put(Material.RECORD_8, (2 * 60) + 30); 
        recordDurations.put(Material.RECORD_9, (3 * 60) + 8);  
        recordDurations.put(Material.RECORD_10, (4 * 60) + 11); 
        recordDurations.put(Material.RECORD_11, (1 * 60) + 11); 
		recordDurations.put(Material.RECORD_12, (3 * 60) + 55);
		playlistOrder = new ArrayList<Material>(recordDurations.size());
		for (Material m: recordDurations.keySet()) {
			playlistOrder.add(m);
		}
	};

    @Override
	public void onEnable() {
		// TODO Auto-generated method stub
    	loadData();
		getServer().getPluginManager().registerEvents(this, this);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			@Override
			public void run() {
				for (LoopingJukebox jb: LoopingJukebox.jukeboxMap.values()) {
					jb.doLoop();
				}
			}
		}, 40, 40);
	}
	
	
	private void loadData() {
		World w = null;
		Location l = null;
		
		for (String s: getConfig().getStringList("jukeboxes")) {
			getLogger().info("initializing jukebox@" + s);
			String[] locParts = s.split("_");
			w = getServer().getWorld(locParts[0]);
			l = new Location(w, Double.parseDouble(locParts[1]), 
								Double.parseDouble(locParts[2]),
								Double.parseDouble(locParts[3]));
			LoopingJukebox.jukeboxMap.put(l, LoopingJukebox.getAt(this, l));
		}
	}
	
	private void saveData() {
		List<String> boxlist = new ArrayList<String>(LoopingJukebox.jukeboxMap.keySet().size());
		for (Location l: LoopingJukebox.jukeboxMap.keySet()){
			boxlist.add(l.getWorld().getName() + "_" + l.getBlockX() + "_" + l.getBlockY() + "_" + l.getBlockZ());
		}
		getConfig().set("jukeboxes", boxlist);
		saveConfig();
	}
	
	@Override
	public void onDisable() {
		saveData();
		super.onDisable();
	}

	@EventHandler(ignoreCancelled=true)
	public void onInteractJukebox(PlayerInteractEvent e) {
		if (!e.getPlayer().hasPermission("jukeloop.use")) return;
		if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getClickedBlock().getType() == Material.JUKEBOX) {
			LoopingJukebox jb = LoopingJukebox.getAt(this, e.getClickedBlock().getLocation());
			if (jb != null) {
				jb.onEject();
				jb.doLoop();
			}
		} else if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getType() == Material.JUKEBOX && 
				 recordDurations.containsKey(e.getPlayer().getItemInHand().getType())) {
			
			Jukebox box = (Jukebox)e.getClickedBlock().getState();
			LoopingJukebox jb = LoopingJukebox.getAt(this, box.getLocation());
			Material record = e.getPlayer().getItemInHand().getType();
			if (!box.isPlaying()) {
				getLogger().info("Wasn't Playing");
				e.setCancelled(true);
				box.setPlaying(record);
				e.getPlayer().setItemInHand(new ItemStack(Material.AIR));
				jb.onInsert(record);
			} else {
				getLogger().info("Was Playing");
				jb.onEject();
			}
		}
	}
}
