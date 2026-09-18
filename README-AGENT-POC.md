# Agent inject PoC (lab)

Hot-inject **Copy That Building** into a running Fabric `KnotClient` using the Java Attach API.

This is a **lab / proof-of-concept** branch. Full mod docs: [README.md](README.md).

## Idea (one paragraph)

Minecraft classes are defined by the Fabric **Knot** class loader. The agent creates a child `URLClassLoader` with `parent = Knot`, so our code sees the **same** Minecraft types and can call APIs such as `Minecraft.getInstance()`, `LocalPlayer.blockPosition()`, and `LevelChunk.getBlockState(...)`.

## Layout

| Path | Role |
|---|---|
| `inject_ready.py` | Attach orchestrator (no Gradle); finds Attacher + jar |
| `inject.py` | Full rebuild + inject (uses Gradle) |
| Agent jar (`build/libs/…`) | JAR with `Agent-Class` manifest |
| `build/attacher-classes/` | External `Attacher` (JDK Attach API) |

## Run

1. Start Minecraft **26.3** with Fabric. Do **not** put this mod in `mods/` at the same time.
2. Build once if needed: `gradlew build` (also builds Attacher).
3. From the project root (or a folder that contains the jar + `attacher-classes`):

```bat
python inject_ready.py
python inject_ready.py path\to\attacher-classes path\to\mod.jar
```

4. Expect handshake `status=started` under `%TEMP%\mikasa-copy-building-inject.status`.
5. In game: **Ctrl+M+I** toggles the agent menu (starts hidden).

Optional: `python inject.py` rebuilds then injects.

## Agent menu features

- **Set A / Set B** — player block position (not raycast).
- **Y bounds** — default to world min/max (clamped to the loaded dimension); **RAM** storage; **NBT** export. No `config.json` read/write in agent mode.
- **Compass** — points toward the nearest selection chunk that still needs to be loaded (relative to look direction; up = walk forward).
- **Chunk map** — loaded / pending / done chunks around the player, plus A/B and next-goal markers.
- **Progress** — overall scan %, active chunk %, waiting list.
- **Export folder** — optional custom path (in-memory for the session).

Slash commands are **not** available after late attach (no Mixin apply on already-loaded classes). Use the menu / hotkey.

## Requirements

- JDK **25+** with `jdk.attach` (`jps`, `java`)
- Python **3** (for the inject scripts)
- Fabric client already running (`KnotClient`)
