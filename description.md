# Copy That Building

**Client-only** Fabric utility by **Mikasa** for copying building regions from **already loaded client chunks** into export files.

Designed for **your own** worlds and servers — for example when you need to rebuild the same fortress quickly under a time limit. This mod **only reads and exports**. Pasting back into a world is handled by a separate server mod (**Place That Building**).

---

## Features

- Copy any box defined by two corners (**point A** / **point B**) plus **Y Min / Y Max**
- Reads **only fully loaded** client chunks — no chunk forcing
- **Background worker thread** — scanning and saving should not freeze movement or input
- Progress HUD: chunks done, fill %, RAM / estimated save size
- **Waypoint** to the nearest still-needed unloaded chunk (through-wall marker while looking at it)
- Storage modes: **RAM** (default) or **FILE** (live JSON flush while scanning)
- Soft **Cancel** (keep progress) vs hard **Stop** (free RAM, delete temp file)
- Changing A/B **rebuilds the zone**: overlapping scanned chunks are kept, the rest is discarded, progress updates
- Export formats: **JSON**, vanilla **NBT**, WorldEdit **.schem**, **.litematic**
- Remappable commands via `commands.json`
- English UI labels (menu)

---

## Requirements

| | |
|---|---|
| Minecraft | **26.3** |
| Loader | Fabric **≥ 0.19.3** |
| API | Fabric API |
| Java | **25+** |
| Side | **Client only** |

Server install is **not** required for this mod.

---

## Quick start

1. Look at a block → `/copybuilding setpointA`
2. Opposite corner → `/copybuilding setpointB` (reading starts when both are set)
3. Open menu → `/copybuilding openmenu`
4. Adjust Y / filters if needed → **Update**
5. Walk so needed chunks load (follow the waypoint if shown)
6. Choose format → **Save**
7. Chat shows: `Saved: ctb-yyyyMMdd-HHmmss.<ext> (N%)`

Exports go to:

```text
.minecraft/config/Mikasa-copy-building/exports/
```

---

## Commands

| Action | Default |
|--------|---------|
| Open menu | `/copybuilding openmenu` |
| Point A | `/copybuilding setpointA` |
| Point B | `/copybuilding setpointB` |
| Status | `/copybuilding status` |
| Save | `/copybuilding save json` / `nbt` / `schem` / `litematic` |
| Cancel | `/copybuilding cancel` — pause reading, **keep** data |
| Stop | `/copybuilding stop` — abort, free memory, delete temp JSON |

Command names are remappable in:

```text
config/Mikasa-copy-building/commands.json
```

Restart the client after editing that file.

---

## How scanning works

- The selection is a box from A/B on X/Z and **Y Min…Y Max** vertically
- Any **remaining** loaded chunk can be read (not stuck on queue order) — works with low view distance
- If no needed chunks are loaded, reading **pauses**; progress is kept
- When you enter a needed chunk, it is read and the bar moves
- Filename is automatic: `ctb-yyyyMMdd-HHmmss` (session start time)

### Changing points A / B

- Builds a **new zone**
- Already scanned chunks that still fully cover what the new zone needs stay in memory
- Chunks outside the new box are cleared
- Progress becomes `kept / newTotal`

---

## Storage

| Mode | Behavior |
|------|----------|
| **RAM** (default) | Keep capture in memory until Save / Stop |
| **FILE** | Periodically flush live JSON under `exports/` while scanning |

**Stop** clears RAM and deletes the live FILE json. Points A/B are kept.

---

## Export formats

| Format | Extension | Notes |
|--------|-----------|--------|
| JSON | `.json` | Custom `mikasa-copy-building` v1 (for Place That Building) |
| NBT | `.nbt` | Vanilla-like structure template |
| SCHEM | `.schem` | Sponge Schematic v2 (WorldEdit / FAWE) |
| LITEMATIC | `.litematic` | Litematica (single region) |

---

## Filters (menu)

- Exclude air / grass / flowers / dirt / water
- Block id list with **Exclude** or **Include** mode  
  Example: `minecraft:stone, minecraft:oak_log`

---

## Notes

- Does **not** place blocks
- Does **not** use keybinds (commands + menu only)
- Does **not** load unloaded chunks for you — walk (or raise view distance) so they load
- Use on worlds/servers you are allowed to copy from

---

## License

MIT — Author: **Mikasa**
