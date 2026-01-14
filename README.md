# BetterConsole

BetterConsole improves Hytale's console readability by adding colors, cleaning up timestamps, and suppressing log spam.

## Configuration

Settings are located in `config/betterconsole/settings.properties`:

- `useColors` (true): Toggles ANSI colors and bright white text.
- `useSuppressions` (true): Enables hiding logs defined in `suppressions.txt`.
- `compactTimestamps` (true): Use `HH:mm:ss` instead of the full date.
- `prettifyPartyInfo` (true): Formats messy `PartyInfo` objects into readable lines.
- `inlineValidationLogs` (true): Compresses asset validation results to single lines.

**Suppressions:**
Add words or regex to `config/betterconsole/suppressions.txt` to hide matching logs.

## Installation

Place the plugin in your server's `earlyplugins` folder. Requires [Hyxin](https://www.curseforge.com/hytale/mods/hyxin).

## GitHub

[BetterConsole on GitHub](https://github.com/Furqit/BetterConsole)
