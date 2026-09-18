# Copy That Building

Client-only Fabric mod by **Mikasa** for copying building regions from **already loaded client chunks** into export files.

Use it on **your own** worlds/servers when you need to rebuild the same structure quickly (for example two fortresses under a time limit). Pasting back into a world is handled by a separate server mod **Place That Building** (permission-gated) — this mod only **reads and exports**.

| | |
|---|---|
| **Name** | Copy That Building |
| **Mod id** | `mikasa-copy-building` |
| **Author** | Mikasa |
| **Version** | `fabric-26.3-2.10` |
| **Jar** | `Mikasa-copy-building-fabric-26.3-2.10.jar` |
| **Minecraft** | `26.3` |
| **Fabric Loader** | **≥ 0.19.3** |
| **Fabric API** | **not required** |
| **Java** | **25+** |
| **Side** | **Client only** |
| **License** | MIT |

---

## What it does

1. You set two corners (**point A** and **point B**) by looking at blocks.
2. The mod builds a box from A/B plus **Y Min / Y Max**.
3. It walks every **loaded** chunk that intersects that box and stores matching blocks.
4. You press **Save** (or use a command) to write an export file.
5. Scanning and saving run on a **background worker thread** so gameplay and input stay responsive.

The mod does **not**:

- load unloaded chunks for you
- place blocks into the world
- use keybinds (commands + menu only)
- require installation on the server

---

## Requirements

- Minecraft **26.3**
- Fabric Loader **≥ 0.19.3**
- Java **25+**
- Fabric API is **not** required

Server install is **not** needed for this mod.

---

## Install

1. Install Fabric Loader **0.19.3+** for Minecraft **26.3** on the client.
2. Put this jar into the client `mods` folder.
3. Launch the game.

Typical jar name:

```text
Mikasa-copy-building-fabric-26.3-2.10.jar
```

Remove older `Mikasa-copy-building-fabric-26.*-*.jar` files so only one version is loaded.

### Agent inject (Fabric already running)

Lab / PoC notes (Attach API, Knot parent class loader, compass UI): **[README-AGENT-POC.md](README-AGENT-POC.md)**.

Use this when Fabric is **already** running and you do **not** want the jar in `mods/` (or want a hot attach). Requires JDK with Attach API.

1. Start Minecraft with **Fabric** (`KnotClient`). Do **not** also load this mod from `mods/` at the same time.
2. From the project root:

```bat
python inject.py
```

Optional flags: `--no-build`, `--pid <pid>`, `--jar path\to.jar`.

If the agent jar and Attacher are **already built**, use the no-Gradle script:

```bat
python inject_ready.py
python inject_ready.py path\to\attacher-classes path\to\mod.jar
```

Without paths, both are searched in the **current working directory**.
3. Agent starts with menu **hidden**. Toggle with **Ctrl+M+I** (hold Ctrl, press M, then I — Mi = Mikasa).
4. In the menu you can set a custom **Export folder** (agent mode only); blank/default uses `config/Mikasa-copy-building/exports`.
5. Agent mode has **no** slash-command intercept (late attach). For slash/HUD, use the normal `mods/` install.
6. Agent UI includes a **compass** toward the next chunk that still needs loading, plus a mini chunk map and scan progress.

---

## Quick start

1. Look at a block → `/copybuilding setpointA`
2. Look at the opposite corner → `/copybuilding setpointB`
3. Reading starts automatically when both points exist.
4. Open the menu → `/copybuilding openmenu`
5. Adjust Y bounds / filters if needed → **Update**
6. Walk so needed chunks stay loaded (or wait while the HUD shows progress)
7. Choose format → **Save**
8. Chat prints: `Saved: ctb-yyyyMMdd-HHmmss.<ext> (N%)`

Files appear under:

```text
.minecraft/config/Mikasa-copy-building/exports/
```

---

## Commands

All names are remappable via `config/Mikasa-copy-building/commands.json`.

| Action | Default command | Description |
|--------|-----------------|-------------|
| Open menu | `/copybuilding openmenu` | Settings UI |
| Point A | `/copybuilding setpointA` | Sets first corner (looked-at block, else raycast / feet) |
| Point B | `/copybuilding setpointB` | Sets second corner; with A set, starts / continues reading |
| Status | `/copybuilding status` | Points, size, progress, size estimate, scanning/saving flags |
| Save | `/copybuilding save <format>` | Queues background export |
| Cancel | `/copybuilding cancel` | Soft pause of reading — **keeps** capture / progress |
| Stop | `/copybuilding stop` | Hard abort — frees RAM, deletes live temp JSON, keeps A/B |

