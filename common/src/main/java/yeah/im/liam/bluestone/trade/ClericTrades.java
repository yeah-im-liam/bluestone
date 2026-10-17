package yeah.im.liam.bluestone.trade;

// From 26.1 a data file in data/minecraft/villager_trade does this.
//#if MC < 26.1
//$$ import net.minecraft.util.RandomSource;
//$$ import net.minecraft.world.entity.npc.villager.VillagerProfession;
//$$ import net.minecraft.world.entity.npc.villager.VillagerTrades;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.item.Items;
//$$ import net.minecraft.world.item.trading.MerchantOffer;
//$$ import org.jspecify.annotations.Nullable;
//$$ import yeah.im.liam.bluestone.registry.BluestoneBlocks;
//$$
//$$ /**
//$$  * Clerics sell bluestone dust in place of redstone dust a quarter of the time.
//$$  */
//$$ public final class ClericTrades {
//$$     private ClericTrades() {
//$$     }
//$$
//$$     public static VillagerTrades.ItemListing wrap(VillagerTrades.ItemListing listing) {
        //#if MC >= 1.21.11
        //$$ return (level, trader, random) -> bluestone(listing.getOffer(level, trader, random), random);
        //#else
        //$$ return (trader, random) -> bluestone(listing.getOffer(trader, random), random);
        //#endif
//$$     }
//$$
//$$     // On Fabric the game's own trade lists are changed; NeoForge hands them out in an event instead.
//$$     public static void wrapGameTrades() {
//$$         VillagerTrades.ItemListing[] listings = VillagerTrades.TRADES.get(VillagerProfession.CLERIC).get(1);
//$$         for (int i = 0; i < listings.length; i++) {
//$$             listings[i] = wrap(listings[i]);
//$$         }
//$$     }
//$$
//$$     private static @Nullable MerchantOffer bluestone(@Nullable MerchantOffer offer, RandomSource random) {
//$$         if (offer == null || !offer.getResult().is(Items.REDSTONE) || random.nextFloat() >= 0.25F) {
//$$             return offer;
//$$         }
//$$         // Costs are ItemCosts from 1.20.5 and item stacks before.
//#if MC >= 1.20.5
//$$         return new MerchantOffer(offer.getItemCostA(), offer.getItemCostB(), new ItemStack(BluestoneBlocks.dust(), offer.getResult().getCount()),
//$$             offer.getMaxUses(), offer.getXp(), offer.getPriceMultiplier());
//#else
//$$         return new MerchantOffer(offer.getBaseCostA(), offer.getCostB(), new ItemStack(BluestoneBlocks.dust(), offer.getResult().getCount()),
//$$             offer.getMaxUses(), offer.getXp(), offer.getPriceMultiplier());
//#endif
//$$     }
//$$ }
//#endif
