package com.mikasa.copybuilding.scan;

import com.mikasa.copybuilding.CopyBuildingClient;
import com.mikasa.copybuilding.config.CopyBuildingConfig;
import com.mikasa.copybuilding.export.CaptureSizeEstimate;
import com.mikasa.copybuilding.export.ExportFormat;
import com.mikasa.copybuilding.export.ExportProgress;
import com.mikasa.copybuilding.export.JsonStructureExporter;
import com.mikasa.copybuilding.export.LitematicStructureExporter;
import com.mikasa.copybuilding.export.NbtStructureExporter;
import com.mikasa.copybuilding.export.RegionCapture;
import com.mikasa.copybuilding.export.SchemStructureExporter;
import com.mikasa.copybuilding.selection.SelectionState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background chunk reader — does not block gameplay / input on the client tick thread.
 * Author: Mikasa
 */
public final class ChunkScanJob {
	private static final DateTimeFormatter EXPORT_TIME =
			DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	/** Yield every this many block samples so the game keeps CPU time. */
	private static final int YIELD_EVERY_BLOCKS = 8192;
	/** Live FILE flush at most this often (ms). */
	private static final long LIVE_FLUSH_MIN_MS = 750L;

	private final ExecutorService worker = Executors.newSingleThreadExecutor(daemonFactory());
	private final AtomicBoolean workerBusy = new AtomicBoolean(false);
	private final AtomicInteger sessionId = new AtomicInteger();
	/** Number of chunks already read (any order). */
	private final AtomicInteger completedChunks = new AtomicInteger();
	private final Object stateLock = new Object();

	private volatile boolean tracking;
	private volatile boolean scanning;
	private volatile boolean cancelRequested;

	private int minX;
	private int maxX;
	private int minZ;
	private int maxZ;
	private int yMin;
	private int yMax;
	private List<long[]> chunkQueue = List.of();
	/** Per-queue-index: already scanned in this session. */
	private boolean[] chunkDone = new boolean[0];
	private int maxChunks;
	private RegionCapture capture;
	private Set<String> filterIds = Set.of();
	private CopyBuildingConfig.FilterMode filterMode = CopyBuildingConfig.FilterMode.EXCLUDE;
	private boolean excludeAir = true;
	private boolean excludeGrass;
	private boolean excludeFlowers;
	private boolean excludeDirt;
	private boolean excludeWater;
	private CopyBuildingConfig.StorageMode storageMode = CopyBuildingConfig.StorageMode.RAM;
	private Path liveJsonPath;
	private String sessionExportName;
	private volatile long lastLiveFlushMs;
	private volatile boolean saving;
	private final AtomicInteger savePercent = new AtomicInteger();
	private volatile long cachedLiveFileBytes = -1L;

