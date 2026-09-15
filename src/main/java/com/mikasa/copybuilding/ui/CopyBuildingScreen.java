package com.mikasa.copybuilding.ui;

import com.mikasa.copybuilding.CopyBuildingClient;
import com.mikasa.copybuilding.config.CopyBuildingConfig;
import com.mikasa.copybuilding.export.ExportFormat;
import com.mikasa.copybuilding.scan.ChunkScanJob;
import com.mikasa.copybuilding.selection.SelectionState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable settings menu - English hardcoded labels.
 * Author: Mikasa
 */
public final class CopyBuildingScreen extends Screen {
	private static final int CONTENT_BOTTOM_PAD = 70;
	private static final int VIEW_TOP = 36;
	private static final int SCROLLBAR_WIDTH = 6;
	private static final int SCROLLBAR_PAD = 4;

	private EditBox yMinBox;
	private EditBox yMaxBox;
	private EditBox filtersBox;
	private Checkbox excludeAirBox;
	private Checkbox excludeGrassBox;
	private Checkbox excludeFlowersBox;
	private Checkbox excludeDirtBox;
	private Checkbox excludeWaterBox;
	private CycleButton<CopyBuildingConfig.FilterMode> filterModeButton;
	private CycleButton<CopyBuildingConfig.StorageMode> storageModeButton;
	private CycleButton<ExportFormat> formatButton;
	private Button updateButton;
	private Button saveButton;
	private Button stopButton;
	private Button closeButton;
	private StringWidget pointALabel;
	private StringWidget pointBLabel;

	private final List<AbstractWidget> scrollWidgets = new ArrayList<>();
	private final List<Integer> scrollBaseY = new ArrayList<>();
	private int scrollOffset;
	private int contentHeight;
	private int viewBottom;
	private boolean draggingScrollbar;

	public CopyBuildingScreen() {
		super(Component.literal("Copy That Building"));
	}

	@Override
	protected void init() {
		CopyBuildingConfig cfg = CopyBuildingClient.config();
		clearWidgets();
		scrollWidgets.clear();
		scrollBaseY.clear();
		scrollOffset = 0;
		draggingScrollbar = false;

		int right = this.width - 8;
		viewBottom = this.height - CONTENT_BOTTOM_PAD;
		int contentRight = scrollbarLeft() - 8;

		closeButton = Button.builder(Component.literal("X"), b -> onClose())
				.bounds(right - 20, 8, 20, 20)
				.build();
		addRenderableWidget(closeButton);

		addScrollable(new StringWidget(20, 12, 220, 12, Component.literal("Copy That Building"), this.font), 12);

		addScrollable(new StringWidget(20, 40, 80, 12, Component.literal("Y Max"), this.font), 40);
		yMaxBox = new EditBox(this.font, 100, 36, 80, 20, Component.literal("Y Max"));
		yMaxBox.setValue(Integer.toString(cfg.yMax()));
		yMaxBox.setMaxLength(8);
		addScrollable(yMaxBox, 36);

		addScrollable(new StringWidget(20, 68, 80, 12, Component.literal("Y Min"), this.font), 68);
		yMinBox = new EditBox(this.font, 100, 64, 80, 20, Component.literal("Y Min"));
		yMinBox.setValue(Integer.toString(cfg.yMin()));
		yMinBox.setMaxLength(8);
		addScrollable(yMinBox, 64);

		updateButton = Button.builder(Component.literal("Update"), b -> onUpdate())
				.bounds(190, 48, 70, 20)
				.build();
		addScrollable(updateButton, 48);

		int pointWidth = Math.max(140, contentRight - 270);
		pointALabel = new StringWidget(270, 40, pointWidth, 12, Component.literal(pointLine(true)), this.font);
		addScrollable(pointALabel, 40);

		pointBLabel = new StringWidget(270, 68, pointWidth, 12, Component.literal(pointLine(false)), this.font);
		addScrollable(pointBLabel, 68);

		int checkY = 96;
		excludeAirBox = Checkbox.builder(Component.literal("Exclude air"), this.font)
				.pos(20, checkY).selected(cfg.excludeAir()).build();
		addScrollable(excludeAirBox, checkY);

		excludeGrassBox = Checkbox.builder(Component.literal("Exclude grass"), this.font)
				.pos(150, checkY).selected(cfg.excludeGrass()).build();
		addScrollable(excludeGrassBox, checkY);

		excludeFlowersBox = Checkbox.builder(Component.literal("Exclude flowers"), this.font)
				.pos(300, checkY).selected(cfg.excludeFlowers()).build();
		addScrollable(excludeFlowersBox, checkY);

		checkY = 120;
		excludeDirtBox = Checkbox.builder(Component.literal("Exclude dirt"), this.font)
				.pos(20, checkY).selected(cfg.excludeDirt()).build();
		addScrollable(excludeDirtBox, checkY);

		excludeWaterBox = Checkbox.builder(Component.literal("Exclude water"), this.font)
				.pos(150, checkY).selected(cfg.excludeWater()).build();
		addScrollable(excludeWaterBox, checkY);

		storageModeButton = CycleButton.<CopyBuildingConfig.StorageMode>builder(
						mode -> Component.literal(mode == CopyBuildingConfig.StorageMode.RAM ? "Storage: RAM" : "Storage: FILE"),
						cfg.storageMode()
				)
				.withValues(CopyBuildingConfig.StorageMode.RAM, CopyBuildingConfig.StorageMode.FILE)
				.create(20, 144, 220, 20, Component.literal("Storage"), (btn, value) -> {
				});
		addScrollable(storageModeButton, 144);

		filterModeButton = CycleButton.<CopyBuildingConfig.FilterMode>builder(
						mode -> Component.literal(mode == CopyBuildingConfig.FilterMode.INCLUDE ? "Include list" : "Exclude list"),
						cfg.filterMode()
				)
				.withValues(CopyBuildingConfig.FilterMode.EXCLUDE, CopyBuildingConfig.FilterMode.INCLUDE)
				.create(20, 172, 220, 20, Component.literal("Filter mode"), (btn, value) -> {
				});
		addScrollable(filterModeButton, 172);

		addScrollable(new StringWidget(20, 200, 300, 12, Component.literal("Block ids (comma-separated)"), this.font), 200);
		int filterWidth = Math.min(400, contentRight - 20);
		filtersBox = new EditBox(this.font, 20, 216, Math.max(120, filterWidth), 20, Component.literal("Filters"));
		filtersBox.setValue(cfg.blockFiltersCsv());
		filtersBox.setMaxLength(512);
		addScrollable(filtersBox, 216);

		contentHeight = 252;
		applyScroll();

		int footerY = this.height - 28;
		stopButton = Button.builder(Component.literal("Stop"), b -> onStop())
				.bounds(right - 340, footerY, 80, 20)
				.build();
		addRenderableWidget(stopButton);

		formatButton = CycleButton.<ExportFormat>builder(
						format -> Component.literal(format.name()),
						cfg.lastFormat()
				)
				.withValues(
						ExportFormat.JSON,
						ExportFormat.NBT,
						ExportFormat.SCHEM,
						ExportFormat.LITEMATIC
				)
				.create(right - 250, footerY, 120, 20, Component.literal("Format"), (btn, value) -> {
				});
		addRenderableWidget(formatButton);

		saveButton = Button.builder(Component.literal("Save"), b -> onSave())
				.bounds(right - 120, footerY, 112, 20)
				.build();
		addRenderableWidget(saveButton);
	}

