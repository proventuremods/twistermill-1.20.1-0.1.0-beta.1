# Create: TwisterMill

<p align="center">
  <img src="src/main/resources/icon.png" alt="Create: TwisterMill Icon" width="225" />
</p>

<p align="center">
  <strong>Minecraft NeoForge 1.21.1 • Java 21 • Create 6.0.10 • Sable 2.0.3</strong>
</p>

---

## Overview

**Create: TwisterMill** is an unofficial addon for the Create mod for **Minecraft NeoForge 1.21.1**.

Build wind turbines that power Create machinery using Sable's ship-on-ship sublevel physics.

TwisterMill adds wind-powered Create machinery, redstone-controlled servo bearings, Sable/Create contraption support, weather-based wind input, Ponder scenes, custom resources, FramedBlocks compatibility for selected materials, and a small progression path around Signal Quartz.

---

## Requirements

| Requirement                   |         Version |
|-------------------------------|----------------:|
| Minecraft                     | NeoForge 1.21.1 |
| Java                          |              21 |
| NeoForge                      |        21.1.228 |
| Create                        |          6.0.10 |
| Tested Create Gradle artifact |      6.0.10-280 |
| Sable                         |           2.0.3 |
| Supported Sable range         |  [2.0.3, 3.0.0) |
| ProtoManly's Weather          |    0.16.4-alpha |
| Weather2                      |           2.8.6 |
| CoroUtil                      |           1.3.9 |

For source builds, use the versions defined in `gradle.properties`, `build.gradle`, and `neoforge.mods.toml`.

---

## Optional Compatibility

### ProtoManly's Weather Mod

ProtoManly's Weather Mod support is available through `pmweather`.

Supported range:

```text
pmweather [0.16.4,)
```

### [ProtoManly's Weather Mod on Modrinth](https://modrinth.com/mod/protomanlys-weather)

---

### Create: Aeronautics

If you use **Create: Aeronautics**, use version **1.3.0**.

### [Create: Aeronautics 1.3.0 on Modrinth](https://modrinth.com/mod/create-aeronautics/version/w7zlLnea)

---

### FramedBlocks

TwisterMill includes optional FramedBlocks camo compatibility for selected materials.

Supported TwisterMill materials include:

* Twister Sail
* Signal Quartz Ore
* Nostalgic Grass Block

These materials can be used as FramedBlocks camo sources where supported by FramedBlocks.

---

## Blocks

### Wind Roto Block
Horizontal wind bearing that converts wind into Create RPM and Stress Units.

### Wind Roto Vertical Block
Wind direction bearing for vertical wind-vane style contraptions.

### Servo Twister
Redstone-controlled servo bearing for precise angle movement.

### Inverted Servo Twister
Inverted servo variant for alternate direction and assembly behavior.

### Control Table
Binary redstone control hub for Servo and InvServo setups.

### Redstone In Bit Out Block
stone input block used by the Control Table system.

### Digital Signal Transmitter
Transmits binary control information to linked systems.

### Twister Sail
Sail block for wind-related contraptions.

### Twister Sail Frame
Frame block for sail construction and material handling.

### Signal Quartz Ore
Overworld ore used for TwisterMill progression and crafting.

### Signal Steel Block
Material block for TwisterMill crafting chains.

### Metal Traverse
Configurable structural block with side and bracket model behavior.

### Blade Arm Blocks
Experimental and creative blade-arm variants.

---

## Features

### Wind Power

* Weather-based wind input for Create kinetic systems
* Weather2 wind support
* ProtoManly's Weather Mod compatibility
* Fallback weather handling for supported wind providers
* Wind-to-RPM and Stress Unit conversion
* Open-sky and wind sampling logic for wind generators
* Smooth RPM ramping instead of instant speed jumps

### Servo Control

* Servo and InvServo redstone modes 1-15
* Binary speed, angle, and mode control through the Control Table
* Redstone-based control workflows
* Manual and diagnostics-assisted reseat support for selected contraption anchors

### Contraption Support

* Sable/Create contraption integration
* Ship-on-ship sublevel physics support
* Wind direction alignment with marker-based setups
* Wind Roto Vertical Block anchor workflows

### Progression

* Signal Quartz world generation
* Advancement progression around Signal Quartz
* Config-controlled Ancient Debris reward for the Binary Code Transmitter advancement

### Quality of Life

* Engineer's Goggles support where useful
* Tooltip support where useful
* Ponder scenes for the main setup flows
* Optional compatibility hooks for supported external mods

---

## Configuration

TwisterMill generates a common config file.
The config includes settings for wind power, RPM behavior, sails, diagnostics, Servo and InvServo control, world generation, experimental content, tooltips, vertical wind bearing behavior, and drops.

## Notes

- Signal Quartz Ore generates in the Overworld when ore generation is enabled.
- The first TwisterMill advancement points players toward Signal Quartz Ore.
- lade Arm blocks are currently intentionally left without survival recipes and loot tables.
- Sable is required for the current contraption integration.
- Weather2 and ProtoManly's Weather support are handled through the TwisterMill weather backend system.


## License and Usage

Create: TwisterMill is licensed under the **MIT License**.

You are welcome to use this mod in modpacks and content creation projects.

## Thank You

Have fun building. Mod is still in development. 

## Support

- Discord: https://discord.com/invite/DgyQ3eQvBc