	private static ThreadFactory daemonFactory() {
		return r -> {
			Thread t = new Thread(r, "copy-that-building-worker");
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY - 2);
			return t;
		};
	}

	public boolean isTracking() {
		return tracking;
	}

	public boolean isScanning() {
		return scanning;
	}

	public boolean isRunning() {
		return scanning;
	}

	public boolean hasCapture() {
		return capture != null;
	}

	public String sessionExportName() {
		return sessionExportName;
	}

	public int currentChunks() {
		return completedChunks.get();
	}

	public int maxChunks() {
		return Math.max(maxChunks, 0);
	}

	public int percent() {
		int max = maxChunks;
		if (max <= 0) {
			return tracking ? 100 : 0;
		}
		return (int) Math.min(100L, (completedChunks.get() * 100L) / max);
	}

	public boolean isSaving() {
		return saving;
	}

	public int hudPercent() {
		return saving ? savePercent.get() : percent();
	}

	public String progressLabel() {
		if (saving) {
			return "Save " + savePercent.get() + "%";
		}
		return currentChunks() + "/" + Math.max(maxChunks(), 1) + " " + percent() + "%";
	}

	/**
	 * Center of the nearest remaining unloaded chunk the scan still needs.
	 * Null when not waiting on unloaded chunks.
	 */
	public UnloadedChunkHint nearestUnloadedChunkHint(ClientLevel level, Vec3 from) {
		if (!tracking || !scanning || saving || level == null || from == null) {
			return null;
		}
		List<long[]> queue;
		boolean[] done;
		int yLo;
		int yHi;
		int max;
		synchronized (stateLock) {
			queue = chunkQueue;
			done = chunkDone;
			max = maxChunks;
			yLo = yMin;
			yHi = yMax;
		}
		if (queue.isEmpty() || max <= 0 || done.length != max) {
			return null;
		}
		UnloadedChunkHint best = null;
		double bestDist = Double.POSITIVE_INFINITY;
		for (int i = 0; i < max; i++) {
			if (done[i]) {
				continue;
			}
			long[] pos = queue.get(i);
			int cx = (int) pos[0];
			int cz = (int) pos[1];
			if (isChunkFullyLoaded(level, cx, cz)) {
				continue;
			}
			double x = (cx << 4) + 8.0;
			double z = (cz << 4) + 8.0;
			double y = (yLo + yHi) * 0.5;
			double distSq = from.distanceToSqr(x, y, z);
			if (distSq < bestDist) {
				bestDist = distSq;
				best = new UnloadedChunkHint(cx, cz, x, y, z, Math.sqrt(distSq));
			}
		}
		return best;
	}

	public record UnloadedChunkHint(int chunkX, int chunkZ, double x, double y, double z, double distance) {
		public Vec3 asVec3() {
			return new Vec3(x, y, z);
		}
	}

	/** RAM used / live file / estimated Save size for current format. */
	public String sizeLabel() {
		RegionCapture cap;
		CopyBuildingConfig.StorageMode mode;
		Path live;
		synchronized (stateLock) {
			cap = capture;
			mode = storageMode;
			live = liveJsonPath;
		}
		if (cap == null) {
			return "RAM 0 B · save ~0 B";
		}
		long ram = CaptureSizeEstimate.estimateRamBytes(cap);
		ExportFormat format = CopyBuildingClient.config().lastFormat();
		long est = CaptureSizeEstimate.estimateFileBytes(cap, format);
		StringBuilder sb = new StringBuilder();
		sb.append("RAM ").append(CaptureSizeEstimate.formatBytes(ram));
		if (mode == CopyBuildingConfig.StorageMode.FILE && live != null) {
			long fileBytes = cachedLiveFileBytes;
			if (fileBytes < 0L) {
				fileBytes = readLiveFileSize(live);
				cachedLiveFileBytes = fileBytes;
			}
			if (fileBytes >= 0L) {
				sb.append(" · file ").append(CaptureSizeEstimate.formatBytes(fileBytes));
			}
		}
		sb.append(" · save ~").append(CaptureSizeEstimate.formatBytes(est));
		return sb.toString();
	}

	private static long readLiveFileSize(Path live) {
		try {
			if (Files.isRegularFile(live)) {
				return Files.size(live);
			}
		} catch (Exception ignored) {
		}
		return -1L;
	}

	public void cancel() {
		if (scanning) {
			cancelRequested = true;
		}
	}

	/**
	 * Hard stop: abort scan/save, free capture RAM, delete live FILE temp JSON.
	 * Selection points are kept so Update can start a new session.
	 */
	public boolean stopWork() {
		final Path toDelete;
		boolean hadWork;
		synchronized (stateLock) {
			hadWork = tracking || saving || capture != null || liveJsonPath != null;
			sessionId.incrementAndGet();
			scanning = false;
			cancelRequested = false;
			saving = false;
			savePercent.set(0);
			toDelete = liveJsonPath;
			tracking = false;
			capture = null;
			chunkQueue = List.of();
			chunkDone = new boolean[0];
			completedChunks.set(0);
			maxChunks = 0;
			liveJsonPath = null;
			sessionExportName = null;
			cachedLiveFileBytes = -1L;
		}
		if (toDelete != null) {
			worker.execute(() -> {
				try {
					Files.deleteIfExists(toDelete);
					CopyBuildingClient.log("Deleted temp file: " + toDelete.getFileName());
				} catch (Exception e) {
					CopyBuildingClient.LOGGER.error("{} Failed to delete temp file {}", CopyBuildingClient.LOG_PREFIX, toDelete, e);
				}
			});
		}
		if (hadWork) {
			CopyBuildingClient.log("Stopped: memory cleared" + (toDelete != null ? ", temp file removed" : ""));
		}
		return hadWork;
	}

	public boolean beginWhenBothPointsSet(SelectionState selection, CopyBuildingConfig config) {
		if (!selection.hasCorners()) {
			stopTracking();
			return false;
		}
		synchronized (stateLock) {
			int sid = sessionId.incrementAndGet();
			applyFiltersFromConfig(config);
			applyBoundsFromSelection(selection);
			rebuildChunkQueue();
			completedChunks.set(0);
			chunkDone = new boolean[maxChunks];
			capture = new RegionCapture(
					new BlockPos(minX, yMin, minZ),
					maxX - minX + 1,
					yMax - yMin + 1,
					maxZ - minZ + 1
			);
			cancelRequested = false;
			tracking = true;
			scanning = maxChunks > 0;
			storageMode = config.storageMode();
			sessionExportName = newExportName();
			prepareLiveFile();
			lastLiveFlushMs = 0L;
			CopyBuildingClient.log("Reading started: " + maxChunks + " chunks, storage=" + storageMode
					+ ", export=" + sessionExportName + ", session=" + sid);
			scheduleLiveFlush(true);
		}
		return true;
	}

	public void updateTrackedZone(SelectionState selection, CopyBuildingConfig config) {
		if (!selection.hasCorners()) {
			stopTracking();
			return;
		}
		if (!tracking) {
			beginWhenBothPointsSet(selection, config);
			return;
		}

		final RegionCapture oldSnap;
		final BlockPos oldOrigin;
		final int oMinX;
		final int oMaxX;
		final int oMinZ;
		final int oMaxZ;
		final int oYMin;
		final int oYMax;
		final Map<Long, Boolean> oldDoneByChunk = new HashMap<>();

		synchronized (stateLock) {
			// Abort in-flight scan of the previous zone.
			sessionId.incrementAndGet();
			oldSnap = capture != null ? capture.snapshot() : null;
			oldOrigin = capture != null ? capture.origin() : null;
			oMinX = minX;
			oMaxX = maxX;
			oMinZ = minZ;
			oMaxZ = maxZ;
			oYMin = yMin;
			oYMax = yMax;
			for (int i = 0; i < chunkDone.length && i < chunkQueue.size(); i++) {
				if (chunkDone[i]) {
					long[] pos = chunkQueue.get(i);
					oldDoneByChunk.put(chunkKey((int) pos[0], (int) pos[1]), Boolean.TRUE);
				}
			}

			applyFiltersFromConfig(config);
			applyBoundsFromSelection(selection);
			rebuildChunkQueue();
			chunkDone = new boolean[maxChunks];
			capture = new RegionCapture(
					new BlockPos(minX, yMin, minZ),
					maxX - minX + 1,
					yMax - yMin + 1,
					maxZ - minZ + 1
			);

			Set<Long> keptChunks = new HashSet<>();
			for (int i = 0; i < maxChunks; i++) {
				long[] pos = chunkQueue.get(i);
				int cx = (int) pos[0];
				int cz = (int) pos[1];
				long key = chunkKey(cx, cz);
				boolean wasDone = oldDoneByChunk.containsKey(key);
				boolean covered = wasDone && chunkNewRegionCoveredByOld(
						cx, cz, oMinX, oMaxX, oMinZ, oMaxZ, oYMin, oYMax);
				chunkDone[i] = covered;
				if (covered) {
					keptChunks.add(key);
				}
			}

			if (oldSnap != null && oldOrigin != null && !keptChunks.isEmpty()) {
				copyKeptBlocks(oldSnap, oldOrigin, keptChunks);
			}

			int kept = 0;
			for (boolean d : chunkDone) {
				if (d) {
					kept++;
				}
			}
			completedChunks.set(kept);
			cancelRequested = false;
			storageMode = config.storageMode();
			if (sessionExportName == null || sessionExportName.isBlank()) {
				sessionExportName = newExportName();
			}
			prepareLiveFile();
			scanning = completedChunks.get() < maxChunks;
			CopyBuildingClient.log("Zone rebuilt: kept " + kept + "/" + maxChunks
					+ " chunks (" + percent() + "%), blocks=" + capture.blockCount());
			scheduleLiveFlush(true);
		}
	}

	private static long chunkKey(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xffffffffL);
	}

	/** True when the new zone's need inside this chunk was already fully scanned in the old zone. */
	private boolean chunkNewRegionCoveredByOld(
			int cx,
			int cz,
			int oMinX,
			int oMaxX,
			int oMinZ,
			int oMaxZ,
			int oYMin,
			int oYMax
	) {
		int nMinX = Math.max(minX, cx << 4);
		int nMaxX = Math.min(maxX, (cx << 4) + 15);
		int nMinZ = Math.max(minZ, cz << 4);
		int nMaxZ = Math.min(maxZ, (cz << 4) + 15);
		if (nMinX > nMaxX || nMinZ > nMaxZ) {
			return true;
		}
		int oMinXc = Math.max(oMinX, cx << 4);
		int oMaxXc = Math.min(oMaxX, (cx << 4) + 15);
		int oMinZc = Math.max(oMinZ, cz << 4);
		int oMaxZc = Math.min(oMaxZ, (cz << 4) + 15);
		if (oMinXc > oMaxXc || oMinZc > oMaxZc) {
			return false;
		}
		boolean xzOk = nMinX >= oMinXc && nMaxX <= oMaxXc && nMinZ >= oMinZc && nMaxZ <= oMaxZc;
		boolean yOk = yMin >= oYMin && yMax <= oYMax;
		return xzOk && yOk;
	}

	private void copyKeptBlocks(RegionCapture oldSnap, BlockPos oldOrigin, Set<Long> keptChunks) {
		List<RegionCapture.PaletteEntry> oldPalette = oldSnap.palette();
		for (RegionCapture.CapturedBlock block : oldSnap.blocks()) {
			int wx = oldOrigin.getX() + block.x();
			int wy = oldOrigin.getY() + block.y();
			int wz = oldOrigin.getZ() + block.z();
			if (wx < minX || wx > maxX || wy < yMin || wy > yMax || wz < minZ || wz > maxZ) {
				continue;
			}
			long key = chunkKey(wx >> 4, wz >> 4);
			if (!keptChunks.contains(key)) {
				continue;
			}
			int pi = block.paletteIndex();
			if (pi < 0 || pi >= oldPalette.size()) {
				continue;
			}
			capture.addPaletteBlock(wx - minX, wy - yMin, wz - minZ, oldPalette.get(pi));
		}
	}

	public void onPointMissing() {
		stopTracking();
	}

	/**
	 * Queues export on the worker thread (does not freeze the game). Chat reports the filename when done.
	 */
	public boolean saveNow(ExportFormat format) {
		if (saving) {
			return false;
		}
		final RegionCapture snap;
		final String name;
		final int scanPct;
		synchronized (stateLock) {
			if (!tracking || capture == null) {
				return false;
			}
			if (sessionExportName == null || sessionExportName.isBlank()) {
				sessionExportName = newExportName();
			}
			name = sessionExportName;
			scanPct = percent();
			snap = capture.snapshot();
		}
		saving = true;
		savePercent.set(0);
		worker.execute(() -> {
			Minecraft client = Minecraft.getInstance();
			workerBusy.set(true);
			try {
				Files.createDirectories(CopyBuildingClient.exportsDir());
				String fileName = name + "." + format.fileExtension();
				Path out = CopyBuildingClient.exportsDir().resolve(fileName);
				ExportProgress progress = p -> savePercent.set(Math.max(0, Math.min(100, p)));
				switch (format) {
					case JSON -> JsonStructureExporter.write(snap, out, progress);
					case NBT -> NbtStructureExporter.write(snap, out, progress);
					case SCHEM -> SchemStructureExporter.write(snap, out, progress);
					case LITEMATIC -> LitematicStructureExporter.write(snap, out, progress);
				}
				savePercent.set(100);
				CopyBuildingClient.log("Saved export: " + fileName + " -> " + out.toAbsolutePath());
				client.execute(() -> {
					LocalPlayer player = client.player;
					if (player != null) {
						player.sendSystemMessage(Component.literal("Saved: " + fileName + " (" + scanPct + "%)"));
					}
				});
			} catch (Exception e) {
				CopyBuildingClient.LOGGER.error("{} Failed to write export", CopyBuildingClient.LOG_PREFIX, e);
				client.execute(() -> {
					LocalPlayer player = client.player;
					if (player != null) {
						player.sendSystemMessage(Component.literal("Save failed: " + e.getMessage()));
					}
				});
			} finally {
				saving = false;
				workerBusy.set(false);
			}
		});
		return true;
	}

	/**
	 * Lightweight client-tick entry: only schedules work. Never scans or writes files here.
	 * Scans any remaining chunk that is currently loaded (not queue-order blocked).
	 */
	public void tick(Minecraft client) {
		if (!tracking || !scanning || saving) {
			return;
		}
		if (client.player == null || client.level == null) {
			return;
		}
		if (cancelRequested) {
			scanning = false;
			cancelRequested = false;
			scheduleLiveFlush(true);
			client.player.sendSystemMessage(Component.literal("Reading cancelled (progress kept)."));
			return;
		}
		if (completedChunks.get() >= maxChunks) {
			scanning = false;
			scheduleLiveFlush(true);
			return;
		}
		if (workerBusy.get()) {
			return;
		}
		ClientLevel level = client.level;
		int pick = findNextLoadedChunkIndex(level, client.player.position());
		if (pick < 0) {
			return;
		}
		long[] chunkPos = chunkQueue.get(pick);
		int cx = (int) chunkPos[0];
		int cz = (int) chunkPos[1];
		LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
		if (chunk == null) {
			return;
		}
		if (!workerBusy.compareAndSet(false, true)) {
			return;
		}
		final int sid = sessionId.get();
		final int scheduledIndex = pick;
		final LevelChunk chunkRef = chunk;
		final int bMinX = minX;
		final int bMaxX = maxX;
		final int bMinZ = minZ;
		final int bMaxZ = maxZ;
		final int bYMin = yMin;
		final int bYMax = yMax;
		worker.execute(() -> runChunkScan(sid, scheduledIndex, chunkRef, cx, cz, bMinX, bMaxX, bMinZ, bMaxZ, bYMin, bYMax));
	}

	/**
	 * Picks a not-yet-scanned chunk that is fully loaded, preferring the one closest to the player.
	 */
	private int findNextLoadedChunkIndex(ClientLevel level, Vec3 playerPos) {
		List<long[]> queue;
		boolean[] done;
		int max;
		synchronized (stateLock) {
			queue = chunkQueue;
			done = chunkDone;
			max = maxChunks;
		}
		if (queue.isEmpty() || done.length != max) {
			return -1;
		}
		int best = -1;
		double bestDist = Double.POSITIVE_INFINITY;
		for (int i = 0; i < max; i++) {
			if (done[i]) {
				continue;
			}
			long[] pos = queue.get(i);
			int cx = (int) pos[0];
			int cz = (int) pos[1];
			if (!isChunkFullyLoaded(level, cx, cz)) {
				continue;
			}
			double x = (cx << 4) + 8.0;
			double z = (cz << 4) + 8.0;
			double distSq = playerPos.distanceToSqr(x, playerPos.y, z);
			if (distSq < bestDist) {
				bestDist = distSq;
				best = i;
			}
		}
		return best;
	}

	private static boolean isChunkFullyLoaded(ClientLevel level, int cx, int cz) {
		if (!level.hasChunk(cx, cz)) {
			return false;
		}
		return level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) != null;
	}

	private void runChunkScan(
			int sid,
			int scheduledIndex,
			LevelChunk chunk,
			int cx,
			int cz,
			int bMinX,
			int bMaxX,
			int bMinZ,
			int bMaxZ,
			int bYMin,
			int bYMax
	) {
		try {
			if (sid != sessionId.get() || cancelRequested || !scanning) {
				return;
			}
			synchronized (stateLock) {
				if (scheduledIndex < 0 || scheduledIndex >= chunkDone.length || chunkDone[scheduledIndex]) {
					return;
				}
			}
			RegionCapture target;
			synchronized (stateLock) {
				target = capture;
			}
			if (target == null) {
				return;
			}

			int worldMinX = Math.max(bMinX, cx << 4);
			int worldMaxX = Math.min(bMaxX, (cx << 4) + 15);
			int worldMinZ = Math.max(bMinZ, cz << 4);
			int worldMaxZ = Math.min(bMaxZ, (cz << 4) + 15);

			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			int sampled = 0;
			for (int x = worldMinX; x <= worldMaxX; x++) {
				for (int z = worldMinZ; z <= worldMaxZ; z++) {
					for (int y = bYMin; y <= bYMax; y++) {
						if (sid != sessionId.get() || cancelRequested) {
							return;
						}
						cursor.set(x, y, z);
						BlockState state = chunk.getBlockState(cursor);
						if (accept(state)) {
							target.addBlock(x - bMinX, y - bYMin, z - bMinZ, state);
						}
						sampled++;
						if (sampled % YIELD_EVERY_BLOCKS == 0) {
							Thread.yield();
						}
					}
				}
			}

			if (sid != sessionId.get() || cancelRequested) {
				return;
			}
			int doneCount;
			synchronized (stateLock) {
				if (scheduledIndex >= chunkDone.length || chunkDone[scheduledIndex]) {
					return;
				}
				chunkDone[scheduledIndex] = true;
				doneCount = completedChunks.incrementAndGet();
			}
			CopyBuildingClient.log("Chunk " + cx + "," + cz + " read (" + doneCount + "/" + maxChunks + ")");
			if (doneCount >= maxChunks) {
				scanning = false;
				CopyBuildingClient.log("Reading complete: 100%");
				flushLiveInline(true);
			} else {
				flushLiveInline(false);
			}
		} catch (Exception e) {
			CopyBuildingClient.LOGGER.error("{} Background scan failed for chunk {},{}", CopyBuildingClient.LOG_PREFIX, cx, cz, e);
		} finally {
			workerBusy.set(false);
		}
	}

	private void prepareLiveFile() {
		if (storageMode != CopyBuildingConfig.StorageMode.FILE) {
			liveJsonPath = null;
			return;
		}
		try {
			Files.createDirectories(CopyBuildingClient.exportsDir());
			if (sessionExportName == null || sessionExportName.isBlank()) {
				sessionExportName = newExportName();
			}
			liveJsonPath = CopyBuildingClient.exportsDir().resolve(sessionExportName + ".json");
		} catch (Exception e) {
			CopyBuildingClient.LOGGER.error("{} Failed to prepare live JSON path", CopyBuildingClient.LOG_PREFIX, e);
			liveJsonPath = null;
		}
	}

	private static String newExportName() {
		return "ctb-" + LocalDateTime.now().format(EXPORT_TIME);
	}

	/** Call only from the worker thread (after a chunk). */
	private void flushLiveInline(boolean force) {
		if (storageMode != CopyBuildingConfig.StorageMode.FILE) {
			return;
		}
		Path path;
		RegionCapture snap;
		synchronized (stateLock) {
			if (capture == null || liveJsonPath == null) {
				return;
			}
			long now = System.currentTimeMillis();
			if (!force && now - lastLiveFlushMs < LIVE_FLUSH_MIN_MS) {
				return;
			}
			lastLiveFlushMs = now;
			path = liveJsonPath;
			snap = capture.snapshot();
		}
		try {
			JsonStructureExporter.write(snap, path);
			cachedLiveFileBytes = Files.size(path);
		} catch (Exception e) {
			CopyBuildingClient.LOGGER.error("{} Failed to flush live JSON", CopyBuildingClient.LOG_PREFIX, e);
		}
	}

	private void scheduleLiveFlush(boolean force) {
		if (storageMode != CopyBuildingConfig.StorageMode.FILE) {
			return;
		}
		final Path path;
		final RegionCapture snap;
		synchronized (stateLock) {
			if (capture == null || liveJsonPath == null) {
				return;
			}
			long now = System.currentTimeMillis();
			if (!force && now - lastLiveFlushMs < LIVE_FLUSH_MIN_MS) {
				return;
			}
			lastLiveFlushMs = now;
			path = liveJsonPath;
			snap = capture.snapshot();
		}
		worker.execute(() -> {
			try {
				JsonStructureExporter.write(snap, path);
				cachedLiveFileBytes = Files.size(path);
			} catch (Exception e) {
				CopyBuildingClient.LOGGER.error("{} Failed to flush live JSON", CopyBuildingClient.LOG_PREFIX, e);
			}
		});
	}

	private void applyBoundsFromSelection(SelectionState selection) {
		this.minX = selection.minX();
		this.maxX = selection.maxX();
		this.minZ = selection.minZ();
		this.maxZ = selection.maxZ();
		this.yMin = selection.yMin();
		this.yMax = selection.yMax();
	}

	private void applyFiltersFromConfig(CopyBuildingConfig config) {
		this.excludeAir = config.excludeAir();
		this.excludeGrass = config.excludeGrass();
		this.excludeFlowers = config.excludeFlowers();
		this.excludeDirt = config.excludeDirt();
		this.excludeWater = config.excludeWater();
		this.filterMode = config.filterMode();
		Set<String> ids = new HashSet<>();
		for (String id : config.blockFilters()) {
			ids.add(id.toLowerCase(Locale.ROOT));
		}
		this.filterIds = ids;
	}

	private void rebuildChunkQueue() {
		List<long[]> chunks = new ArrayList<>();
		int cMinX = minX >> 4;
		int cMaxX = maxX >> 4;
		int cMinZ = minZ >> 4;
		int cMaxZ = maxZ >> 4;
		for (int cx = cMinX; cx <= cMaxX; cx++) {
			for (int cz = cMinZ; cz <= cMaxZ; cz++) {
				chunks.add(new long[]{cx, cz});
			}
		}
		this.chunkQueue = chunks;
		this.maxChunks = chunks.size();
	}

	private boolean accept(BlockState state) {
		if (excludeAir && state.isAir()) {
			return false;
		}
		if (excludeGrass && BlockCategoryExclusions.isGrass(state)) {
			return false;
		}
		if (excludeFlowers && BlockCategoryExclusions.isFlower(state)) {
			return false;
		}
		if (excludeDirt && BlockCategoryExclusions.isDirt(state)) {
			return false;
		}
		if (excludeWater && BlockCategoryExclusions.isWater(state)) {
			return false;
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		String key = id == null ? "minecraft:air" : id.toString();
		if (filterIds.isEmpty()) {
			return true;
		}
		boolean listed = filterIds.contains(key.toLowerCase(Locale.ROOT));
		if (filterMode == CopyBuildingConfig.FilterMode.INCLUDE) {
			return listed;
		}
		return !listed;
	}

	private void stopTracking() {
		stopWork();
	}
}
