# ServerSideAntiCheat

**ServerSideAntiCheat** is a 100% server-side anti-cheat for Paper/Spigot that detects unauthorized client-side mods (like Freecam, Minimaps, and Hacked Clients) **without requiring your players to install any mods themselves.**

## How it Works

Traditional anti-cheats try to detect the *behavior* of a hack (like flying or moving too fast), which often results in false positives due to lag. **ServerSideAntiCheat** is different. It uses a combination of advanced protocol listening and a highly stealthy, invisible UI trick to read exactly what mods the client has installed.

1. **Protocol Interception**: Actively monitors hidden `minecraft:brand` and Plugin Channel payloads that modern mods broadcast.
2. **The "Invisible Anvil" Technique**: Uses a proprietary method to open and instantly close an invisible Anvil UI on the client during login. It injects translation keys into this UI and analyzes the client's packet response to determine exactly which mods the client is capable of translating.
3. **Zero False Positives**: Because it detects the actual mod files rather than player movement, it is incredibly accurate. 
4. **Auto-Updating Signatures**: The plugin automatically connects to the Modrinth API on startup to fetch the latest internal translation keys for your blacklisted mods. You never have to manually update detection signatures!

## Detected Mods (Out of the Box)

By default, the config is setup to catch the most common utility/cheat mods, including:
- Freecam / Wurst Freecam / Easy Freecam / Aether Freecam / Legacy Freecam
- Tweakeroo
- Xaero's Minimap & World Map
- JourneyMap
- Camera Enhancements

## Configuration

The plugin is highly configurable. You can instantly ban any Fabric/Forge mod by simply adding its Modrinth ID to the `config.yml`. The server will automatically query Modrinth, download its language files in the background, and dynamically generate an invisible detection trap for it.

### How to find a Modrinth ID:
You can find the Modrinth ID for any mod by looking at the end of its URL on the Modrinth website:

<img width="326" height="66" alt="Screenshot 2026-07-07 at 12 33 08 PM" src="https://github.com/user-attachments/assets/81986925-b533-4e44-ae5c-dae390314845" />



```yaml
# Add any Modrinth mod ID here to automatically ban it!
banned-mod-ids:
  - "tweakeroo"
  - "freecam"
  - "wi-freecam"
  - "camera-enhancements"
```

## Installation
1. Ensure your server is running **Paper/Spigot 1.20+**
2. Install [ProtocolLib](https://modrinth.com/plugin/protocollib) (Required for packet manipulation)
3. Drop `ServerSideAntiCheat-1.0.0.jar` into your `plugins` folder.
4. Restart your server.

## License
This project is open-source and licensed under the **MIT License**.
