# Steel & Honor

<div align="center">

**A comprehensive medieval kingdom management mod for Minecraft**

*Claim territories, build realms, wage war, and rule with honor*

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-00AA00?style=flat-square&logo=minecraft)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-Loader-6270A0?style=flat-square&logo=data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjQiIGhlaWdodD0iMjQiIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZD0iTTEyIDJMMTMuMDkgOC4yNkwyMCA5TDEzLjA5IDE1Ljc0TDEyIDIyTDEwLjkxIDE1Ljc0TDQgOUwxMC45MSA4LjI2TDEyIDJaIiBmaWxsPSIjNjI3MEEwIi8+Cjwvc3ZnPgo=)](https://fabricmc.net)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)](LICENSE)

</div>

---

## 📖 Overview

**Steel & Honor** transforms your Minecraft server into a dynamic medieval realm where players can establish kingdoms, claim territories, manage citizens, and engage in strategic warfare. Built with modern Kotlin and Fabric, this mod provides a seamless, immersive experience with beautiful visual feedback and intuitive commands.

### ✨ Key Features

- **🏰 Kingdom Management** - Create, customize, and manage your realm with a comprehensive command system
- **🗺️ Territory Control** - Claim and defend territories with visual borders and Xaero's Minimap integration
- **⚔️ War System** - Declare war, form alliances, and battle for dominance with a 15-minute prep phase and 25-minute war duration
- **👥 Role System** - Assign roles (Leader, Officer, Politician, Military, Citizen) with granular permissions
- **🎨 Visual Polish** - Cinematic HUD overlays, particle effects, and synchronized border rendering
- **💬 Smart Tab Completion** - Context-aware autocomplete for all commands in both chat and GUI
- **🎯 Professional UI** - Beautiful in-game menu system with tab completion support

---

## 🚀 Quick Start

### Requirements

- **Minecraft**: 1.21.1
- **Fabric Loader**: 0.16.9 or later
- **Fabric API**: Latest version
- **Fabric Language Kotlin**: Latest version
- **Open Parties and Claims**: 0.25.8 or later (optional but recommended)
- **Xaero's Minimap** & **Xaero's World Map** (optional, for map integration)

### Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.1
2. Download the latest release of Steel & Honor from [Modrinth](https://modrinth.com/mod/steel-and-honor) or [GitHub Releases](https://github.com/S1lk08/steel-and-honor/releases)
3. Place the mod file in your `mods` folder
4. Install required dependencies (Fabric API, Fabric Language Kotlin)
5. Launch Minecraft and enjoy!

---

## 📋 Commands

### Kingdom Management

| Command | Description | Permission |
|---------|-------------|------------|
| `/kingdom create <name> <color>` | Establish a new realm | None |
| `/kingdom rename <name>` | Rename your kingdom | Leader only |
| `/kingdom color <color>` | Change kingdom color | Officer+ |
| `/kingdom flag` | Copy held banner as kingdom crest | Leader only |
| `/kingdom info` | View your kingdom status | None |
| `/kingdom help` | Display command help | None |

### Member Management

| Command | Description | Permission |
|---------|-------------|------------|
| `/kingdom invite <player>` | Invite a player to your kingdom | Leader/Politician |
| `/kingdom join <kingdom>` | Accept a pending invitation | None |
| `/kingdom leave` | Leave your current kingdom | None |
| `/kingdom role set <player> <role>` | Assign a role to a member | Officer+ |

**Available Roles:**
- `leader` - Full control (only one per kingdom)
- `officer` - Can manage members and colors
- `politician` - Can invite new members
- `military` - Combat-focused role
- `citizen` - Basic member (default)

### Warfare

| Command | Description | Permission |
|---------|-------------|------------|
| `/kingdom war declare <kingdom>` | Declare war on another kingdom | Leader/Officer |
| `/kingdom war request <kingdom>` | Request to join an ongoing war | Leader/Officer |
| `/kingdom war approve <kingdom>` | Approve a war assistance request | Officer+ |
| `/kingdom war deny <kingdom>` | Deny a war assistance request | Officer+ |

**War Mechanics:**
- 15-minute preparation phase (no kills count)
- 25-minute active war phase
- Only military, officers, and leaders count for kill tracking
- Civilians and politicians are protected from war scoring

---

## 🎮 Gameplay Features

### Territory System

- **Claim Requirements**: Kingdoms need at least 9 claimed chunks to be considered established
- **Claim Limits**: Based on member count
  - 1-2 members: 1 territory
  - 3-4 members: 2 territories
  - 5-7 members: 3 territories
  - 8+ members: 4 territories
- **Visual Borders**: Real-time particle borders and Xaero's map integration
- **Capital Cities**: Designate special capital territories with unique visual effects

### War System

- **Preparation Phase**: 15 minutes to prepare before combat begins
- **Active Phase**: 25 minutes of warfare where kills are tracked
- **Kill Tracking**: Only military personnel, officers, and leaders contribute to war scores
- **Alliances**: Request assistance from other kingdoms during wars
- **Victory Conditions**: Kingdom with most kills wins; ties result in draws

### Visual Feedback

- **HUD Overlays**: 
  - War status display with prep and active timers
  - Kingdom entry notifications
  - Territory information
- **Particle Effects**: 
  - Celebratory particles when entering cities
  - Special effects for capital cities
  - War declaration celebrations
- **Map Integration**: Full Xaero's Minimap and World Map support with colored territories

---

## 🛠️ For Server Administrators

### Configuration

The mod stores all data in `world/steel_and_honor/kingdoms.dat`. This file contains:
- All kingdom data (names, colors, members, roles)
- Active wars and their states
- Pending invitations and war requests

### Permissions

All commands respect Minecraft's permission system. You can use permission mods like LuckPerms to further restrict access.

### Performance

- Optimized networking with batched updates
- Efficient border rendering with configurable update intervals
- Minimal server-side overhead

---

## 🎨 Customization

### Kingdom Colors

Choose from any Minecraft dye color when creating or updating your kingdom:
- `white`, `orange`, `magenta`, `light_blue`, `yellow`, `lime`, `pink`, `gray`
- `light_gray`, `cyan`, `purple`, `blue`, `brown`, `green`, `red`, `black`

### Banner Designs

Use `/kingdom flag` while holding a banner to copy its design as your kingdom's crest. The banner design appears on:
- All kingdom members' equipment
- Territory borders
- Map overlays

---

## 🐛 Troubleshooting

### Common Issues

**Q: The mod doesn't load**  
A: Ensure you have Fabric API and Fabric Language Kotlin installed.

**Q: Borders don't show up**  
A: Make sure Open Parties and Claims is installed and configured properly.

**Q: Tab completion doesn't work**  
A: Tab completion works in both chat commands and the in-game GUI menu (press the menu key).

**Q: War timer is wrong**  
A: The timer shows both prep time and war time. Format: "Prep: MM:SS | Time Left MM:SS"

---

## 📝 Development

### Building from Source

```bash
# Clone the repository
git clone https://github.com/S1lk08/steel-and-honor.git
cd steel-and-honor

# Build the mod
./gradlew build

# Run in development
./gradlew runClient  # For client testing
./gradlew runServer  # For server testing
```

### Project Structure

```
src/
├── main/
│   ├── kotlin/          # Shared server/client code
│   └── resources/       # Assets, language files, mixins
└── client/
    ├── kotlin/          # Client-only code
    └── java/            # Client mixins
```

### Dependencies

- **Fabric API**: Core Fabric functionality
- **Fabric Language Kotlin**: Kotlin support
- **Open Parties and Claims**: Territory claiming system
- **Xaero's Minimap/World Map**: Map integration (optional)

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Credits

- **Developer**: S1lk08
- **Design & Polish**: Codex
- **Special Thanks**: 
  - FabricMC team for the amazing modding platform
  - Xaero for the excellent map mods
  - Open Parties and Claims developers

---

## 🔗 Links

- [Modrinth](https://modrinth.com/mod/steel-and-honor)
- [GitHub](https://github.com/S1lk08/steel-and-honor)
- [Issue Tracker](https://github.com/S1lk08/steel-and-honor/issues)
- [Discord](https://discord.gg/steel-and-honor) *(if applicable)*

---

<div align="center">

**Made with ❤️ for the Minecraft community**

*Forge your legacy. Rule with honor.*

</div>
