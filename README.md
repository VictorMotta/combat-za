# Cobblemon Combat ZA

**Real-time battles against wild Pokémon for [Cobblemon](https://cobblemon.com/)**, inspired by Pokémon Legends: Z-A. A Fabric
addon: no more turns. You command your Pokémon with keys, its moves charge up and cool down, and the wild Pokémon fights back.

> ⚠️ **BETA: wild Pokémon battles only (for now).** Battles against trainers (NPCs) and other players still use Cobblemon's normal
> turn-based battles.

📦 **Download:** [Modrinth](https://modrinth.com/mod/cobblemon-combat-za) · 🐞 **Bugs and ideas:** [Issues](../../issues) (please
attach your `latest.log`)

## Installation

Put the mod in the `mods` folder of **both the game and the server**, together with the **required** mods:

| Required | Download |
|---|---|
| Minecraft **1.21.1** + **Fabric Loader** 0.18.4+ | [Fabric installer](https://fabricmc.net/use/installer/) |
| **Fabric API** | [Modrinth](https://modrinth.com/mod/fabric-api) (a 1.21.1 version) |
| **Fabric Language Kotlin** | [Modrinth](https://modrinth.com/mod/fabric-language-kotlin) |
| **Cobblemon 1.7.3** (Fabric) | [Cobblemon 1.7.3](https://modrinth.com/mod/cobblemon/version/kF7CvxTo) ⚠️ not 1.8 (not supported yet) |
| **Cobblemon Combat ZA** | [Releases](../../releases) or [Modrinth](https://modrinth.com/mod/cobblemon-combat-za) |

Optional: [Cobblemon: Mega Showdown](https://modrinth.com/mod/cobblemon-mega-showdown) (Mega Evolution),
[Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) (party on the map), [Open Parties and Claims](https://modrinth.com/mod/open-parties-and-claims)
(its party is used). ❌ Not compatible with **Cobblemon Fight or Flight Reborn**.

Full guide, in English and Portuguese: the [Wiki](../../wiki) ([Installation](../../wiki/Installation) ·
[Instalação](../../wiki/Instalação)). Made and tested with the **Cobbleverse** modpack (1.7.42).

**Server owners:** the battle balance (wild damage, your damage, how far wild Pokémon see you, how many attack on their own)
is set in `config/cobblemon_combat_za.json`: see [Server configuration](../../wiki/Server-configuration).

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
