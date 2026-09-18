package com.mikasa.copybuilding.ui;

import com.mikasa.copybuilding.CopyBuildingActions;
import com.mikasa.copybuilding.CopyBuildingClient;
import com.mikasa.copybuilding.config.CopyBuildingConfig;
import com.mikasa.copybuilding.export.ExportFormat;
import com.mikasa.copybuilding.scan.ChunkScanJob;
import com.mikasa.copybuilding.scan.ChunkScanJob.ActiveChunkProgress;
import com.mikasa.copybuilding.scan.ChunkScanJob.AgentHudSnapshot;
import com.mikasa.copybuilding.selection.SelectionState;
import net.minecraft.client.Minecraft;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.function.Supplier;

/**
 * External control window for agent inject mode.
 * Author: Mikasa
 */
public final class AgentControlFrame extends JFrame {
	private static final int MAP_RADIUS = 8;

	private final JTextField yMinField = new JTextField(8);
	private final JTextField yMaxField = new JTextField(8);
	private final JTextField filtersField = new JTextField(28);
	private final JTextField exportPathField = new JTextField(28);
	private final JCheckBox excludeAir = new JCheckBox("Exclude air");
	private final JCheckBox excludeGrass = new JCheckBox("Exclude grass");
	private final JCheckBox excludeFlowers = new JCheckBox("Exclude flowers");
	private final JCheckBox excludeDirt = new JCheckBox("Exclude dirt");
	private final JCheckBox excludeWater = new JCheckBox("Exclude water");
	private final JComboBox<CopyBuildingConfig.FilterMode> filterMode =
			new JComboBox<>(CopyBuildingConfig.FilterMode.values());
	private final JComboBox<CopyBuildingConfig.StorageMode> storageMode =
			new JComboBox<>(CopyBuildingConfig.StorageMode.values());
	private final JComboBox<ExportFormat> formatBox = new JComboBox<>(ExportFormat.values());

	private final JLabel hotkeyHint = new JLabel("Ctrl+M+I hide / show");
	private final JLabel playerPosLabel = new JLabel("Pos: —");
	private final JLabel pointsLabel = new JLabel("A=unset   B=unset");
	private final JLabel nextLabel = new JLabel("Next: —");
	private final JLabel overallLabel = new JLabel("Overall: —");
	private final JTextArea scanningArea = new JTextArea(3, 36);
	private final JTextArea waitingArea = new JTextArea(5, 36);
	private final JTextArea logArea = new JTextArea(6, 40);

	private final CompassPanel compassPanel = new CompassPanel();
	private final ChunkMapPanel chunkMapPanel = new ChunkMapPanel();

	private volatile boolean refreshQueued;

