package com.dre.brewery;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Stairs;
import org.bukkit.plugin.Plugin;

import com.dre.brewery.integration.GriefPreventionBarrel;
import com.dre.brewery.integration.LWCBarrel;
import com.dre.brewery.integration.LogBlockBarrel;
import com.dre.brewery.integration.WGBarrel;

import org.apache.commons.lang.ArrayUtils;

public class Barrel {

	public static CopyOnWriteArrayList<Barrel> barrels = new CopyOnWriteArrayList<Barrel>();

	private Block spigot;
	private int[] woodsloc = null; // location of wood Blocks
	private int[] stairsloc = null; // location of stair Blocks
	private byte signoffset;
	private boolean checked = false;
	private Inventory inventory;
	private float time;

	public Barrel(Block spigot, byte signoffset) {
		this.spigot = spigot;
		this.signoffset = signoffset;
	}

	// load from file
	public Barrel(Block spigot, byte sign, String[] st, String[] wo, Map<String, Object> items, float time) {
		this.spigot = spigot;
		this.signoffset = sign;
		if (isLarge()) {
			this.inventory = org.bukkit.Bukkit.createInventory(null, 27, P.p.languageReader.get("Etc_Barrel"));
		} else {
			this.inventory = org.bukkit.Bukkit.createInventory(null, 9, P.p.languageReader.get("Etc_Barrel"));
		}
		if (items != null) {
			for (String slot : items.keySet()) {
				if (items.get(slot) instanceof ItemStack) {
					this.inventory.setItem(P.p.parseInt(slot), (ItemStack) items.get(slot));
				}
			}
		}
		this.time = time;

		int i = 0;
		if (wo.length > 1) {
			woodsloc = new int[wo.length];
			for (String wos : wo) {
				woodsloc[i] = P.p.parseInt(wos);
				i++;
			}
			i = 0;
		}
		if (st.length > 1) {
			stairsloc = new int[st.length];
			for (String sts : st) {
				stairsloc[i] = P.p.parseInt(sts);
				i++;
			}
		}

		if (woodsloc == null && stairsloc == null) {
			Block broken = getBrokenBlock(true);
			if (broken != null) {
				remove(broken, null);
				return;
			}
		}

		barrels.add(this);
	}

	public static void onUpdate() {
		Block broken;
		for (Barrel barrel : barrels) {
			if (!barrel.checked) {
				broken = barrel.getBrokenBlock(false);
				if (broken != null) {
					// remove the barrel if it was destroyed
					barrel.willDestroy();
					barrel.remove(broken, null);
					continue;
				} else {
					// Dont check this barrel again, its enough to check it once after every restart
					// as now this is only the backup if we dont register the barrel breaking, as sample
					// when removing it with some world editor
					barrel.checked = true;
				}
			}

			// Minecraft day is 20 min, so add 1/20 to the time every minute
			barrel.time += (1.0 / 20.0);
		}
	}

