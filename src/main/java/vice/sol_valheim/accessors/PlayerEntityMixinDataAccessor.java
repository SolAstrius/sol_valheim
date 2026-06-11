package vice.sol_valheim.accessors;

import vice.sol_valheim.ValheimFoodData;

public interface PlayerEntityMixinDataAccessor
{
    ValheimFoodData sol_valheim$getFoodData();

    // Pushes the current food data into the synched entity data so it reaches the client.
    void sol_valheim$syncFoodData();
}
