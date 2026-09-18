# Better Chest Storage

A Fabric mod for **Minecraft 26.3** that merges a solid block of chests into one shared inventory,
using nothing but a hand tool. No pipes, no controller block, no memory bank — the chests keep their
vanilla look, so a storage room still looks like a storage room.

## The tool

The mod adds exactly one item, the **Chest Connector**: a wooden wrench found in the *Tools and
Utilities* creative tab.

| Gesture | What it does |
| --- | --- |
| **Sneak + hold right click**, drag, release | Selects the dragged area of chests |
| **Ctrl + right click** inside a selection | Connects it into one shared storage |
| **Ctrl + left click** on a selection | Cancels the selection |
| **Ctrl + left click** on a connected area | Disconnects it |
| Just holding the tool | Shows every connected area in the world around you |

Selections are drawn in warm oak, connected networks in the darker brown of a chest, and the area
being dragged in a brighter highlight.

## Rules

An area can only be selected if **every block inside it is a chest** — not a single gap, not even
air. This is deliberate: an area that quietly skipped the odd block would leave you unable to tell
which chests actually share their storage. A double chest may not be cut in half by the area border
either, since its outside half would still open as a plain vanilla chest and show contents the mod
does not control. Areas may hold at most 256 chests.

## The shared storage

Opening **any** chest of a network shows the same aggregated screen: one cell per item kind, with
the total the whole network holds, a search field and a scrollbar.

The network owns no storage of its own. Every read and write goes straight through to the vanilla
chest block entities inside the area, so two players browsing the same network from two different
chests see, and compete for, the exact same items. Taking ten diamonds from one chest and ten from
another is simply not expressible: there is only ever one pile.

Items are kept packed towards the top of the area — inserts fill the highest chests first and
extractions drain the lowest first. That way, when a network is disconnected, you already know where
its contents ended up, and the mod repacks the chests one last time to make sure.

### Grid controls

| Click | Effect |
| --- | --- |
| Left click an item | Take a stack onto the cursor |
| Right click an item | Take half a stack |
| Middle click an item | Take one |
| Shift + left click an item | Send a stack to your inventory |
| Left click with a full cursor | Store everything you are holding |
| Right click with a full cursor | Store one item |
| Shift + click your own inventory | Send that stack to the network |

Hoppers, comparators and everything else keep working against the individual chests as usual.

## Building

```bash
./gradlew build
```

The jar lands in `build/libs/`. Minecraft 26.x ships with official names, so there is no mapping or
remapping step: the plain `jar` task already produces the shippable mod.

To build and drop it straight into the CurseForge test instance:

```bash
./gradlew copyToTestInstance
```

The target defaults to `~/curseforge/minecraft/Instances/mods_testing/mods` and can be pointed
elsewhere with `-PtestInstanceDir=<path>`.

To launch a development client with the mod loaded:

```bash
./gradlew runClient
```

## Textures

The PNG assets are generated rather than hand-edited, so the art stays reviewable in source control:

```bash
python tools/make_textures.py
```

`tools/make_textures.py` holds the item art as ASCII pixel art and draws the GUI sheet from
rectangles whose coordinates mirror the layout constants in `ChestGridScreen` and `ChestGridMenu`.
It writes an upscaled preview to `build/texture-preview/` for eyeballing changes.

## Layout

```
src/main/java/com/betterchest/
├── BetterChestStorage.java     entry point shared by both sides
├── item/                       the Chest Connector
├── menu/                       the aggregated menu and its click actions
├── net/                        packets, and the server handlers that accept them
├── registry/                   items, menu types, payload types
└── storage/                    the domain: areas, networks, validation, shared inventory

src/client/java/com/betterchest/client/
├── BetterChestStorageClient.java
├── input/                      turns modifier gestures into packets
├── render/                     the world highlights
├── screen/                     the aggregated storage screen
└── state/                      what the client knows about areas
```

The split matters: the client only ever expresses intent. Modifier keys exist on the client, so the
gestures are recognised there, but every decision about what an area is and what leaves a chest is
made on the server, in `storage/`.

## Crafting

```
.  P  P
P  S  P
S  P  .
```

`P` is any planks and `S` is a stick. Planks are taken as the `#minecraft:planks` tag rather than a specific type, so the tool can be built from whatever wood is at hand. The recipe shows up in the recipe book as soon as the player picks up any planks.

## Requirements

* Minecraft 26.3
* Fabric Loader 0.19.5 or newer
* Fabric API
* Java 25
