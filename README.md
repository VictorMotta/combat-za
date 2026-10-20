# Cobblemon Combat ZA

**Real-time battles against wild Pokémon for [Cobblemon](https://cobblemon.com/)**, inspired by Pokémon Legends: Z-A. A Fabric
addon: no more turns. You command your Pokémon with keys, its moves charge up and cool down, and the wild Pokémon fights back.

> ⚠️ **BETA: wild Pokémon battles only (for now).** Battles against trainers (NPCs) and other players still use Cobblemon's normal
> turn-based battles.

📦 **Download:** [Modrinth](https://modrinth.com/mod/cobblemon-combat-za) · 🐞 **Bugs and ideas:** [Issues](../../issues) (please
attach your `latest.log`)

## Requirements

- Minecraft **1.21.1**, **Fabric** Loader 0.18.4+, Java 21
- **Cobblemon 1.7.3** (1.8 is not supported yet), **Fabric API**, **Fabric Language Kotlin**
- Installed on **both the client and the server**
- Optional: Cobblemon: Mega Showdown, Xaero's Minimap, Open Parties and Claims
- Not compatible with Cobblemon Fight or Flight Reborn

## Building

```
./gradlew build
```

The jar goes to `build/libs/`. (On Windows: `.\gradlew.bat build`.)

## About this repository

This is the public copy of the mod's source code, updated with each release. The code comments are in Portuguese.

The gameplay design is mine: I decided or approved how every feature behaves, and I did the in-game testing. A large part of the
code was written by an AI assistant (Anthropic's Claude) following my specifications, then compiled, tested in game and revised at
my request.

## License

[MIT](LICENSE). Built on top of Cobblemon; the confusion birds effect uses Cobblemon's texture and is adapted from its particle.