	public boolean hasPermsOpen(Player player, PlayerInteractEvent event) {
		if (isLarge()) {
			if (!player.hasPermission("brewery.openbarrel.big")) {
				P.p.msg(player, P.p.languageReader.get("Error_NoBarrelAccess"));
				return false;
			}
		} else {
			if (!player.hasPermission("brewery.openbarrel.small")) {
				P.p.msg(player, P.p.languageReader.get("Error_NoBarrelAccess"));
				return false;
			}
		}

		if (P.p.useWG) {
			Plugin plugin = P.p.getServer().getPluginManager().getPlugin("WorldGuard");
			if (plugin != null) {
				try {
					if (!WGBarrel.checkAccess(player, spigot, plugin)) {
						return false;
					}
				} catch (Exception e) {
					P.p.errorLog("Failed to Check WorldGuard for Barrel Open Permissions!");
					P.p.errorLog("Brewery was tested with version 5.8 of WorldGuard!");
					e.printStackTrace();
					P.p.msg(player, "&cError opening Barrel, please report to an Admin!");
					return false;
				}
			}
		}

		if (P.p.useGP) {
			if (P.p.getServer().getPluginManager().isPluginEnabled("GriefPrevention")) {
				try {
					if (!GriefPreventionBarrel.checkAccess(player, spigot)) {
						return false;
					}
				} catch (Exception e) {
					P.p.errorLog("Failed to Check GriefPrevention for Barrel Open Permissions!");
					P.p.errorLog("Brewery only works with the latest release of GriefPrevention (7.8)");
					e.printStackTrace();
					P.p.msg(player, "&cError opening Barrel, please report to an Admin!");
					return false;
				}
			}
		}

		if (event != null && P.p.useLWC) {
			Plugin plugin = P.p.getServer().getPluginManager().getPlugin("LWC");
			if (plugin != null) {

				// If the Clicked Block was the Sign, LWC already knows and we dont need to do anything here
				if (!isSign(event.getClickedBlock())) {
					Block sign = getSignOfSpigot();
					// If the Barrel does not have a Sign, it cannot be locked
					if (!sign.equals(event.getClickedBlock())) {
						try {
							return LWCBarrel.checkAccess(player, sign, event, plugin);
						} catch (Exception e) {
							P.p.errorLog("Failed to Check LWC for Barrel Open Permissions!");
							P.p.errorLog("Brewery was tested with version 4.3.1 of LWC!");
							e.printStackTrace();
							P.p.msg(player, "&cError opening Barrel, please report to an Admin!");
							return false;
						}
					}
				}
			}
		}

		return true;
	}

	// Ask for permission to destroy barrel, remove protection if has
	public boolean hasPermsDestroy(Player player) {
		if (player == null) {
			willDestroy();
			return true;
		}
		if (P.p.useLWC) {
			try {
				return LWCBarrel.checkDestroy(player, this);
			} catch (Exception e) {
				P.p.errorLog("Failed to Check LWC for Barrel Break Permissions!");
				e.printStackTrace();
				P.p.msg(player, "&cError breaking Barrel, please report to an Admin!");
				return false;
			}
		}

		return true;
	}

	// If something other than the Player is destroying the barrel, inform protection plugins
	public void willDestroy() {
		if (P.p.useLWC) {
			try {
				LWCBarrel.remove(this);
			} catch (Exception e) {
				P.p.errorLog("Failed to Remove LWC Lock from Barrel!");
				e.printStackTrace();
			}
		}
	}

	// player opens the barrel
	public void open(Player player) {
		if (inventory == null) {
			if (isLarge()) {
				inventory = org.bukkit.Bukkit.createInventory(null, 27, P.p.languageReader.get("Etc_Barrel"));
			} else {
				inventory = org.bukkit.Bukkit.createInventory(null, 9, P.p.languageReader.get("Etc_Barrel"));
			}
		} else {
			if (time > 0) {
				// if nobody has the inventory opened
				if (inventory.getViewers().isEmpty()) {
					// if inventory contains potions
					if (inventory.contains(Material.POTION)) {
						byte wood = getWood();
						long loadTime = System.nanoTime();
						for (ItemStack item : inventory.getContents()) {
							if (item != null) {
								Brew brew = Brew.get(item);
								if (brew != null) {
									brew.age(item, time, wood);
								}
							}
						}
						loadTime = System.nanoTime() - loadTime;
						float ftime = (float) (loadTime / 1000000.0);
						P.p.debugLog("opening Barrel with potions (" + ftime + "ms)");
					}
				}
			}
		}
		// reset barreltime, potions have new age
		time = 0;

		if (P.p.useLB) {
			try {
				LogBlockBarrel.openBarrel(player, inventory, spigot.getLocation());
			} catch (Exception e) {
				P.p.errorLog("Failed to Log Barrel to LogBlock!");
				P.p.errorLog("Brewery was tested with version 1.80 of LogBlock!");
				e.printStackTrace();
			}
		}
		player.openInventory(inventory);
	}