	public AgentControlFrame() {
		super("Copy That Building — Inject (Mikasa)");
		setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
		setAlwaysOnTop(true);

		CopyBuildingConfig cfg = CopyBuildingClient.config();
		yMinField.setText(Integer.toString(cfg.yMin()));
		yMaxField.setText(Integer.toString(cfg.yMax()));
		filtersField.setText(cfg.blockFiltersCsv());
		String exportPath = cfg.agentExportPath();
		exportPathField.setText(exportPath.isEmpty()
				? CopyBuildingClient.exportsDir().toString()
				: exportPath);
		excludeAir.setSelected(cfg.excludeAir());
		excludeGrass.setSelected(cfg.excludeGrass());
		excludeFlowers.setSelected(cfg.excludeFlowers());
		excludeDirt.setSelected(cfg.excludeDirt());
		excludeWater.setSelected(cfg.excludeWater());
		filterMode.setSelectedItem(cfg.filterMode());
		storageMode.setSelectedItem(cfg.storageMode());
		formatBox.setSelectedItem(cfg.lastFormat());

		scanningArea.setEditable(false);
		scanningArea.setLineWrap(true);
		waitingArea.setEditable(false);
		waitingArea.setLineWrap(true);
		logArea.setEditable(false);
		logArea.setLineWrap(true);

		JPanel root = new JPanel(new BorderLayout(6, 6));
		root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		hotkeyHint.setAlignmentX(LEFT_ALIGNMENT);
		playerPosLabel.setAlignmentX(LEFT_ALIGNMENT);
		pointsLabel.setAlignmentX(LEFT_ALIGNMENT);
		top.add(hotkeyHint);
		top.add(Box.createVerticalStrut(4));
		top.add(playerPosLabel);
		top.add(Box.createVerticalStrut(2));
		top.add(pointsLabel);

		JPanel navRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		navRow.setAlignmentX(LEFT_ALIGNMENT);
		compassPanel.setBorder(BorderFactory.createTitledBorder("Compass"));
		chunkMapPanel.setBorder(BorderFactory.createTitledBorder("Chunk map (XZ)"));
		JPanel legend = new JPanel();
		legend.setLayout(new BoxLayout(legend, BoxLayout.Y_AXIS));
		legend.setBorder(BorderFactory.createTitledBorder("Legend"));
		legend.add(new JLabel("P = player"));
		legend.add(new JLabel("@ = next goal"));
		legend.add(new JLabel("# = loaded"));
		legend.add(new JLabel("· = need load"));
		legend.add(new JLabel("+ = scanned"));
		legend.add(new JLabel("A/B = points"));
		navRow.add(compassPanel);
		navRow.add(chunkMapPanel);
		navRow.add(legend);
		top.add(Box.createVerticalStrut(4));
		top.add(navRow);

		nextLabel.setAlignmentX(LEFT_ALIGNMENT);
		overallLabel.setAlignmentX(LEFT_ALIGNMENT);
		top.add(nextLabel);
		top.add(overallLabel);

		JPanel lists = new JPanel(new BorderLayout(4, 4));
		lists.setAlignmentX(LEFT_ALIGNMENT);
		JPanel scanWrap = new JPanel(new BorderLayout());
		scanWrap.setBorder(BorderFactory.createTitledBorder("Scanning now"));
		scanWrap.add(new JScrollPane(scanningArea), BorderLayout.CENTER);
		JPanel waitWrap = new JPanel(new BorderLayout());
		waitWrap.setBorder(BorderFactory.createTitledBorder("Waiting"));
		waitWrap.add(new JScrollPane(waitingArea), BorderLayout.CENTER);
		lists.add(scanWrap, BorderLayout.NORTH);
		lists.add(waitWrap, BorderLayout.CENTER);
		top.add(Box.createVerticalStrut(4));
		top.add(lists);

		JPanel form = new JPanel(new GridBagLayout());
		form.setAlignmentX(LEFT_ALIGNMENT);
		form.setBorder(BorderFactory.createTitledBorder("Settings"));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 4, 2, 4);
		c.anchor = GridBagConstraints.WEST;
		int row = 0;
		row = addRow(form, c, row, "Y Min", yMinField);
		row = addRow(form, c, row, "Y Max", yMaxField);
		row = addRow(form, c, row, "Export folder", exportPathField);
		row = addRow(form, c, row, "Block filters", filtersField);
		row = addRow(form, c, row, "Filter mode", filterMode);
		row = addRow(form, c, row, "Storage", storageMode);
		row = addRow(form, c, row, "Format", formatBox);
		c.gridx = 0;
		c.gridy = row;
		c.gridwidth = 2;
		JPanel checks = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		checks.add(excludeAir);
		checks.add(excludeGrass);
		checks.add(excludeFlowers);
		checks.add(excludeDirt);
		checks.add(excludeWater);
		form.add(checks, c);
		top.add(Box.createVerticalStrut(4));
		top.add(form);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
		buttons.add(button("Set A @ player", this::onSetA));
		buttons.add(button("Set B @ player", this::onSetB));
		buttons.add(button("Update", this::onUpdate));
		buttons.add(button("Save", this::onSave));
		buttons.add(button("Cancel", this::onCancel));
		buttons.add(button("Stop", this::onStop));
		buttons.add(button("Status", this::onStatus));

