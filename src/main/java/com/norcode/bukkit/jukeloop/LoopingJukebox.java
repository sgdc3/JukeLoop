package com.norcode.bukkit.jukeloop;

import java.util.HashMap;
import java.util.LinkedHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Hopper;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Attachable;

public class LoopingJukebox {
    private Location location;
    private Jukebox jukebox;
    private Chest chest;
    private JukeLoopPlugin plugin;
    private int startedAt = -1;
    public boolean isDead = false;
    private int chestSlot = -1;
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

    public Location getLocation() {
        return location;
    }

    public LoopingJukebox(JukeLoopPlugin plugin, Location location) {
        this.location = location;
        this.plugin = plugin;
    }

    public void log(String msg) {
        if (plugin.debugMode) {
            plugin.getLogger().info(
                    "[Jukebox@" + location.getWorld().getName() + " "
                            + location.getBlockX() + " " + location.getBlockY()
                            + " " + location.getBlockZ() + "] " + msg);
        }
    }

    public Jukebox getJukebox() {
        try {
            return (Jukebox) this.location.getBlock().getState();
        } catch (ClassCastException ex) {
            return null;
        }
    }

    public Chest getChest() {
        if (this.chest == null) {
            return null;
        } else {
            try {
                return (Chest) this.chest.getBlock().getState();
            } catch (ClassCastException ex) {
            }
        }
        return null;
    }

    public boolean validate() {
        try {
            this.jukebox = (Jukebox) this.location.getBlock().getState();
        } catch (ClassCastException ex) {
            return false;
        } catch (NullPointerException ex) {
            return false;
        }
        this.chest = null;
        BlockState rel = null;
        for (BlockFace f: JukeLoopPlugin.directions) {
            try {
                rel = (BlockState)this.jukebox.getBlock().getRelative(f).getState();
                this.chest = (Chest) rel;
                if (!containsRecords(this.chest.getInventory())) {
                    log(this.chest + " does not contain records. skipping.");
                    continue;
                }
                this.chest = (Chest) rel;
                break;
            } catch (ClassCastException ex) {
                log(ex.getMessage());
                continue;
            }
        }
        return true;
    }

    public boolean containsRecords(Inventory inv) {
        for (ItemStack s : inv.getContents()) {
            if (s != null && JukeLoopPlugin.recordDurations.keySet().contains(s.getType())) {
                return true;
            }
        }
        return false;
    }

    public boolean playersNearby() {
        double dist;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            try {
                dist = getJukebox().getLocation().distance(p.getLocation());
                plugin.debug("distance from " + getJukebox().getLocation() + " to " + p.getLocation());
                if (dist <= 64) {
                    return true;
                }
            } catch (IllegalArgumentException ex) { // cross-world.
        	plugin.debug("Cross world distance-check.");
            }
        }

