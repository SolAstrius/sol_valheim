package vice.sol_valheim;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Config(name = SOLValheim.MOD_ID)
@Config.Gui.Background("minecraft:textures/block/stone.png")
public class ModConfig extends PartitioningSerializer.GlobalData {

    @ConfigEntry.Category("common")
    @ConfigEntry.Gui.TransitiveObject()
    public Common common = new Common();

    @ConfigEntry.Category("client")
    @ConfigEntry.Gui.TransitiveObject()
    public Client client = new Client();

    // synchronized: the food map is lazily populated and read from both the server thread and the
    // client render thread (HUD). The original used a synchronized Hashtable; this preserves that.
    public static synchronized Common.FoodConfig getFoodConfig(Item item) {
        var stack = item.getDefaultInstance();
        var isDrink = stack.getUseAnimation() == UseAnim.DRINK;
        var isEdible = stack.has(DataComponents.FOOD);
        if (item != Items.CAKE && !isEdible && !isDrink)
            return null;

        var key = BuiltInRegistries.ITEM.getKey(item).toString();

        var existing = SOLValheim.CONFIG.common.foodConfigs.get(key);
        if (existing == null) {
            FoodProperties food = item == Items.CAKE
                ? new FoodProperties.Builder().nutrition(10).saturationModifier(0.7f).build()
                : stack.get(DataComponents.FOOD);

            if (isDrink) {
                if (key.contains("potion")) {
                    food = new FoodProperties.Builder().nutrition(4).saturationModifier(0.75f).build();
                } else if (key.contains("milk")) {
                    food = new FoodProperties.Builder().nutrition(6).saturationModifier(1f).build();
                } else {
                    food = new FoodProperties.Builder().nutrition(2).saturationModifier(0.5f).build();
                }
            }

            if (food == null)
                return null;

            existing = new Common.FoodConfig();
            existing.nutrition = food.nutrition();
            existing.healthRegenModifier = 1f;
            // 1.21's FoodProperties.saturation() returns the *absolute* saturation
            // (nutrition * modifier * 2), whereas getTime() expects the original ~0.3-1.0
            // saturation *modifier* (as 1.20.1's getSaturationModifier() returned). Recover the
            // modifier so food durations stay in Valheim's minutes range instead of inflating ~20x.
            existing.saturationModifier = food.saturation() / (2f * Math.max(1, food.nutrition()));

            if (key.startsWith("farmers")) {
                existing.nutrition = (int) ((existing.nutrition * 1.25));
                existing.saturationModifier = existing.saturationModifier * 1.10f;
                existing.healthRegenModifier = 1.25f;
            }

            if (key.equals("minecraft:golden_apple") || key.equals("minecraft:enchanted_golden_apple")) {
                existing.nutrition = 10;
                existing.healthRegenModifier = 1.5f;
            }

            SOLValheim.CONFIG.common.foodConfigs.put(key, existing);
        }

        return existing;
    }

    @Config(name = "common")
    public static final class Common implements ConfigData {

        @ConfigEntry.Gui.Tooltip()
        @Comment("Default time in seconds that food should last per saturation level")
        public int defaultTimer = 180;

        @ConfigEntry.Gui.Tooltip()
        @Comment("Speed at which regeneration should occur")
        public float regenSpeedModifier = 1f;

        @ConfigEntry.Gui.Tooltip()
        @Comment("Time in ticks that regeneration should wait after taking damage")
        public int regenDelay = 20 * 10;

        @ConfigEntry.Gui.Tooltip()
        @Comment("Time in seconds after spawning before sprinting is disabled")
        public int respawnGracePeriod = 60 * 5;

        @ConfigEntry.Gui.Tooltip()
        @Comment("Extra speed given when your hearts are full (0 to disable)")
        public float speedBoost = 0.20f;

        @ConfigEntry.Gui.Tooltip()
        @Comment("Number of hearts to start with")
        public int startingHealth = 3;

        @ConfigEntry.Gui.Tooltip()
        @Comment("Number of food slots (range 2-5, default 3)")
        public int maxSlots = 3;

        @ConfigEntry.Gui.Tooltip()
        @Comment("Percentage remaining before you can eat again (Valheim refreshes at half-digested)")
        public float eatAgainPercentage = 0.5F;

        @ConfigEntry.Gui.Tooltip()
        @Comment("Boost given to other foods when drinking")
        public float drinkSlotFoodEffectivenessBonus = 0.10F;

        @ConfigEntry.Gui.Tooltip()
        @Comment("Simulate food ticking down during night")
        public boolean passTicksDuringNight = true;

        @ConfigEntry.Gui.Tooltip(count = 5)
        @Comment("""
                Food nutrition and effect overrides (Auto Generated if Empty)
                - nutrition: Affects Heart Gain & Health Regen
                - saturationModifier: Affects Food Duration & Player Speed
                - healthRegenModifier: Multiplies health regen speed
                - extraEffects: Extra effects provided by eating the food. Format: { String ID, float duration, int amplifier }
            """)
        public Map<String, FoodConfig> foodConfigs = new LinkedHashMap<>();

        public static final class FoodConfig implements ConfigData {
            public int nutrition;
            public float saturationModifier = 1f;
            public float healthRegenModifier = 1f;
            public List<MobEffectConfig> extraEffects = new ArrayList<>();

            public int getTime() {
                var time = (int) (SOLValheim.CONFIG.common.defaultTimer * 20 * saturationModifier * nutrition);
                return Math.max(time, 6000);
            }

            public int getHearts() {
                return Math.max(nutrition, 2);
            }

            public float getHealthRegen() {
                return Mth.clamp(nutrition * 0.10f * healthRegenModifier, 0.25f, 2f);
            }
        }

        public static final class MobEffectConfig implements ConfigData {
            @ConfigEntry.Gui.Tooltip()
            @Comment("Mob Effect ID")
            public String ID;

            @ConfigEntry.Gui.Tooltip()
            @Comment("Effect duration percentage (1f is the entire food duration)")
            public float duration = 1f;

            @ConfigEntry.Gui.Tooltip()
            @Comment("Effect Level")
            public int amplifier = 1;

            public MobEffect getEffect() {
                if (ID == null || ID.isBlank())
                    return null;
                return BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.parse(ID));
            }
        }
    }

    @Config(name = "client")
    public static final class Client implements ConfigData {
        @ConfigEntry.Gui.Tooltip
        @Comment("Enlarge the currently eaten food icons")
        public boolean useLargeIcons = true;
    }
}
