package vice.sol_valheim;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@Mod(value = SOLValheim.MOD_ID, dist = Dist.CLIENT)
public class SOLValheimClient {
    public static final ResourceLocation FOOD_HUD_LAYER = ResourceLocation.fromNamespaceAndPath(SOLValheim.MOD_ID, "food_hud");

    public SOLValheimClient(IEventBus modEventBus) {
        modEventBus.addListener(this::registerLayers);

        NeoForge.EVENT_BUS.addListener(this::onRenderLayerPre);
        NeoForge.EVENT_BUS.addListener(this::onItemTooltip);

        ModLoadingContext.get().registerExtensionPoint(
            IConfigScreenFactory.class,
            () -> (container, parent) -> AutoConfig.getConfigScreen(ModConfig.class, parent).get()
        );
    }

    private void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, FOOD_HUD_LAYER, FoodHUD::render);
    }

    // Hide the vanilla food/hunger bar; the valheim food slots replace it.
    private void onRenderLayerPre(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.FOOD_LEVEL))
            event.setCanceled(true);
    }

    private void onItemTooltip(ItemTooltipEvent event) {
        SOLValheim.addTooltip(event.getItemStack(), event.getFlags(), event.getToolTip());
    }
}