        return false;
    }

    public void doLoop() {
        
        Jukebox jukebox = getJukebox();
        if (jukebox == null) {
            this.isDead = true;
            log("doLoop:Died.");
            return;
        }

        if (!getJukebox().isPlaying()) {
            log("doLoop:not playing.");
            return;
        }

        int now = (int) (System.currentTimeMillis() / 1000);
        Material record = jukebox.getPlaying();
        Integer duration = JukeLoopPlugin.recordDurations.get(record);
        if (duration != null) {
            if (now - startedAt > duration) {
                if (!playersNearby()) {
                    log("doLoop:No player nearby.");
                    return;
                }
                if (!putInHopper()) {
                    if (!putInChest()) {
                        log("doLoop:Couldn't put " + record + " anywhere, repeating.");
                        jukebox.setPlaying(record);
                        onInsert(record);
                        return;
                    }
                }
                if (!takeFromHopper()) {
                    if (!takeFromChest()) {
                        log("This shouldn't happen");
                    }
                }
            }
        }
    }

    private static final BlockFace[] hopperDirections = new BlockFace[] {
        BlockFace.DOWN,
        BlockFace.UP,
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.WEST,
        BlockFace.EAST
    };

    private boolean putInHopper() {
        BlockState blockBelow = jukebox.getBlock().getRelative(BlockFace.DOWN).getState();
        if (blockBelow.getType().equals(Material.HOPPER)) {
            Hopper hopper = (Hopper)blockBelow;
            if (hopper.getInventory().addItem(new ItemStack(jukebox.getPlaying())).isEmpty()) {
                jukebox.setPlaying(null);
                return true;
            }
        }
        return false;
    }
    private boolean putInChest() {
        Chest chest = getChest();
        if (chest != null) {
            Inventory inv = chest.getInventory();
            if (chestSlot == -1 || chestSlot > chest.getInventory().getSize()-1 ||(inv.getItem(chestSlot) != null && !inv.getItem(chestSlot).getType().equals(Material.AIR))) {
                chestSlot = inv.firstEmpty();
            }
            if (chestSlot >= 0) {
                log("Placing " + jukebox.getPlaying() + " in slot " + chestSlot + " of chest@"+chest.getLocation().getBlockX() + ","+chest.getLocation().getBlockY() +","+ chest.getLocation().getBlockZ());
                inv.setItem(chestSlot, new ItemStack(jukebox.getPlaying()));
                jukebox.setPlaying(null);
                return true;
            } else {
                log("Failed to place " + jukebox.getPlaying() + " in chest@"+chest.getLocation().getBlockX() + ","+chest.getLocation().getBlockY() +","+ chest.getLocation().getBlockZ() + " there is no free slot.");
            }
        } else {
            log("putInChest:there is no chest.");
        }
        return false;
    }

    public boolean takeFromChest() {
        Chest chest = getChest();
        if (chest != null) {
            Inventory inv = chest.getInventory();
            int i = chestSlot + 1;
            while (i != chestSlot) {
                if (i > inv.getSize()-1) { 
                    i = 0;
                }
                ItemStack s = inv.getItem(i);
                if (s != null && JukeLoopPlugin.recordDurations.containsKey(s.getType())) {
                    log("Taking " + s.getType() + " from slot " + i + " of chest@"+chest.getLocation().getBlockX() + ","+chest.getLocation().getBlockY() +","+ chest.getLocation().getBlockZ());
                    jukebox.setPlaying(s.getType());
                    onInsert(jukebox.getPlaying());
                    inv.setItem(i, null);
                    chestSlot = i;
                    return true;
                }
                ++i;
            }
        } else {
            log("putInChest:there is no chest.");
        }
        log("Failed to take a disc from the chest, there wasn't one.");
        return false;
    }

    private boolean takeFromHopper() {
        for (BlockFace dir: JukeLoopPlugin.directions) {
            if (!dir.equals(BlockFace.DOWN)) {
        	plugin.debug("Checking " + dir + " for hopper.");
                BlockState bs = this.getJukebox().getBlock().getRelative(dir).getState();
                if (bs.getType().equals(Material.HOPPER)) {
                    plugin.debug("Found Hopper.");
                    byte data = ((Hopper) bs).getData().getData();
                    BlockFace attachedFace = hopperDirections[data & 7];
                    boolean isAttached = attachedFace.getOppositeFace().equals(dir);
                    Inventory inv = ((Hopper) bs).getInventory();
                    if (isAttached) {
                        for (int i=0;i<inv.getSize();i++) {
                            ItemStack s = inv.getItem(i);
                            if (s != null && JukeLoopPlugin.recordDurations.containsKey(s.getType())) {
                                inv.setItem(i, null);
                                this.getJukebox().setPlaying(s.getType());
                                onInsert(s.getType());
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public void onInsert(Material record) {
        startedAt = (int) (System.currentTimeMillis() / 1000);
    }

    public void onEject() {
        this.startedAt = -1;
    }
}
