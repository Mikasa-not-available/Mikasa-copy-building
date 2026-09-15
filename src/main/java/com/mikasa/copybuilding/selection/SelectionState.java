package com.mikasa.copybuilding.selection;

import com.mikasa.copybuilding.config.CopyBuildingConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;

/**
 * Region corners + settings-backed Y bounds.
 * Author: Mikasa
 */
public final class SelectionState {
	private final CopyBuildingConfig config;
	private BlockPos pointA;
	private BlockPos pointB;

	public SelectionState(CopyBuildingConfig config) {
		this.config = config;
	}

	public BlockPos pointA() {
		return pointA;
	}

	public BlockPos pointB() {
		return pointB;
	}

	public void setPointA(BlockPos pos) {
		this.pointA = pos == null ? null : pos.immutable();
	}

	public void setPointB(BlockPos pos) {
		this.pointB = pos == null ? null : pos.immutable();
	}

	public boolean hasCorners() {
		return pointA != null && pointB != null;
	}

	public int minX() {
		return Math.min(pointA.getX(), pointB.getX());
	}

	public int maxX() {
		return Math.max(pointA.getX(), pointB.getX());
	}

	public int minZ() {
		return Math.min(pointA.getZ(), pointB.getZ());
	}

	public int maxZ() {
		return Math.max(pointA.getZ(), pointB.getZ());
	}

	public int yMin() {
		return config.yMin();
	}

	public int yMax() {
		return config.yMax();
	}

	public BlockPos origin() {
		return new BlockPos(minX(), yMin(), minZ());
	}

	public Vec3i size() {
		int sx = maxX() - minX() + 1;
		int sy = yMax() - yMin() + 1;
		int sz = maxZ() - minZ() + 1;
		return new Vec3i(sx, sy, sz);
	}

	public AABB box() {
		return new AABB(minX(), yMin(), minZ(), maxX() + 1, yMax() + 1, maxZ() + 1);
	}

	public long volume() {
		Vec3i s = size();
		return (long) s.getX() * (long) s.getY() * (long) s.getZ();
	}
}
