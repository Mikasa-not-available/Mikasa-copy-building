package com.mikasa.copybuilding.mixin;

import com.mikasa.copybuilding.ui.ScanProgressHud;
import com.mikasa.copybuilding.ui.UnloadedChunkWaypointHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draw mod HUD overlays after vanilla HUD extract (no Fabric HudElementRegistry).
 * Author: Mikasa
 */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void mikasaCopyBuilding$afterHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		ScanProgressHud.render(graphics, deltaTracker);
		UnloadedChunkWaypointHud.render(graphics, deltaTracker);
	}
}
