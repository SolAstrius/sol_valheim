# SOL: Valheim

A Valheim-style food system for Minecraft. Instead of a single hunger bar, you eat a **variety** of
foods — each occupies a timed slot on the HUD, and the more different foods you keep active, the
higher your **max health** and **health regen**. Food has no saturation/hunger; it has duration.

This branch is a **NeoForge 1.21.1** port of [txni's original SOL: Valheim](https://github.com/txnimc/sol_valheim)
(Architectury, Fabric/Forge 1.20.1).

## How it works

- Each eaten food fills a slot for a set time (based on its nutrition/saturation). You can hold a
  configurable number of food slots plus one drink slot.
- Max health = `startingHealth` + the combined nutrition of your active foods (capped at 20 hearts).
- Health regenerates over time at a rate driven by your active foods, after a post-damage delay.
- At full hearts you gain a small movement-speed buff.
- You can't re-eat a food until its slot has nearly expired. Rotten flesh empties your stomach.
- The vanilla hunger bar is hidden and replaced by the food-slot HUD.

## Differences from the original

- **Single-platform NeoForge** (ModDevGradle) instead of Architectury multiloader.
- **Config is a plain JSON file** (`config/sol_valheim.json`, auto-generated on first launch) rather
  than the Cloth Config / AutoConfig screen. Same fields, hand-editable.
- Eaten food is recorded via NeoForge's `LivingEntityUseItemEvent.Finish` instead of the
  `Player#eat` / `FoodData#eat` mixins (more robust across the 1.21 eating refactor).

## Integrations

- **Farmers Delight** (optional): its foods get a nutrition/saturation/regen buff, and the cake slot
  shows the `farmersdelight:cake_slice` icon when the mod is present.

## Building

Requires JDK 21.

```
./gradlew build
```

The built jar lands in `build/libs/`. CI (`.github/workflows/build.yml`) builds on every push.

## License

GPL-3.0-or-later, inherited from the original project.