### Save formats

```text
/copybuilding save json
/copybuilding save nbt
/copybuilding save schem
/copybuilding save litematic
```

### Renaming commands

Example `commands.json`:

```json
{
  "root": "ctb",
  "openmenu": "menu",
  "setpointA": "setpointA",
  "setpointB": "setpointB",
  "status": "status",
  "save": "save",
  "cancel": "cancel",
  "stop": "stop"
}
```

Then use `/ctb menu`, `/ctb setpointA`, etc. **Restart the client** after editing `commands.json`.

Allowed name characters: `a-z`, `0-9`, `_`.

---

## Menu (UI)

English labels only (no translation files).

### Layout

- Title and scrollable settings (wheel + right-side scrollbar)
- **Point A / Point B** labels scroll with the content
- Footer (fixed): **Stop** · **Format** · **Save**
- Fill % is drawn above Save
- Top-right **X** closes and saves settings

### Controls

| Control | Meaning |
|---------|---------|
| **Y Max / Y Min** | Vertical bounds of the copy box (clamped so min ≤ max) |
| **Update** | Applies Y/filters/storage to the active session **without resetting progress** |
| **Exclude air / grass / flowers / dirt / water** | Category filters |
| **Storage: RAM / FILE** | Where capture lives while scanning (default **RAM**) |
| **Exclude list / Include list** | How the block-id list is applied |
| **Block ids** | Comma-separated ids, e.g. `minecraft:stone, minecraft:oak_log` |
| **Format** | `JSON` · `NBT` · `SCHEM` · `LITEMATIC` |
| **Save** | Snapshot + background write; chat shows filename when done |
| **Stop** | Abort session, free memory, delete temp FILE json |

Export names are **automatic**: `ctb-yyyyMMdd-HHmmss` from session start time (no manual name field).

---

## Selection and scanning behavior

### Region

- Horizontal bounds: min/max of points A and B on X/Z
- Vertical bounds: **Y Min / Y Max** from config/menu (not the Y of the clicked blocks alone)
- Chunks are 16×16 on X/Z; the job queues every chunk the box intersects

### Loaded chunks only

- A chunk is read only if it is fully loaded on the client
- **Any** remaining loaded chunk can be read (not stuck waiting for queue order) — important with low view distance
- If no remaining needed chunks are loaded, reading **pauses**
- Progress is **kept**; when you walk into a needed chunk, it is read and the bar moves
- Leaving the world briefly (null level) also pauses without wiping progress

### Changing point A / B (or Update)

- Rebuilds the selection box into a **new zone**
- Chunks already scanned that still fully cover what the new zone needs in that chunk are **kept** (blocks stay in RAM)
- Chunks outside the new box (or only partially covered by the old scan) are **dropped**; those slots must be read again
- Progress bar becomes `kept / newTotal`

### Background worker

- Thread name: `copy-that-building-worker` (daemon, lower priority)
- Client tick only schedules work — it does not scan blocks or write large files on the render thread
- **Save** is asynchronous; HUD switches to orange **Save N%** while writing

### Cancel vs Stop

| | **Cancel** | **Stop** |
|---|------------|----------|
| Effect | Pause reading | Abort everything |
| Capture in RAM | Kept | Cleared |
| Temp FILE json | Kept | Deleted |
| Points A/B | Kept | Kept |
| Restart | Continues when chunks load / scanning resumes | Use **Update** or reset a point to start a new session |

---

## Storage modes

Configured in the menu and stored in `config.json`.

### RAM (default)

- Capture stays in memory until **Save** or **Stop**
- Best when you want less disk IO during scanning
- HUD shows `RAM … · save ~…`

### FILE

- While scanning, the live capture is periodically flushed to:

  ```text
  config/Mikasa-copy-building/exports/<session-name>.json
  ```

- HUD also shows `file …` (on-disk size after flushes)
- **Stop** deletes that temp/live json
- Final **Save** still writes the chosen format (json/nbt/schem/litematic)

---

## HUD

Shown at the top center while a session is tracked (both points set and tracking active), or while Save is in progress.

1. Progress bar  
   - Blue = scanning fill %  
   - Orange = save %
2. Line 1: `12/40 30%` or `Save 45%`
3. Line 2: size estimate, e.g. `RAM 4.2 MB · save ~3.1 MB`  
   (FILE mode may add `· file 2.8 MB`)

Estimates are approximate (for planning), not exact JVM heap accounting.

### Unloaded-chunk waypoint

While scanning is waiting on unloaded chunks:

