package vice.sol_valheim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Ported from the original Cloth/AutoConfig + Jankson setup to a plain Gson JSON config.
// Keeps the original behaviour: foodConfigs is auto-generated from the item registry on first
// launch (when empty) and persisted, so it can be hand-tuned afterwards.
public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public Common common = new Common();
    public Client client = new Client();

    private static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(SOLValheim.MOD_ID + ".json");
    }

    public static ModConfig load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                ModConfig cfg = GSON.fromJson(reader, ModConfig.class);
                if (cfg != null) {
                    if (cfg.common == null) cfg.common = new Common();
                    if (cfg.client == null) cfg.client = new Client();
                    if (cfg.common.foodConfigs == null) cfg.common.foodConfigs = new LinkedHashMap<>();
                    return cfg;
                }
            } catch (Exception e) {
                System.out.println("[sol_valheim] Failed to read config, using defaults: " + e);
            }
        }
        return new ModConfig();
    }

    public void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            System.out.println("[sol_valheim] Failed to save config: " + e);
        }
    }

    // synchronized: the food map is lazily populated and read from both the server thread and the
    // client render thread (HUD). The original used a synchronized Hashtable; this preserves that.
    public static synchronized Common.FoodConfig getFoodConfig(Item item) {
        var stack = item.getDefaultInstance();
        var isDrink = stack.getUseAnimation() == UseAnim.DRINK;
        var isEdible = stack.has(DataComponents.FOOD);
        if (item != Items.CAKE && !isEdible && !isDrink)
            return null;

        var key = BuiltInRegistries.ITEM.getKey(item).toString();

        var existing = SOLValheim.Config.common.foodConfigs.get(key);
        if (existing == null)
        {
            FoodProperties food = item == Items.CAKE
                    ? new FoodProperties.Builder().nutrition(10).saturationModifier(0.7f).build()
                    : stack.get(DataComponents.FOOD);

            if (isDrink) {
                if (key.contains("potion")) {
                    food = new FoodProperties.Builder().nutrition(4).saturationModifier(0.75f).build();
                }
                else if (key.contains("milk")) {
                    food = new FoodProperties.Builder().nutrition(6).saturationModifier(1f).build();
                }
                else {
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

            if (key.startsWith("farmers"))
            {
                existing.nutrition = (int) ((existing.nutrition * 1.25));
                existing.saturationModifier = existing.saturationModifier * 1.10f;
                existing.healthRegenModifier = 1.25f;
            }

            if (key.equals("minecraft:golden_apple") || key.equals("minecraft:enchanted_golden_apple")) {
                existing.nutrition = 10;
                existing.healthRegenModifier = 1.5f;
            }

            SOLValheim.Config.common.foodConfigs.put(key, existing);
        }

        return existing;
    }

    public static final class Common {

        // Default time in seconds that food should last per saturation level
        public int defaultTimer = 180;

        // Speed at which regeneration should occur
        public float regenSpeedModifier = 1f;

        // Time in ticks that regeneration should wait after taking damage
        public int regenDelay = 20 * 10;

        // Time in seconds after spawning before sprinting is disabled
        public int respawnGracePeriod = 60 * 5;

        // Extra speed given when your hearts are full (0 to disable)
        public float speedBoost = 0.20f;

        // Number of hearts to start with
        public int startingHealth = 3;

        // Number of food slots (range 2-5, default 3)
        public int maxSlots = 3;

        // Percentage remaining before you can eat again (Valheim refreshes at half-digested)
        public float eatAgainPercentage = 0.5F;

        // Boost given to other foods when drinking
        public float drinkSlotFoodEffectivenessBonus = 0.10F;

        // Simulate food ticking down during night
        public boolean passTicksDuringNight = true;

        // Food nutrition and effect overrides (auto-generated if empty)
        public Map<String, FoodConfig> foodConfigs = new LinkedHashMap<>();

        public static final class FoodConfig {
            public int nutrition;
            public float saturationModifier = 1f;
            public float healthRegenModifier = 1f;
            public List<MobEffectConfig> extraEffects = new ArrayList<>();

            public int getTime() {
                var time = (int) (SOLValheim.Config.common.defaultTimer * 20 * saturationModifier * nutrition);
                return Math.max(time, 6000);
            }

            public int getHearts() {
                return Math.max(nutrition, 2);
            }

            public float getHealthRegen()
            {
                return Mth.clamp(nutrition * 0.10f * healthRegenModifier, 0.25f, 2f);
            }
        }

        public static final class MobEffectConfig {
            // Mob Effect ID
            public String ID;

            // Effect duration percentage (1f is the entire food duration)
            public float duration = 1f;

            // Effect Level
            public int amplifier = 1;

            public MobEffect getEffect() {
                if (ID == null || ID.isBlank())
                    return null;
                return BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.parse(ID));
            }
        }
    }

    public static final class Client {
        // Enlarge the currently eaten food icons
        public boolean useLargeIcons = true;
    }
}
