# Leaf-PvP

Leaf-PvP is [Leaf](https://github.com/Winds-Studio/Leaf) (branch `ver/26.3`), a fast Paper fork, with a few changes
for the modern Duels server (Mace, Elytra, Sword, Axe, Crystal, NethPot, SMP and Pot duels). Everything else is Leaf as
it is. The branch is `pvp`; GitHub Actions builds it, boot-tests it and publishes each push as a release.

## Changes

| Change | Where |
| --- | --- |
| Knockback profiles per world, read from `leaf-pvp.yml`. Plugins pick a world's profile (Duels uses `duels-modern`), and a profile a plugin names starts as a copy of vanilla. Players everywhere else, and all mobs, get 26.3's own knockback. | `leaf-server/src/main/java/io/github/lmalololol/leafpvp/Knockback.java`, hook in `LivingEntity#knockback` |
| Players' movement goes out every tick (vanilla: every other tick), so opponents move smoothly on screen. | `EntityTypes.PLAYER` |
| `/leaf reload` also reloads `leaf-pvp.yml`. | `ReloadCommand` |
| The brand is `Leaf-PvP` (F3 and `/version`). | `leaf-server/build.gradle.kts` |

The changes to Leaf's own code are in `pvp/apply.py`. Each one swaps one exact piece of Leaf's code and stops the
build if Leaf changed that piece, so an update from Leaf can never drop one silently.

## leaf-pvp.yml

```yaml
profiles:
  vanilla:
    friction: 2.0          # the target's speed is divided by this before a push is added
    horizontal: 1.0        # sideways part of the 0.4 push every hit gives
    vertical: 1.0          # its upward part
    vertical-limit: 0.4    # highest upward speed a push can leave
    extra-horizontal: 1.0  # sideways part of the sprint / Knockback enchantment push
    extra-vertical: 1.0    # its upward part
    air-vertical: false    # also push up players who are off the ground
worlds: {}                 # world name: profile name (wins over what plugins set)
```

## Building

GitHub Actions only (`.github/workflows/pvp-build.yml`): `./gradlew applyAllPatches`, `bash pvp/apply.sh`,
`./gradlew createPaperclipJar`, a boot test, then a release with the jar and its SHA-256.

## Updating Leaf

Merge Leaf's `ver/26.3` into `pvp`. If the build stops at "Apply the Leaf-PvP changes", Leaf changed code one of our
changes edits: update that entry in `pvp/apply.py`.