		JPanel south = new JPanel(new BorderLayout(4, 4));
		south.add(buttons, BorderLayout.NORTH);
		JScrollPane logScroll = new JScrollPane(logArea);
		logScroll.setBorder(BorderFactory.createTitledBorder("Log"));
		south.add(logScroll, BorderLayout.CENTER);

		root.add(new JScrollPane(top), BorderLayout.CENTER);
		root.add(south, BorderLayout.SOUTH);
		setContentPane(root);

		setMinimumSize(new Dimension(640, 720));
		pack();
		setLocationByPlatform(true);

		Timer uiTimer = new Timer(250, e -> requestRefresh());
		uiTimer.start();
		appendLog("Ctrl+M+I toggles this window. Set A/B uses your block position.");
	}

	private static int addRow(JPanel form, GridBagConstraints c, int row, String label, java.awt.Component field) {
		c.gridwidth = 1;
		c.gridx = 0;
		c.gridy = row;
		form.add(new JLabel(label), c);
		c.gridx = 1;
		form.add(field, c);
		return row + 1;
	}

	private JButton button(String title, Runnable action) {
		JButton b = new JButton(title);
		b.addActionListener(e -> action.run());
		return b;
	}

	private void onSetA() {
		runOnGameThread(CopyBuildingActions::setPointA);
	}

	private void onSetB() {
		runOnGameThread(CopyBuildingActions::setPointB);
	}

	private void onUpdate() {
		runOnGameThread(() -> {
			applyFormToConfig();
			CopyBuildingClient.config().save();
			SelectionState selection = CopyBuildingClient.selection();
			if (selection.hasCorners()) {
				return CopyBuildingActions.afterSelectionChanged();
			}
			return "Config updated. Set both points to start reading.";
		});
	}

	private void onSave() {
		runOnGameThread(() -> {
			applyFormToConfig();
			ExportFormat format = (ExportFormat) formatBox.getSelectedItem();
			if (format == null) {
				format = ExportFormat.JSON;
			}
			CopyBuildingClient.config().setLastFormat(format);
			CopyBuildingClient.config().save();
			return CopyBuildingActions.save(format);
		});
	}

	private void onCancel() {
		runOnGameThread(CopyBuildingActions::cancel);
	}

	private void onStop() {
		runOnGameThread(CopyBuildingActions::stop);
	}

	private void onStatus() {
		runOnGameThread(CopyBuildingActions::statusText);
	}

	private void applyFormToConfig() {
		CopyBuildingConfig cfg = CopyBuildingClient.config();
		cfg.setYMin(parseInt(yMinField.getText(), cfg.yMin()));
		cfg.setYMax(parseInt(yMaxField.getText(), cfg.yMax()));
		if (cfg.yMin() > cfg.yMax()) {
			int t = cfg.yMin();
			cfg.setYMin(cfg.yMax());
			cfg.setYMax(t);
			yMinField.setText(Integer.toString(cfg.yMin()));
			yMaxField.setText(Integer.toString(cfg.yMax()));
		}
		cfg.setExcludeAir(excludeAir.isSelected());
		cfg.setExcludeGrass(excludeGrass.isSelected());
		cfg.setExcludeFlowers(excludeFlowers.isSelected());
		cfg.setExcludeDirt(excludeDirt.isSelected());
		cfg.setExcludeWater(excludeWater.isSelected());
		cfg.setFilterMode((CopyBuildingConfig.FilterMode) filterMode.getSelectedItem());
		cfg.setStorageMode((CopyBuildingConfig.StorageMode) storageMode.getSelectedItem());
		cfg.setBlockFiltersFromCsv(filtersField.getText());
		cfg.setAgentExportPath(exportPathField.getText());
		ExportFormat format = (ExportFormat) formatBox.getSelectedItem();
		if (format != null) {
			cfg.setLastFormat(format);
		}
	}

	private static int parseInt(String text, int fallback) {
		try {
			return Integer.parseInt(text.trim());
		} catch (Exception e) {
			return fallback;
		}
	}

	private void runOnGameThread(Supplier<String> action) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			appendLog("ERROR: Minecraft not ready");
			return;
		}
		mc.execute(() -> {
			String msg;
			try {
				msg = action.get();
			} catch (Throwable t) {
				msg = "ERROR: " + t.getMessage();
			}
			String finalMsg = msg;
			SwingUtilities.invokeLater(() -> {
				if (finalMsg != null) {
					appendLog(finalMsg);
				}
				requestRefresh();
			});
		});
	}

	private void requestRefresh() {
		if (!CopyBuildingClient.isCoreReady() || refreshQueued) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			return;
		}
		refreshQueued = true;
		mc.execute(() -> {
			AgentHudSnapshot snap;
			try {
				snap = CopyBuildingClient.scanJob().buildAgentHudSnapshot(
						mc, MAP_RADIUS, CopyBuildingClient.selection());
			} catch (Throwable t) {
				SwingUtilities.invokeLater(() -> refreshQueued = false);
				return;
			}
			AgentHudSnapshot finalSnap = snap;
			SwingUtilities.invokeLater(() -> {
				applySnapshot(finalSnap);
				refreshQueued = false;
			});
		});
	}

	private void applySnapshot(AgentHudSnapshot snap) {
		playerPosLabel.setText(snap.posLine());
		pointsLabel.setText(snap.pointsLine());
		nextLabel.setText(snap.nextLine());
		overallLabel.setText("Overall: " + snap.progressLabel() + "  |  " + snap.sizeLabel()
				+ "  |  " + snap.overallPercent() + "%");

		List<ActiveChunkProgress> scanning = snap.scanningNow();
		if (scanning == null || scanning.isEmpty()) {
			scanningArea.setText(snap.scanning() ? "(waiting for loaded chunk…)" : "(idle)");
		} else {
			StringBuilder sb = new StringBuilder();
			for (ActiveChunkProgress p : scanning) {
				sb.append(p.chunkX()).append(", ").append(p.chunkZ())
						.append("  ").append(p.percent()).append("%\n");
			}
			scanningArea.setText(sb.toString().trim());
		}

		StringBuilder wait = new StringBuilder();
		for (String line : snap.waitingLines()) {
			wait.append(line).append('\n');
		}
		waitingArea.setText(wait.toString().trim());

		compassPanel.setBearing(snap.relativeBearingDeg());
		chunkMapPanel.setMap(snap.mapRadius(), snap.mapCells());
	}

	private void appendLog(String line) {
		logArea.append(line + "\n");
		logArea.setCaretPosition(logArea.getDocument().getLength());
	}

	/** Geographic N fixed; arrow points toward next unload relative to look (up = ahead). */
	private static final class CompassPanel extends JPanel {
		private Float bearingDeg;

		CompassPanel() {
			setPreferredSize(new Dimension(120, 120));
			setMinimumSize(new Dimension(120, 120));
		}

		void setBearing(Float bearingDeg) {
			this.bearingDeg = bearingDeg;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			int cx = w / 2;
			int cy = h / 2;
			int r = Math.min(w, h) / 2 - 10;

			g2.setColor(new Color(245, 245, 245));
			g2.fillOval(cx - r, cy - r, r * 2, r * 2);
			g2.setColor(Color.DARK_GRAY);
			g2.drawOval(cx - r, cy - r, r * 2, r * 2);

			g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
			g2.drawString("N", cx - 4, cy - r + 14);
			g2.drawString("S", cx - 4, cy + r - 4);
			g2.drawString("W", cx - r + 4, cy + 4);
			g2.drawString("E", cx + r - 12, cy + 4);

			g2.setColor(new Color(180, 180, 180));
			g2.drawLine(cx - 6, cy, cx + 6, cy);
			g2.drawLine(cx, cy - 6, cx, cy + 6);

			if (bearingDeg != null) {
				double rad = Math.toRadians(bearingDeg);
				// up = ahead (0°); positive bearing = clockwise (turn right)
				double tipX = cx + Math.sin(rad) * (r - 18);
				double tipY = cy - Math.cos(rad) * (r - 18);
				double baseX = cx - Math.sin(rad) * 10;
				double baseY = cy + Math.cos(rad) * 10;
				double px = Math.cos(rad) * 8;
				double py = Math.sin(rad) * 8;

				Path2D arrow = new Path2D.Double();
				arrow.moveTo(tipX, tipY);
				arrow.lineTo(baseX + px, baseY + py);
				arrow.lineTo(baseX - px, baseY - py);
				arrow.closePath();
				g2.setColor(new Color(30, 120, 220));
				g2.fill(arrow);
			} else {
				g2.setColor(Color.GRAY);
				g2.drawString("—", cx - 4, cy + 4);
			}
			g2.dispose();
		}
	}

	private static final class ChunkMapPanel extends JPanel {
		private int radius = MAP_RADIUS;
		private byte[] cells = new byte[0];

		ChunkMapPanel() {
			setPreferredSize(new Dimension(220, 220));
			setMinimumSize(new Dimension(180, 180));
		}

		void setMap(int radius, byte[] cells) {
			this.radius = Math.max(1, radius);
			this.cells = cells == null ? new byte[0] : cells;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g.create();
			int side = radius * 2 + 1;
			if (cells.length < side * side) {
				g2.setColor(Color.LIGHT_GRAY);
				g2.fillRect(0, 0, getWidth(), getHeight());
				g2.dispose();
				return;
			}
			int pad = 4;
			int avail = Math.min(getWidth(), getHeight()) - pad * 2;
			int cell = Math.max(4, avail / side);
			int originX = (getWidth() - cell * side) / 2;
			int originY = (getHeight() - cell * side) / 2;

			for (int z = 0; z < side; z++) {
				for (int x = 0; x < side; x++) {
					byte code = cells[z * side + x];
					g2.setColor(colorFor(code));
					g2.fillRect(originX + x * cell, originY + z * cell, cell - 1, cell - 1);
					String mark = markFor(code);
					if (mark != null) {
						g2.setColor(Color.BLACK);
						g2.setFont(getFont().deriveFont(Font.BOLD, Math.max(8f, cell * 0.7f)));
						g2.drawString(mark, originX + x * cell + 1, originY + z * cell + cell - 2);
					}
				}
			}
			g2.dispose();
		}

		private static Color colorFor(byte code) {
			return switch (code) {
				case AgentHudSnapshot.CELL_UNLOADED -> new Color(230, 230, 230);
				case AgentHudSnapshot.CELL_LOADED -> new Color(140, 200, 140);
				case AgentHudSnapshot.CELL_DONE -> new Color(90, 160, 220);
				case AgentHudSnapshot.CELL_PLAYER -> new Color(255, 220, 80);
				case AgentHudSnapshot.CELL_NEXT -> new Color(255, 120, 80);
				case AgentHudSnapshot.CELL_A -> new Color(200, 160, 255);
				case AgentHudSnapshot.CELL_B -> new Color(255, 160, 200);
				default -> new Color(250, 250, 250);
			};
		}

		private static String markFor(byte code) {
			return switch (code) {
				case AgentHudSnapshot.CELL_PLAYER -> "P";
				case AgentHudSnapshot.CELL_NEXT -> "@";
				case AgentHudSnapshot.CELL_A -> "A";
				case AgentHudSnapshot.CELL_B -> "B";
				case AgentHudSnapshot.CELL_DONE -> "+";
				case AgentHudSnapshot.CELL_LOADED -> "#";
				case AgentHudSnapshot.CELL_UNLOADED -> "·";
				default -> null;
			};
		}
	}
}
