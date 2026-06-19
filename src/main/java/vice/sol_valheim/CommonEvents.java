package vice.sol_valheim;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor;


@EventBusSubscriber(modid = SOLValheim.MOD_ID)
public class CommonEvents {
    private static void createFoodConfig(ItemStack stack) {
        var item = stack.getItem();
        var key = BuiltInRegistries.ITEM.getKey(item);
        var keyString = key.toString();

        FoodProperties food = item == Items.CAKE
            ? new FoodProperties.Builder().nutrition(10).saturationModifier(0.7f).build()
            : stack.get(DataComponents.FOOD);

        if (stack.getUseAnimation() == UseAnim.DRINK) {
            if (keyString.contains("potion")) {
                food = new FoodProperties.Builder().nutrition(4).saturationModifier(0.75f).build();
            } else if (keyString.contains("milk")) {
                food = new FoodProperties.Builder().nutrition(6).saturationModifier(1f).build();
            } else {
                food = new FoodProperties.Builder().nutrition(2).saturationModifier(0.5f).build();
            }
        }

        var cfg = new ModConfig.Common.FoodConfig();
        cfg.nutrition = food.nutrition();
        cfg.healthRegenModifier = 1f;
        // 1.21's FoodProperties.saturation() returns the *absolute* saturation
        // (nutrition * modifier * 2), whereas getTime() expects the original ~0.3-1.0
        // saturation *modifier* (as 1.20.1's getSaturationModifier() returned). Recover the
        // modifier so food durations stay in Valheim's minutes range instead of inflating ~20x.
        cfg.saturationModifier = food.saturation() / (2f * Math.max(1, food.nutrition()));

        if (keyString.startsWith("farmers")) {
            cfg.nutrition = (int) ((cfg.nutrition * 1.25));
            cfg.saturationModifier = cfg.saturationModifier * 1.10f;
            cfg.healthRegenModifier = 1.25f;
        }

        if (keyString.equals("minecraft:golden_apple") || keyString.equals("minecraft:enchanted_golden_apple")) {
            cfg.nutrition = 10;
            cfg.healthRegenModifier = 1.5f;
        }

        SOLValheim.CONFIG.common.foodConfigs.put(key, cfg);
    }

    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        long startTime = System.nanoTime();

        for (var item : BuiltInRegistries.ITEM) {
            var stack = item.getDefaultInstance();
            var isDrink = stack.getUseAnimation() == UseAnim.DRINK;
            var isEdible = stack.has(DataComponents.FOOD);
            var isFood = stack.getItem() == Items.CAKE || isEdible || isDrink;
            if (isFood && ModConfig.getFoodConfig(item) == null) {
                createFoodConfig(stack);
            }
        }

        AutoConfig.getConfigHolder(ModConfig.class).save();

        long executionTime = (System.nanoTime() - startTime) / 1000000;
        System.out.println("[sol_valheim] Generating default food configs took " + executionTime + "ms.");
    }

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
    public static void onPlayerClone(PlayerEvent.Clone event) {
        var accessor = (PlayerEntityMixinDataAccessor) event.getEntity();
        if (!event.isWasDeath()) {
            var oldPlayer = (ServerPlayer) event.getOriginal();
            accessor.sol_valheim$loadFrom(oldPlayer);
        }
        accessor.sol_valheim$syncFoodData();
    }
}
