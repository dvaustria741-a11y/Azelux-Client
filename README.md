# Azelux Client

A feature-rich Minecraft Java Edition utility client for **1.21.11**, built on Fabric.  
Lunar-style GUI — press **Right Shift** to open the mod menu.

## Features

### Combat
- KillAura
- AimAssist
- Reach
- Velocity

### Movement
- Speed
- Sprint
- NoFall

### Render
- ESP
- Fullbright
- Zoom

### Misc
- AntiAFK
- FastPlace

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for MC **1.21.11**
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Drop `azeluxclient-1.0.0.jar` into your `.minecraft/mods` folder
4. Launch Minecraft and press **Right Shift** in-game to open the GUI

### Mobile (Zalith / Pojav)
Same steps — just place the jar in your mods folder inside the launcher's game directory.

## Building from Source

```bash
git clone https://github.com/dvaustria741-a11y/Azelux-Client
cd Azelux-Client
./gradlew build
# Output: build/libs/azeluxclient-1.0.0.jar
```

## Notes

- No Bedrock config screen — this is a full Java client, no setup codes needed
- Right Shift opens the GUI (same as Lunar Client)
- Targets MC 1.21.11 Fabric with Yarn mappings
