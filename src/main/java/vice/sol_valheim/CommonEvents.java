package vice.sol_valheim;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor;

// Records eaten food/drinks into the Valheim food data when an item finishes being used.
// Replaces the original Player#eat / FoodData#eat / ServerPlayer#completeUsingItem mixins with a
// single robust NeoForge hook that covers both solid food and drinks.
@EventBusSubscriber(modid = SOLValheim.MOD_ID)
public class CommonEvents
{
    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event)
    {
        if (!(event.getEntity() instanceof Player player))
            return;

        if (player.level().isClientSide())
            return;

        var accessor = (PlayerEntityMixinDataAccessor) player;
        var data = accessor.sol_valheim$getFoodData();
        var item = event.getItem().getItem();

        if (item == Items.ROTTEN_FLESH) {
            data.clear();
            accessor.sol_valheim$syncFoodData();
            return;
        }

        if (ModConfig.getFoodConfig(item) == null)
            return;

        if (data.canEat(item)) {
            data.eatItem(item);
            accessor.sol_valheim$syncFoodData();
        }
    }
}
