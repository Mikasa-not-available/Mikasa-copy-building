package com.mikasa.copybuilding.ui;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Projects a world position to HUD screen space using the active camera.
 * Author: Mikasa
 */
public final class WorldScreenProjection {
	public record ScreenPoint(float x, float y, float ndcZ, boolean inFront) {
	}

	private WorldScreenProjection() {
	}

	public static ScreenPoint project(Vec3 world, int guiWidth, int guiHeight) {
		Minecraft mc = Minecraft.getInstance();
		Camera camera = mc.gameRenderer.mainCamera();
		if (!camera.isInitialized()) {
			return new ScreenPoint(0f, 0f, 0f, false);
		}
		Vec3 cam = camera.position();
		Matrix4f matrix = new Matrix4f();
		camera.getViewRotationProjectionMatrix(matrix);
		Vector4f pos = new Vector4f(
				(float) (world.x - cam.x),
				(float) (world.y - cam.y),
				(float) (world.z - cam.z),
				1.0f
		);
		matrix.transform(pos);
		if (pos.w == 0.0f) {
			return new ScreenPoint(0f, 0f, 0f, false);
		}
		float ndcX = pos.x / pos.w;
		float ndcY = pos.y / pos.w;
		float ndcZ = pos.z / pos.w;
		boolean inFront = pos.w > 0.05f && ndcZ > -1.0f && ndcZ < 1.0f;
		float screenX = (ndcX * 0.5f + 0.5f) * guiWidth;
		float screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * guiHeight;
		return new ScreenPoint(screenX, screenY, ndcZ, inFront);
	}

	/** True when the camera is roughly facing the world point. */
	public static boolean lookingToward(Vec3 world, float minDot) {
		Minecraft mc = Minecraft.getInstance();
		Camera camera = mc.gameRenderer.mainCamera();
		if (!camera.isInitialized()) {
			return false;
		}
		Vec3 cam = camera.position();
		double dx = world.x - cam.x;
		double dy = world.y - cam.y;
		double dz = world.z - cam.z;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0e-4) {
			return true;
		}
		dx /= len;
		dy /= len;
		dz /= len;
		var forward = camera.forwardVector();
		double dot = forward.x() * dx + forward.y() * dy + forward.z() * dz;
		return dot >= minDot;
	}
}