	private String pointLine(boolean pointA) {
		SelectionState s = CopyBuildingClient.selection();
		if (pointA) {
			return "Point A: " + (s.pointA() == null ? "unset" : s.pointA().toShortString());
		}
		return "Point B: " + (s.pointB() == null ? "unset" : s.pointB().toShortString());
	}

	private void refreshPointLabels() {
		int contentRight = scrollbarLeft() - 8;
		if (pointALabel != null) {
			String line = pointLine(true);
			pointALabel.setMessage(Component.literal(line));
			int w = Math.min(this.font.width(line) + 4, Math.max(80, contentRight - 270));
			pointALabel.setWidth(w);
			pointALabel.setX(Math.max(270, contentRight - w));
		}
		if (pointBLabel != null) {
			String line = pointLine(false);
			pointBLabel.setMessage(Component.literal(line));
			int w = Math.min(this.font.width(line) + 4, Math.max(80, contentRight - 270));
			pointBLabel.setWidth(w);
			pointBLabel.setX(Math.max(270, contentRight - w));
		}
	}

	private void addScrollable(AbstractWidget widget, int baseY) {
		scrollWidgets.add(widget);
		scrollBaseY.add(baseY);
		addRenderableWidget(widget);
	}

	private int viewHeight() {
		return Math.max(1, viewBottom - VIEW_TOP);
	}

	private int maxScroll() {
		return Math.max(0, contentHeight - viewHeight());
	}

	private int scrollbarLeft() {
		return this.width - SCROLLBAR_PAD - SCROLLBAR_WIDTH - 24;
	}

	private boolean needsScrollbar() {
		return contentHeight > viewHeight();
	}

	private int thumbHeight() {
		int track = viewHeight();
		return Math.max(16, (int) ((long) track * track / Math.max(contentHeight, 1)));
	}

	private int thumbY() {
		int max = maxScroll();
		if (max <= 0) {
			return VIEW_TOP;
		}
		int travel = viewHeight() - thumbHeight();
		return VIEW_TOP + (int) ((long) scrollOffset * travel / max);
	}

	private void applyScroll() {
		scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
		for (int i = 0; i < scrollWidgets.size(); i++) {
			AbstractWidget w = scrollWidgets.get(i);
			int y = scrollBaseY.get(i) - scrollOffset;
			w.setY(y);
			boolean visible = y + w.getHeight() > VIEW_TOP && y < viewBottom;
			w.visible = visible;
			w.active = visible;
		}
	}

