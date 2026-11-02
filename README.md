# EnchantmentsPlus

Fabric mod for **Minecraft Java 1.21.11**.

## Enchantments

- **Шокированный** (mace): when you hit a target while falling, a cosmetic lightning bolt flashes on the target and deals lightning damage. The bolt does not ignite the target or nearby drops.
- **Шахтёр I** (pickaxe): breaks a 3×3 plane. **Шахтёр II** breaks a 5×5 plane. Extra blocks must be suitable for the pickaxe; their drops use the held tool's enchantments, and each extra block consumes durability.
- **Верность IV** (trident): throwing an enchanted trident gives a named amethyst-shard tracker. Right-click the shard to display the trident's latest known dimension and coordinates. Its coordinates update while the trident entity is loaded.
- **Посейдон** (trident): while the thrown trident exists, hold sneak/Shift to pull yourself toward it. It works without rain or water.

## Installation

1. Install Fabric Loader for Minecraft Java **1.21.11**.
2. Put `enchantmentsplus-1.0.0.jar` in the instance's `mods` folder.
3. Launch the game with Fabric.

## Build

Requires Java 21 and Gradle:
```sh
gradle build
```

GitHub Actions builds the mod automatically on pushes to `main`.
