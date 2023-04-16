package com.dre.brewery.integration.item;

import com.dre.brewery.recipe.PluginItem;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.inventory.ItemStack;

public class ItemsAdderPluginItem extends PluginItem {

	@Override
	public boolean matches(ItemStack item) {
		CustomStack cs = CustomStack.byItemStack(item);
		if(cs == null) return false;

		if(cs.getId().equals(getItemId())){
			return true;
		}

		return false;
	}

	@Override
	public String displayName() {
		return getItemId();
	}
}
