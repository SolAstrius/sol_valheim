package vice.sol_valheim.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vice.sol_valheim.SOLValheim;
import vice.sol_valheim.ValheimFoodData;
import vice.sol_valheim.accessors.PlayerEntityMixinDataAccessor;

@Mixin({Player.class})
public abstract class PlayerEntityMixin extends LivingEntity implements PlayerEntityMixinDataAccessor {
    @Unique
    private static final EntityDataAccessor<ValheimFoodData> sol_valheim$DATA_ACCESSOR = SynchedEntityData.defineId(Player.class, ValheimFoodData.FOOD_DATA_SERIALIZER);

    @Override
    @Unique
    public ValheimFoodData sol_valheim$getFoodData() {
        var player = (Player) (LivingEntity) this;
        return player.getEntityData().get(sol_valheim$DATA_ACCESSOR);
    }

    @Unique
    private ValheimFoodData sol_valheim$food_data;

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(at = {@At("HEAD")}, method = {"causeFoodExhaustion(F)V"}, cancellable = true)
    private void onAddExhaustion(float exhaustion, CallbackInfo info) {
        info.cancel();
    }

    @Inject(at = {@At("HEAD")}, method = {"tick"})
    private void onTick(CallbackInfo info) {
        sol_valheim$tick();
    }

    @Unique
    private void sol_valheim$tick() {
        var level = this.level();
        if (level.isClientSide || isDeadOrDying())
            return;

        Player player = (Player) (LivingEntity) this;
        player.getFoodData().setSaturation(0);

        if (!sol_valheim$food_data.ItemEntries.isEmpty()) {
            if (sol_valheim$food_data.tick() || player.tickCount % 20 == 0) {
                sol_valheim$syncFoodData();
            }
        }

        var timeSinceHurt = level.getGameTime() - ((LivingEntityDamageAccessor) this).getLastDamageStamp();
        if (timeSinceHurt > SOLValheim.CONFIG.common.regenDelay && player.tickCount % (5 * SOLValheim.CONFIG.common.regenSpeedModifier) == 0) {
            player.heal(sol_valheim$food_data.getRegenSpeed() / 20f);
        }
    }

    @Inject(at = {@At("HEAD")}, method = {"canEat(Z)Z"}, cancellable = true)
    private void onCanConsume(boolean ignorehunger, CallbackInfoReturnable<Boolean> info) {
        info.setReturnValue(true);
        info.cancel();
    }

    @Inject(at = {@At("HEAD")}, method = {"hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"}, cancellable = true)
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> info) {
        if (source == this.damageSources().starve()) {
            info.setReturnValue(Boolean.FALSE);
            info.cancel();
        }
    }

    @Inject(at = {@At("TAIL")}, method = {"addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V"})
    private void onWriteCustomData(CompoundTag nbt, CallbackInfo info) {
        nbt.put("sol_food_data", sol_valheim$food_data.save(new CompoundTag()));
    }

    @Inject(at = {@At("TAIL")}, method = {"readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V"})
    private void onReadCustomData(CompoundTag nbt, CallbackInfo info) {
        if (sol_valheim$food_data == null)
            sol_valheim$food_data = new ValheimFoodData();

        var foodData = ValheimFoodData.read(nbt.getCompound("sol_food_data"));
        sol_valheim$food_data.loadFrom(foodData);
        sol_valheim$syncFoodData();
    }

    @Inject(at = @At("TAIL"), method = "<init>")
    private void init(Level level, BlockPos pos, float yRot, GameProfile gameProfile, CallbackInfo ci) {
        sol_valheim$syncFoodData();
    }

    @Override
    @Unique
    public void sol_valheim$syncFoodData() {
        Player player = (Player) (LivingEntity) this;
        float maxHP = Math.min(40, (SOLValheim.CONFIG.common.startingHealth * 2) + sol_valheim$food_data.getTotalFoodNutrition());

        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHP);
        if (SOLValheim.CONFIG.common.speedBoost > 0.01f) {
            var attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            var speedBuff = attr.getModifier(SOLValheim.SPEED_BUFF_ID);
            if (maxHP >= 20 && speedBuff == null)
                attr.addTransientModifier(SOLValheim.getSpeedBuffModifier());
            else if (maxHP < 20 && speedBuff != null)
                attr.removeModifier(SOLValheim.SPEED_BUFF_ID);
        }

        this.entityData.set(sol_valheim$DATA_ACCESSOR, sol_valheim$food_data, true);
    }

    @Override
    @Unique
    public void sol_valheim$loadFrom(ServerPlayer oldPlayer) {
        var oldAccessor = (PlayerEntityMixinDataAccessor) oldPlayer;
        sol_valheim$food_data.loadFrom(oldAccessor.sol_valheim$getFoodData());
    }

    @Inject(at = {@At("TAIL")}, method = {"defineSynchedData(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V"})
    private void onInitDataTracker(SynchedEntityData.Builder builder, CallbackInfo info) {
        if (sol_valheim$food_data == null)
            sol_valheim$food_data = new ValheimFoodData();

        builder.define(sol_valheim$DATA_ACCESSOR, sol_valheim$food_data);
    }
}
