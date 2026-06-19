package vice.sol_valheim.accessors;

import net.minecraft.server.level.ServerPlayer;
import vice.sol_valheim.ValheimFoodData;

public interface PlayerEntityMixinDataAccessor {
    ValheimFoodData sol_valheim$getFoodData();

    void sol_valheim$loadFrom(ServerPlayer oldPlayer);

    void sol_valheim$syncFoodData();
}
