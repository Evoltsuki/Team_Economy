package com.evolt.teamecon.economy;

import com.evolt.teamecon.price.PriceService;
import net.minecraft.world.item.Item;

/**
 * Central economy operations: selling items to the market, buying from the shop, refunds.
 * Every operation goes through the ledger so leaderboards stay accurate.
 */
public record EconomySnapshot(long unitValue, long total, int quantity) {
}