- Finds the **nearest remaining unloaded chunk** in the selection
- Places a HUD marker at that chunk’s **center** (Y = middle of Y Min…Y Max)
- Marker is drawn on the HUD (so it shows **through walls/textures**)
- Appears only when you are **looking toward** that chunk
- Label shows chunk coords and distance in meters

When all remaining queued chunks are loaded, the marker hides.

---

## Export formats

All files go to:

```text
config/Mikasa-copy-building/exports/
```

| Format | Extension | Notes |
|--------|-----------|--------|
| **JSON** | `.json` | Custom `mikasa-copy-building` v1 — intended for **Place That Building** |
| **NBT** | `.nbt` | Vanilla-like structure template (gzip NBT), plus Mikasa origin tags |
| **SCHEM** | `.schem` | Sponge Schematic **v2** (WorldEdit / FAWE style) |
| **LITEMATIC** | `.litematic` | Litematica schematic (single region `main`, air at palette index 0) |

Dense formats (schem / litematic) fill the full box volume; empty cells become air. Sparse JSON/NBT store only captured (filtered) blocks.

### JSON schema (v1)

```json
{
  "format": "mikasa-copy-building",
  "version": 1,
  "origin": { "x": 0, "y": 64, "z": 0 },
  "size": { "x": 16, "y": 32, "z": 16 },
  "palette": [
    { "name": "minecraft:stone" },
    { "name": "minecraft:oak_log", "properties": { "axis": "y" } }
  ],
  "blocks": [
    { "p": 0, "x": 0, "y": 0, "z": 0 }
  ]
}
```

- `origin` — world min corner of the box  
- `size` — full box size in blocks  
- `p` — index into `palette`  
- `x/y/z` — coordinates **relative to origin**

---

## Config files

Folder:

```text
.minecraft/config/Mikasa-copy-building/
```

| File | Purpose |
|------|---------|
| `config.json` | Y bounds, filters, storage mode, last format |
| `commands.json` | Remappable command literals |
| `exports/` | Output (and live FILE json while scanning) |

### `config.json` defaults (conceptual)

| Key | Default | Notes |
|-----|---------|--------|
| `yMin` / `yMax` | `-64` / `320` | Vertical range |
| `excludeAir` | `true` | Skip air |
| `excludeGrass` / `excludeFlowers` / `excludeDirt` / `excludeWater` | `false` | Category filters |
| `filterMode` | `EXCLUDE` | or `INCLUDE` with block id list |
| `blockFilters` | `[]` | Block ids |
| `storageMode` | `RAM` | or `FILE` |
| `lastFormat` | `JSON` | Last chosen export format |

---

## Filters

1. **Category checkboxes** — air, grass, flowers, dirt, water (built-in id sets).
2. **Block id list** + mode:
   - **Exclude list** — listed ids are skipped; everything else (that passed categories) is kept
   - **Include list** — only listed ids are kept

Ids are normalized to lowercase. Example:

```text
minecraft:stone, minecraft:cobblestone, minecraft:oak_planks
```

**Update** reapplies filters to the session without wiping chunk progress (already scanned chunk data is not re-filtered retroactively for blocks already stored — set filters before / early in a scan when possible).

---

## Size notes (rough)

One full modern chunk column is **16 × 16 × 384 ≈ 98 304** block positions.

For formats that store **every** cell (schem / litematic), size scales with full volume × Y range.

For JSON with **Exclude air**, size scales with **non-filtered solid blocks**, often much smaller than raw volume.

HUD `save ~…` estimates the selected format before you press Save.

---

## Build from source

```bat
gradlew.bat build
```

Output:

```text
build/libs/Mikasa-copy-building-fabric-26.3-2.9.jar
```

Versioning convention in this project: `fabric-<minecraft>-<mod>` with **+0.1** per change set (example: `2.8` → `2.9`).

---

## Troubleshooting

| Problem | What to try |
|---------|-------------|
| Commands missing | Fabric Loader 0.19.3+, Fabric API present, client-side jar only |
| Progress stuck | Stand so the next chunk loads; check HUD `N/M %` |
| Empty / tiny export | Filters too aggressive; disable Exclude air / widen include list |
| Game stutter on Save | Should be rare (background thread); huge regions still use CPU/disk |
| Duplicate mod | Remove older `Mikasa-copy-building-*.jar` from `mods` |
| Old SCHEMA format | Removed; configs with `SCHEMA` fall back to JSON |
| Want to discard work | `/copybuilding stop` or menu **Stop** |

---

## Related project

- **Place That Building** — server-side paste companion (separate repo / jar, permission-gated). This client mod does not place structures by itself.

---

## License

MIT — Author: **Mikasa**