	// Returns true if this Block is part of this Barrel
	public boolean hasBlock(Block block) {
		if (block != null) {
			if (block.getType().equals(Material.WOOD)) {
				if (hasWoodBlock(block)) {
					return true;
				}
			} else if (isStairs(block.getType())) {
				if (hasStairsBlock(block)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean hasWoodBlock(Block block) {
		if (woodsloc != null) {
			if (spigot.getWorld() != null && spigot.getWorld().equals(block.getWorld())) {
				if (woodsloc.length > 2) {
					int x = block.getX();
					if (Math.abs(x - woodsloc[0]) < 10) {
						for (int i = 0; i < woodsloc.length - 2; i += 3) {
							if (woodsloc[i] == x) {
								if (woodsloc[i + 1] == block.getY()) {
									if (woodsloc[i + 2] == block.getZ()) {
										return true;
									}
								}
							}
						}
					}
				}
			}
		}
		return false;
	}

	public boolean hasStairsBlock(Block block) {
		if (stairsloc != null) {
			if (spigot.getWorld() != null && spigot.getWorld().equals(block.getWorld())) {
				if (stairsloc.length > 2) {
					int x = block.getX();
					if (Math.abs(x - stairsloc[0]) < 10) {
						for (int i = 0; i < stairsloc.length - 2; i += 3) {
							if (stairsloc[i] == x) {
								if (stairsloc[i + 1] == block.getY()) {
									if (stairsloc[i + 2] == block.getZ()) {
										return true;
									}
								}
							}
						}
					}
				}
			}
		}
		return false;
	}

	// Returns true if the Offset of the clicked Sign matches the Barrel.
	// This prevents adding another sign to the barrel and clicking that.
	public boolean isSignOfBarrel(byte offset) {
		return offset == 0 || signoffset == 0 || signoffset == offset;
	}

	// Get the Barrel by Block, null if that block is not part of a barrel
	public static Barrel get(Block block) {
		if (block != null) {
			switch (block.getType()) {
			case FENCE:
			case NETHER_FENCE:
			case SIGN:
			case WALL_SIGN:
				Barrel barrel = getBySpigot(block);
				if (barrel != null) {
					return barrel;
				}
				return null;
			case WOOD:
			case WOOD_STAIRS:
			case ACACIA_STAIRS:
			case BIRCH_WOOD_STAIRS:
			case DARK_OAK_STAIRS:
			case JUNGLE_WOOD_STAIRS:
			case SPRUCE_WOOD_STAIRS:
				Barrel barrel2 = getByWood(block);
				if (barrel2 != null) {
					return barrel2;
				}
			default:
				break;
			}
		}
		return null;
	}

	// Get the Barrel by Sign or Spigot (Fastest)
	public static Barrel getBySpigot(Block sign) {
		// convert spigot if neccessary
		Block spigot = getSpigotOfSign(sign);

		byte signoffset = 0;
		if (!spigot.equals(sign)) {
			signoffset = (byte) (sign.getY() - spigot.getY());
		}

		for (Barrel barrel : barrels) {
			if (barrel.isSignOfBarrel(signoffset)) {
				if (barrel.spigot.equals(spigot)) {
					if (barrel.signoffset == 0 && signoffset != 0) {
						// Barrel has no signOffset even though we clicked a sign, may be old
						barrel.signoffset = signoffset;
					}
					return barrel;
				}
			}
		}
		return null;
	}

	// Get the barrel by its corpus (Wood Planks, Stairs)
	public static Barrel getByWood(Block wood) {
		if (wood.getType().equals(Material.WOOD)) {
			for (Barrel barrel : barrels) {
				if (barrel.hasWoodBlock(wood)) {
					return barrel;
				}
			}
		} else if (isStairs(wood.getType())) {
			for (Barrel barrel : Barrel.barrels) {
				if (barrel.hasStairsBlock(wood)) {
					return barrel;
				}
			}
		}
		return null;
	}

	// creates a new Barrel out of a sign
	public static boolean create(Block sign, Player player) {
		Block spigot = getSpigotOfSign(sign);

		byte signoffset = 0;
		if (!spigot.equals(sign)) {
			signoffset = (byte) (sign.getY() - spigot.getY());
		}

		Barrel barrel = getBySpigot(spigot);
		if (barrel == null) {
			barrel = new Barrel(spigot, signoffset);
			if (barrel.getBrokenBlock(true) == null) {
				if (isSign(spigot)) {
					if (!player.hasPermission("brewery.createbarrel.small")) {
						P.p.msg(player, P.p.languageReader.get("Perms_NoSmallBarrelCreate"));
						return false;
					}
				} else {
					if (!player.hasPermission("brewery.createbarrel.big")) {
						P.p.msg(player, P.p.languageReader.get("Perms_NoBigBarrelCreate"));
						return false;
					}
				}
				barrels.add(barrel);
				return true;
			}
		} else {
			if (barrel.signoffset == 0 && signoffset != 0) {
				barrel.signoffset = signoffset;
				return true;
			}
		}
		return false;
	}

	// removes a barrel, throwing included potions to the ground
	public void remove(Block broken, Player breaker) {
		if (inventory != null) {
			for (HumanEntity human : inventory.getViewers()) {
				human.closeInventory();
			}
			ItemStack[] items = inventory.getContents();
			if (P.p.useLB && breaker != null) {
				try {
					LogBlockBarrel.breakBarrel(breaker.getName(), items, spigot.getLocation());
				} catch (Exception e) {
					P.p.errorLog("Failed to Log Barrel-break to LogBlock!");
					P.p.errorLog("Brewery was tested with version 1.80 of LogBlock!");
					e.printStackTrace();
				}
			}
			for (ItemStack item : items) {
				if (item != null) {
					Brew brew = Brew.get(item);
					if (brew != null) {
						// Brew before throwing
						brew.age(item, time, getWood());
						PotionMeta meta = (PotionMeta) item.getItemMeta();
						if (Brew.hasColorLore(meta)) {
							brew.convertLore(meta, false);
							item.setItemMeta(meta);
						}
					}
					// "broken" is the block that destroyed, throw them there!
					if (broken != null) {
						broken.getWorld().dropItem(broken.getLocation(), item);
					} else {
						spigot.getWorld().dropItem(spigot.getLocation(), item);
					}
				}
			}
		}

		barrels.remove(this);
	}

	//unloads barrels that are in a unloading world
	public static void onUnload(String name) {
		for (Barrel barrel : barrels) {
			if (barrel.spigot.getWorld().getName().equals(name)) {
				barrels.remove(barrel);
			}
		}
	}

	// If the Sign of a Large Barrel gets destroyed, set signOffset to 0
	public void destroySign() {
		signoffset = 0;
	}

	// Saves all data
	public static void save(ConfigurationSection config, ConfigurationSection oldData) {
		P.p.createWorldSections(config);

		if (!barrels.isEmpty()) {
			int id = 0;
			for (Barrel barrel : barrels) {

				String worldName = barrel.spigot.getWorld().getName();
				String prefix;

				if (worldName.startsWith("DXL_")) {
					prefix = P.p.getDxlName(worldName) + "." + id;
				} else {
					prefix = barrel.spigot.getWorld().getUID().toString() + "." + id;
				}

				// block: x/y/z
				config.set(prefix + ".spigot", barrel.spigot.getX() + "/" + barrel.spigot.getY() + "/" + barrel.spigot.getZ());

				if (barrel.signoffset != 0) {
					config.set(prefix + ".sign", barrel.signoffset);
				}
				if (barrel.stairsloc != null && barrel.stairsloc.length > 0) {
					StringBuilder st = new StringBuilder();
					for (int i : barrel.stairsloc) {
						st.append(i).append(",");
					}
					config.set(prefix + ".st", st.substring(0, st.length() - 1));
				}
				if (barrel.woodsloc != null && barrel.woodsloc.length > 0) {
					StringBuilder wo = new StringBuilder();
					for (int i : barrel.woodsloc) {
						wo.append(i).append(",");
					}
					config.set(prefix + ".wo", wo.substring(0, wo.length() - 1));
				}

				if (barrel.inventory != null) {
					int slot = 0;
					ItemStack item;
					ConfigurationSection invConfig = null;
					while (slot < barrel.inventory.getSize()) {
						item = barrel.inventory.getItem(slot);
						if (item != null) {
							if (invConfig == null) {
								if (barrel.time != 0) {
									config.set(prefix + ".time", barrel.time);
								}
								invConfig = config.createSection(prefix + ".inv");
							}
							// ItemStacks are configurationSerializeable, makes them
							// really easy to save
							invConfig.set(slot + "", item);
						}

						slot++;
					}
				}

				id++;
			}
		}
		// also save barrels that are not loaded
		if (oldData != null){
			for (String uuid : oldData.getKeys(false)) {
				if (!config.contains(uuid)) {
					config.set(uuid, oldData.get(uuid));
				}
			}
		}
	}

	// direction of the barrel from the spigot
	public static int getDirection(Block spigot) {
		int direction = 0;// 1=x+ 2=x- 3=z+ 4=z-
		Material type = spigot.getRelative(0, 0, 1).getType();
		if (type == Material.WOOD || isStairs(type)) {
			direction = 3;
		}
		type = spigot.getRelative(0, 0, -1).getType();
		if (type == Material.WOOD || isStairs(type)) {
			if (direction == 0) {
				direction = 4;
			} else {
				return 0;
			}
		}
		type = spigot.getRelative(1, 0, 0).getType();
		if (type == Material.WOOD || isStairs(type)) {
			if (direction == 0) {
				direction = 1;
			} else {
				return 0;
			}
		}
		type = spigot.getRelative(-1, 0, 0).getType();
		if (type == Material.WOOD || isStairs(type)) {
			if (direction == 0) {
				direction = 2;
			} else {
				return 0;
			}
		}
		return direction;
	}

	// is this a Large barrel?
	public boolean isLarge() {
		return !isSign(spigot);
	}

	// true for small barrels
	public static boolean isSign(Block spigot) {
		return spigot.getType() == Material.SIGN || spigot.getType() == Material.SIGN_POST;
	}

	// woodtype of the block the spigot is attached to
	public byte getWood() {
		int direction = getDirection(this.spigot);// 1=x+ 2=x- 3=z+ 4=z-
		Block wood;
		if (direction == 0) {
			return 0;
		} else if (direction == 1) {
			wood = this.spigot.getRelative(1, 0, 0);
		} else if (direction == 2) {
			wood = this.spigot.getRelative(-1, 0, 0);
		} else if (direction == 3) {
			wood = this.spigot.getRelative(0, 0, 1);
		} else {
			wood = this.spigot.getRelative(0, 0, -1);
		}
		if (wood.getType() == Material.WOOD) {
			byte data = wood.getData();
			if (data == 0x0) {
				return 2;
			} else if (data == 0x1) {
				return 4;
			} else if (data == 0x2) {
				return 1;
			} else {
				return 3;
			}
		}
		if (wood.getType() == Material.WOOD_STAIRS) {
			return 2;
		}
		if (wood.getType() == Material.SPRUCE_WOOD_STAIRS) {
			return 4;
		}
		if (wood.getType() == Material.BIRCH_WOOD_STAIRS) {
			return 1;
		}
		if (wood.getType() == Material.JUNGLE_WOOD_STAIRS) {
			return 3;
		}
		return 0;
	}

	// returns the Sign of a large barrel, the spigot if there is none
	public Block getSignOfSpigot() {
		if (signoffset != 0) {
			if (isSign(spigot)) {
				return spigot;
			}

			if (isSign(spigot.getRelative(0, signoffset, 0))) {
				return spigot.getRelative(0, signoffset, 0);
			} else {
				signoffset = 0;
			}
		}
		return spigot;
	}

	// returns the fence above/below a block, itself if there is none
	public static Block getSpigotOfSign(Block block) {

		int y = -2;
		while (y <= 1) {
			// Fence and Netherfence
			Block relative = block.getRelative(0, y, 0);
			if (relative.getType() == Material.FENCE || relative.getType() == Material.NETHER_FENCE) {
				return (relative);
			}
			y++;
		}
		return block;
	}

	public static boolean isStairs(Material material) {
		return material == Material.WOOD_STAIRS || material == Material.SPRUCE_WOOD_STAIRS || material == Material.BIRCH_WOOD_STAIRS || material == Material.JUNGLE_WOOD_STAIRS || material == Material.ACACIA_STAIRS || material == Material.DARK_OAK_STAIRS;
	}

	// returns null if Barrel is correctly placed; the block that is missing when not
	// the barrel needs to be formed correctly
	// flag force to also check if chunk is not loaded
	public Block getBrokenBlock(boolean force) {
		if (force || spigot.getChunk().isLoaded()) {
			spigot = getSpigotOfSign(spigot);
			if (isSign(spigot)) {
				return checkSBarrel();
			} else {
				return checkLBarrel();
			}
		}
		return null;
	}

	public Block checkSBarrel() {
		int direction = getDirection(spigot);// 1=x+ 2=x- 3=z+ 4=z-
		if (direction == 0) {
			return spigot;
		}
		int startX;
		int startZ;
		int endX;
		int endZ;

		ArrayList<Integer> stairs = new ArrayList<Integer>();

		if (direction == 1) {
			startX = 1;
			endX = startX + 1;
			startZ = -1;
			endZ = 0;
		} else if (direction == 2) {
			startX = -2;
			endX = startX + 1;
			startZ = 0;
			endZ = 1;
		} else if (direction == 3) {
			startX = 0;
			endX = 1;
			startZ = 1;
			endZ = startZ + 1;
		} else {
			startX = -1;
			endX = 0;
			startZ = -2;
			endZ = startZ + 1;
		}

		Material type;
		int x = startX;
		int y = 0;
		int z = startZ;
		while (y <= 1) {
			while (x <= endX) {
				while (z <= endZ) {
					Block block = spigot.getRelative(x, y, z);
					type = block.getType();

					if (isStairs(type)) {
						if (y == 0) {
							// stairs have to be upside down
							MaterialData data = block.getState().getData();
							if(data instanceof Stairs){
								Stairs sdata = (Stairs) data;
								if (sdata.getFacing() == BlockFace.DOWN) {
									return block;
								}
							}
						}
						stairs.add(block.getX());
						stairs.add(block.getY());
						stairs.add(block.getZ());
						z++;
					} else {
						return spigot.getRelative(x, y, z);
					}
				}
				z = startZ;
				x++;
			}
			z = startZ;
			x = startX;
			y++;
		}
		stairsloc = ArrayUtils.toPrimitive(stairs.toArray(new Integer[stairs.size()]));
		return null;
	}

	public Block checkLBarrel() {
		int direction = getDirection(spigot);// 1=x+ 2=x- 3=z+ 4=z-
		if (direction == 0) {
			return spigot;
		}
		int startX;
		int startZ;
		int endX;
		int endZ;

		ArrayList<Integer> stairs = new ArrayList<Integer>();
		ArrayList<Integer> woods = new ArrayList<Integer>();

		if (direction == 1) {
			startX = 1;
			endX = startX + 3;
			startZ = -1;
			endZ = 1;
		} else if (direction == 2) {
			startX = -4;
			endX = startX + 3;
			startZ = -1;
			endZ = 1;
		} else if (direction == 3) {
			startX = -1;
			endX = 1;
			startZ = 1;
			endZ = startZ + 3;
		} else {
			startX = -1;
			endX = 1;
			startZ = -4;
			endZ = startZ + 3;
		}

		Material type;
		int x = startX;
		int y = 0;
		int z = startZ;
		while (y <= 2) {
			while (x <= endX) {
				while (z <= endZ) {
					Block block = spigot.getRelative(x, y, z);
					type = block.getType();
					if (direction == 1 || direction == 2) {
						if (y == 1 && z == 0) {
							if (x == -1 || x == -4 || x == 1 || x == 4) {
								woods.add(block.getX());
								woods.add(block.getY());
								woods.add(block.getZ());
							}
							z++;
							continue;
						}
					} else {
						if (y == 1 && x == 0) {
							if (z == -1 || z == -4 || z == 1 || z == 4) {
								woods.add(block.getX());
								woods.add(block.getY());
								woods.add(block.getZ());
							}
							z++;
							continue;
						}
					}
					if (type == Material.WOOD || isStairs(type)) {
						if (type == Material.WOOD) {
							woods.add(block.getX());
							woods.add(block.getY());
							woods.add(block.getZ());
						} else {
							stairs.add(block.getX());
							stairs.add(block.getY());
							stairs.add(block.getZ());
						}
						z++;
					} else {
						return block;
					}
				}
				z = startZ;
				x++;
			}
			z = startZ;
			x = startX;
			y++;
		}
		stairsloc = ArrayUtils.toPrimitive(stairs.toArray(new Integer[stairs.size()]));
		woodsloc = ArrayUtils.toPrimitive(woods.toArray(new Integer[woods.size()]));

		return null;
	}

}
