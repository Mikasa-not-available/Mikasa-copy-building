package com.mikasa.copybuilding.mixin;

import com.mikasa.copybuilding.CopyBuildingClient;
import com.mikasa.copybuilding.scan.ChunkScanJob;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * End-of-tick hook without Fabric API lifecycle events.
 * Author: Mikasa
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "tick", at = @At("RETURN"))
	private void mikasaCopyBuilding$endTick(CallbackInfo ci) {
		ChunkScanJob job = CopyBuildingClient.scanJob();
		if (job != null) {
			job.tick((Minecraft) (Object) this);
		}
	}
}
