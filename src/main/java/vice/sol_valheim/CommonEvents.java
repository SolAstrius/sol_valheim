package vice.sol_valheim;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor;

@EventBusSubscriber(modid = SOLValheim.MOD_ID)
public class CommonEvents {
    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
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

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide())
            return;

        var accessor = (PlayerEntityMixinDataAccessor) player;
        accessor.sol_valheim$getFoodData().clear();
        accessor.sol_valheim$syncFoodData();
    }
}
