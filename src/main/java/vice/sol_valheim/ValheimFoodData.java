package vice.sol_valheim;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ValheimFoodData {
    // 1.21 syncs SynchedEntityData values through a StreamCodec rather than the old
    // FriendlyByteBuf read/write serializer. We still ship the whole object as an NBT blob.
    public static final StreamCodec<RegistryFriendlyByteBuf, ValheimFoodData> STREAM_CODEC = StreamCodec.of(
        (buffer, value) -> buffer.writeNbt(value.save(new CompoundTag())),
        buffer -> ValheimFoodData.read(buffer.readNbt())
    );

    public static final EntityDataSerializer<ValheimFoodData> FOOD_DATA_SERIALIZER =
        EntityDataSerializer.forValueType(STREAM_CODEC);

    public List<EatenFoodItem> ItemEntries = new ArrayList<>();
    public EatenFoodItem DrinkSlot;

    public void eatItem(Item food) {
        if (food == Items.ROTTEN_FLESH)
            return;

        var config = ModConfig.getFoodConfig(food);
        if (config == null)
            return;

        var isDrink = food.getDefaultInstance().getUseAnimation() == UseAnim.DRINK;
        if (isDrink) {
            if (DrinkSlot != null && !DrinkSlot.canEatEarly())
                return;

            if (DrinkSlot == null)
                DrinkSlot = new EatenFoodItem(food, config.getTime());
            else {
                DrinkSlot.ticksLeft = config.getTime();
                DrinkSlot.item = food;
            }

            return;
        }

        var existing = getEatenFood(food);
        if (existing != null) {
            if (!existing.canEatEarly())
                return;

            existing.ticksLeft = config.getTime();
            return;
        }

        if (ItemEntries.size() < SOLValheim.CONFIG.common.maxSlots) {
            ItemEntries.add(new EatenFoodItem(food, config.getTime()));
            ItemEntries.sort(Comparator.comparingInt(a -> a.ticksLeft));
            return;
        }

        for (var item : ItemEntries) {
            if (item.canEatEarly()) {
                item.ticksLeft = config.getTime();
                item.item = food;
                ItemEntries.sort(Comparator.comparingInt(a -> a.ticksLeft));
                return;
            }
        }
    }

    public boolean canEat(Item food) {
        if (food == Items.ROTTEN_FLESH)
            return true;

        if (food.getDefaultInstance().getUseAnimation() == UseAnim.DRINK)
            return DrinkSlot == null || DrinkSlot.canEatEarly();

        var existing = getEatenFood(food);
        if (existing != null)
            return existing.canEatEarly();

        if (ItemEntries.size() < SOLValheim.CONFIG.common.maxSlots)
            return true;

        return ItemEntries.stream().anyMatch(EatenFoodItem::canEatEarly);
    }

    public EatenFoodItem getEatenFood(Item food) {
        return ItemEntries.stream()
            .filter((item) -> item.item == food)
            .findFirst()
            .orElse(null);
    }


    public void clear() {
        ItemEntries.clear();
        DrinkSlot = null;
    }


    public boolean tick() {
        var shouldSync = false;

        for (var item : ItemEntries) {
            item.ticksLeft--;
        }
        if (ItemEntries.removeIf(item -> item.ticksLeft <= 0)) {
            shouldSync = true;
        }

        if (DrinkSlot != null) {
            DrinkSlot.ticksLeft--;
            if (DrinkSlot.ticksLeft <= 0) {
                DrinkSlot = null;
                shouldSync = true;
            }
        }

        return shouldSync;
    }


    public float getTotalFoodNutrition() {
        float nutrition = 0f;
        for (var item : ItemEntries) {
            ModConfig.Common.FoodConfig food = ModConfig.getFoodConfig(item.item);
            if (food == null)
                continue;

            nutrition += food.getHearts();
        }

        if (DrinkSlot != null) {
            ModConfig.Common.FoodConfig food = ModConfig.getFoodConfig(DrinkSlot.item);
            if (food != null) {
                nutrition += food.getHearts();
            }

            nutrition = nutrition * (1.0f + SOLValheim.CONFIG.common.drinkSlotFoodEffectivenessBonus);
        }

        return nutrition;
    }


    public float getRegenSpeed() {
        float regen = 0.25f;
        for (var item : ItemEntries) {
            ModConfig.Common.FoodConfig food = ModConfig.getFoodConfig(item.item);
            if (food == null)
                continue;

            regen += food.getHealthRegen();
        }

        if (DrinkSlot != null) {
            ModConfig.Common.FoodConfig food = ModConfig.getFoodConfig(DrinkSlot.item);
            if (food != null) {
                regen += food.getHealthRegen();
            }

            regen = regen * (1.0f + SOLValheim.CONFIG.common.drinkSlotFoodEffectivenessBonus);
        }

        return regen;
    }


    public CompoundTag save(CompoundTag tag) {
        int count = 0;
        tag.putInt("count", ItemEntries.size());
        for (var item : ItemEntries) {
            tag.putString("id" + count, BuiltInRegistries.ITEM.getKey(item.item).toString());
            tag.putInt("ticks" + count, item.ticksLeft);
            count++;
        }

        if (DrinkSlot != null) {
            tag.putString("drink", BuiltInRegistries.ITEM.getKey(DrinkSlot.item).toString());
            tag.putInt("drinkticks", DrinkSlot.ticksLeft);
        }

        return tag;
    }

    public static ValheimFoodData read(CompoundTag tag) {
        var instance = new ValheimFoodData();

        var size = tag.getInt("count");
        for (int count = 0; count < size; count++) {
            var str = tag.getString("id" + count);
            var ticks = tag.getInt("ticks" + count);
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(str));

            instance.ItemEntries.add(new EatenFoodItem(item, ticks));
        }

        var drink = tag.getString("drink");
        var drinkTicks = tag.getInt("drinkticks");

        if (!drink.isBlank()) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(drink));
            instance.DrinkSlot = new EatenFoodItem(item, drinkTicks);
        }

        return instance;
    }

    public void loadFrom(ValheimFoodData that) {
        this.DrinkSlot = that.DrinkSlot;
        this.ItemEntries = that.ItemEntries.stream()
            .map(ValheimFoodData.EatenFoodItem::new)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public static class EatenFoodItem {
        public Item item;
        public int ticksLeft;

        public boolean canEatEarly() {
            if (ticksLeft < 1200)
                return true;

            var config = ModConfig.getFoodConfig(item);
            if (config == null)
                return false;

            return ((float) this.ticksLeft / config.getTime()) < SOLValheim.CONFIG.common.eatAgainPercentage;
        }

        public EatenFoodItem(Item item, int ticksLeft) {
            this.item = item;
            this.ticksLeft = ticksLeft;
        }

        public EatenFoodItem(EatenFoodItem eaten) {
            this.item = eaten.item;
            this.ticksLeft = eaten.ticksLeft;
        }
    }
}
