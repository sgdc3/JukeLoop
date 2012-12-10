package com.norcode.bukkit.jukeloop;

import java.util.HashMap;
import java.util.LinkedHashMap;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Jukebox;
import org.bukkit.inventory.ItemStack;

public class LoopingJukebox {
	private Location location;
	private Jukebox jukebox;
	private Chest chest;
	private JukeLoopPlugin plugin;
	private int startedAt = -1;
	
	public static LinkedHashMap<Location, LoopingJukebox> jukeboxMap = new LinkedHashMap<Location, LoopingJukebox>();
	public static LoopingJukebox getAt(JukeLoopPlugin plugin, Location loc) {
		LoopingJukebox box = null;
		if (jukeboxMap.containsKey(loc)) {
			box = jukeboxMap.get(loc);
		} else {
			box = new LoopingJukebox(plugin, loc);
		}
		if (box.validate()) {
			jukeboxMap.put(loc, box);
			return box;
		}
		return null;
	}
	
	public LoopingJukebox(JukeLoopPlugin plugin, Location location) {
		this.location = location;
		this.plugin = plugin;
	}
	
	public Jukebox getJukebox() {
		try {
			return (Jukebox)this.location.getBlock().getState();
		} catch (ClassCastException ex) {
			return null;
		}
	}
	
	public Chest getChest() {
		if (this.chest == null) {
			return null;
		} else {
			try {
				return (Chest)this.chest.getBlock().getLocation().getBlock().getState();
			} catch (ClassCastException ex) {}
		}
		return null;
	}
	
	public boolean validate() {
		try {
			this.jukebox = (Jukebox)this.location.getBlock().getState();
		} catch (ClassCastException ex) {
			return false;
		}
		this.chest = null;
		BlockState rel = null;
		for (BlockFace f: plugin.directions) {
			try {
				rel = (BlockState)this.jukebox.getBlock().getRelative(f).getState();
				this.chest = (Chest)rel;
			} catch (ClassCastException ex) {
				continue;
			}
		}
		if (this.chest != null) {
			plugin.getLogger().info("Has a chest!");
		}
		return true;
	}
	
	public void doLoop() {
		Jukebox jukebox = getJukebox();
		if (jukebox == null) {
			plugin.getLogger().info("Jukebox Destroyed, removing from cache");
			this.jukeboxMap.remove(location);
			return;
		}
		if (!getJukebox().isPlaying()) return;
		if (!location.getChunk().isLoaded()) return;
		
		
		int now = (int)(System.currentTimeMillis()/1000);
		Material record = this.jukebox.getPlaying();
		if (now - startedAt > plugin.recordDurations.get(record)) {

			if (this.getChest() == null) {
				loopOneDisc(now);
			} else {
				loopFromChest(now);
			}
		}

	}
	
	
	
	private void loopOneDisc(int now) {
			plugin.getLogger().info("Looping single disc");
			Material record = this.getJukebox().getPlaying();
			this.getJukebox().setPlaying(record);
			onInsert(record);
					
	}
	
	public void onInsert(Material record) {
		startedAt = (int)(System.currentTimeMillis()/1000);
	}
	
	public void onEject() {
		this.startedAt = -1;
	}
	
	private void loopFromChest(int now) {
		plugin.getLogger().info("Looping from chest");
		Jukebox jukebox = getJukebox();
		Material record = jukebox.getPlaying();
		Chest chest = getChest();
		chest.getBlockInventory().addItem(new ItemStack(record));
		Material newRecord = null;
		boolean next = false;
		while (newRecord == null) {
			for (Material m: plugin.recordDurations.keySet()) {
				if (next) {
					if (chest.getBlockInventory().contains(m)) {
						chest.getBlockInventory().removeItem(new ItemStack(m,1));
						newRecord = m;
						next = false;
						break;
					}
				}
				if (m.equals(record)) {
					next = true;
				}
			}
		}
		
		this.startedAt = now;
		jukebox.setPlaying(newRecord);
		
	}
	
}
