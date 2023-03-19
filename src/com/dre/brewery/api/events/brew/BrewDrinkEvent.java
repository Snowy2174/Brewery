package com.dre.brewery.api.events.brew;

import com.dre.brewery.BPlayer;
import com.dre.brewery.Brew;
import com.dre.brewery.utility.PermissionUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * A Player Drinks a Brew.
 * <p>The amount of alcohol and quality that will be added to the player can be get/set here
 * <p>If cancelled the drinking will fail silently
 */
public class BrewDrinkEvent extends BrewEvent implements Cancellable {
	private static final HandlerList handlers = new HandlerList();
	private final Player player;
	private final BPlayer bPlayer;
	private int alc;
	private int quality;
	private boolean cancelled;

	public BrewDrinkEvent(Brew brew, ItemMeta meta, Player player, BPlayer bPlayer) {
		super(brew, meta);
		this.player = player;
		this.bPlayer = bPlayer;
		alc = calcAlcWSensitivity(brew.getOrCalcAlc());
		quality = brew.getQuality();
	}

	/**
	 * Calculate the Alcohol to add to the player using his sensitivity permission (if existing)
	 *
	 * <p>If the player has been given the brewery.sensitive.xx permission, will factor in the sensitivity to the given alcohol amount.
	 * <p>Will return the calculated value without changing the event
	 *
	 * @param alc The base amount of alcohol
	 * @return The amount of alcohol given the players alcohol-sensitivity
	 */
	@Contract(pure = true)
	public int calcAlcWSensitivity(int alc) {
		int sensitive = PermissionUtil.getDrinkSensitive(player);
		if (sensitive == 0) {
			alc = 0;
		} else if (sensitive > 0) {
			alc *= ((float) sensitive) / 100f;
		}
		return alc;
	}

	public Player getPlayer() {
		return player;
	}

	public BPlayer getbPlayer() {
		return bPlayer;
	}

	public int getAddedAlcohol() {
		return alc;
	}

	public void setAddedAlcohol(int alc) {
		this.alc = alc;
	}

	public int getQuality() {
		return quality;
	}

	public void setQuality(int quality) {
		if (quality > 10 || quality < 0) {
			throw new IllegalArgumentException("Quality must be in range from 0 to 10");
		}
		this.quality = quality;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	// Required by Bukkit
	public static HandlerList getHandlerList() {
		return handlers;
	}
}
