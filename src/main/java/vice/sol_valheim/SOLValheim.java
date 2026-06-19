package vice.sol_valheim;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;

@Mod(SOLValheim.MOD_ID)
public class SOLValheim
{
    public static final String MOD_ID = "sol_valheim";

    private static final DeferredRegister<EntityDataSerializer<?>> ENTITY_DATA_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, MOD_ID);
    static {
        ENTITY_DATA_SERIALIZERS.register("food_data", () -> ValheimFoodData.FOOD_DATA_SERIALIZER);
    }

    public static final ResourceLocation SPEED_BUFF_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "speed_buff");

    public static ModConfig Config;

    private static AttributeModifier speedBuff;
    public static AttributeModifier getSpeedBuffModifier() {
        if (speedBuff == null)
            speedBuff = new AttributeModifier(SPEED_BUFF_ID, Config.common.speedBoost, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        return speedBuff;
    }

    public SOLValheim(IEventBus modEventBus)
    {
        ENTITY_DATA_SERIALIZERS.register(modEventBus);

        Config = ModConfig.load();

        if (Config.common.foodConfigs.isEmpty())
        {
            System.out.println("[sol_valheim] Generating default food configs, this might take a second.");
            long startTime = System.nanoTime();

            BuiltInRegistries.ITEM.forEach(ModConfig::getFoodConfig);

            Config.save();

            long executionTime = (System.nanoTime() - startTime) / 1000000;
            System.out.println("[sol_valheim] Generating default food configs took " + executionTime + "ms.");
        }
    }


    public static void addTooltip(ItemStack item, TooltipFlag flag, List<Component> list)
    {
        var food = item.getItem();
        if (food == Items.ROTTEN_FLESH) {
            list.add(Component.literal("☠ Empties Your Stomach!").withStyle(ChatFormatting.GREEN));
            return;
        }

        var config = ModConfig.getFoodConfig(food);
        if (config == null)
            return;

        var hearts = config.getHearts() % 2 == 0 ? config.getHearts() / 2 : String.format("%.1f", (float) config.getHearts() / 2f);
        list.add(Component.literal("❤ " + hearts + " Heart" + (config.getHearts() / 2f > 1 ? "s" : "")).withStyle(ChatFormatting.RED));
        list.add(Component.literal("☀ " + String.format("%.1f", config.getHealthRegen()) + " Regen").withStyle(ChatFormatting.DARK_RED));

        var minutes = (float) config.getTime() / (20 * 60);

        list.add(Component.literal("⌚ " + String.format("%.0f", minutes)  + " Minute" + (minutes > 1 ? "s" : "")).withStyle(ChatFormatting.GOLD));

        for (var effect : config.extraEffects) {
            var eff = effect.getEffect();
            if (eff == null)
                continue;

            list.add(Component.literal("★ " + eff.getDisplayName().getString() + (effect.amplifier > 1 ? " " + effect.amplifier : "")).withStyle(ChatFormatting.GREEN));
        }

        if (item.getUseAnimation() == UseAnim.DRINK) {
            list.add(Component.literal("❄ Refreshing!").withStyle(ChatFormatting.AQUA));
        }
    }
}