	private void setScrollFromMouseY(double mouseY) {
		int max = maxScroll();
		if (max <= 0) {
			return;
		}
		int travel = Math.max(1, viewHeight() - thumbHeight());
		double relative = mouseY - VIEW_TOP - (thumbHeight() / 2.0);
		scrollOffset = (int) Math.round(relative * max / travel);
		applyScroll();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseY >= VIEW_TOP && mouseY <= viewBottom) {
			scrollOffset -= (int) (scrollY * 12);
			applyScroll();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() == 0 && needsScrollbar()) {
			double mouseX = event.x();
			double mouseY = event.y();
			int left = scrollbarLeft();
			int right = left + SCROLLBAR_WIDTH;
			if (mouseX >= left && mouseX <= right && mouseY >= VIEW_TOP && mouseY <= viewBottom) {
				draggingScrollbar = true;
				setScrollFromMouseY(mouseY);
				return true;
			}
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (draggingScrollbar && event.button() == 0) {
			setScrollFromMouseY(event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0 && draggingScrollbar) {
			draggingScrollbar = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	private void onUpdate() {
		applyFormToConfig();
		SelectionState selection = CopyBuildingClient.selection();
		if (!selection.hasCorners()) {
			message("Set point A and point B first.");
			return;
		}
		CopyBuildingClient.scanJob().updateTrackedZone(selection, CopyBuildingClient.config());
		message("Zone rebuilt — " + CopyBuildingClient.scanJob().progressLabel()
				+ " (kept overlapping scanned chunks).");
	}

	private void onStop() {
		boolean ok = CopyBuildingClient.scanJob().stopWork();
		if (!ok) {
			message("Nothing to stop.");
			return;
		}
		message("Stopped. Memory cleared, temp file removed.");
	}

	private void onSave() {
		applyFormToConfig();
		SelectionState selection = CopyBuildingClient.selection();
		if (!selection.hasCorners()) {
			message("Set point A and point B first.");
			return;
		}
		ChunkScanJob job = CopyBuildingClient.scanJob();
		if (!job.isTracking() || !job.hasCapture()) {
			message("Nothing to save yet.");
			return;
		}
		ExportFormat format = formatButton.getValue();
		CopyBuildingClient.config().setLastFormat(format);
		CopyBuildingClient.config().save();
		boolean ok = job.saveNow(format);
		if (!ok) {
			message(job.isSaving() ? "Save already in progress." : "Save failed.");
		}
	}

	private void message(String text) {
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.sendSystemMessage(Component.literal(text));
		}
	}

	private void applyFormToConfig() {
		CopyBuildingConfig cfg = CopyBuildingClient.config();
		cfg.setYMin(parseInt(yMinBox.getValue(), cfg.yMin()));
		cfg.setYMax(parseInt(yMaxBox.getValue(), cfg.yMax()));
		if (cfg.yMin() > cfg.yMax()) {
			int t = cfg.yMin();
			cfg.setYMin(cfg.yMax());
			cfg.setYMax(t);
		}
		cfg.setExcludeAir(excludeAirBox.selected());
		cfg.setExcludeGrass(excludeGrassBox.selected());
		cfg.setExcludeFlowers(excludeFlowersBox.selected());
		cfg.setExcludeDirt(excludeDirtBox.selected());
		cfg.setExcludeWater(excludeWaterBox.selected());
		cfg.setStorageMode(storageModeButton.getValue());
		cfg.setFilterMode(filterModeButton.getValue());
		cfg.setBlockFiltersFromCsv(filtersBox.getValue());
		cfg.setLastFormat(formatButton.getValue());
		cfg.save();
	}

	private static int parseInt(String raw, int fallback) {
		try {
			return Integer.parseInt(raw.trim());
		} catch (Exception e) {
			return fallback;
		}
	}

	@Override
	public void onClose() {
		applyFormToConfig();
		super.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractTransparentBackground(graphics);
		refreshPointLabels();
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		SelectionState s = CopyBuildingClient.selection();
		int footerY = this.height - 28;
		ChunkScanJob job = CopyBuildingClient.scanJob();
		String pct = (s.hasCorners() && job.isTracking()) ? (job.percent() + "%") : "0%";
		String pctLine = "Fill: " + pct;
		graphics.text(
				this.font,
				pctLine,
				saveButton.getX() + saveButton.getWidth() - this.font.width(pctLine),
				footerY - 14,
				0xFFFFFFFF
		);

		if (needsScrollbar()) {
			int left = scrollbarLeft();
			int trackBottom = viewBottom;
			graphics.fill(left, VIEW_TOP, left + SCROLLBAR_WIDTH, trackBottom, 0x66000000);
			int ty = thumbY();
			int th = thumbHeight();
			graphics.fill(left, ty, left + SCROLLBAR_WIDTH, ty + th, 0xFFAAAAAA);
		}
	}
}
