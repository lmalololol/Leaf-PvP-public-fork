#!/usr/bin/env python3
"""Leaf-PvP changes on top of Leaf's patched sources. Run after ./gradlew applyAllPatches (pvp/apply.sh does).

Every change swaps one exact piece of Leaf's text for ours and stops the build when that text is not there exactly
once, so a Leaf update can never drop a change without anyone noticing. New classes need no change here: they live in
leaf-server/src/main/java/io/github/lmalololol/leafpvp.
"""
import pathlib
import sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else pathlib.Path(__file__).resolve().parent.parent)
MC = "leaf-server/src/minecraft/java/"
LEAF = "leaf-server/src/main/java/"

CHANGES = [
    (
        "knockback profiles per world",
        MC + "net/minecraft/world/entity/LivingEntity.java",
        """            Vec3 deltaVector = new Vec3(xd, 0.0, zd).normalize().scale(power);
            Vec3 targetMovement = new Vec3( // Paper - knockback events
                deltaMovement.x / 2.0 - deltaVector.x,
                this.onGround() ? Math.min(0.4, deltaMovement.y / 2.0 + power) : deltaMovement.y,
                deltaMovement.z / 2.0 - deltaVector.z
            );
""",
        """            Vec3 targetMovement = io.github.lmalololol.leafpvp.Knockback.target(this, deltaMovement, xd, zd, power, comesFromEffect); // Leaf-PvP - knockback profiles per world
""",
    ),
    (
        "players' movement sent every tick",
        MC + "net/minecraft/world/entity/EntityTypes.java",
        """            .vehicleAttachment(Avatar.DEFAULT_VEHICLE_ATTACHMENT)
            .clientTrackingRange(32)
            .updateInterval(2)
            .dontTrackDeltas()
    );
    public static final EntityType<FishingHook> FISHING_BOBBER = register(""",
        """            .vehicleAttachment(Avatar.DEFAULT_VEHICLE_ATTACHMENT)
            .clientTrackingRange(32)
            .updateInterval(1) // Leaf-PvP - opponents move every tick on screen, not every other tick
            .dontTrackDeltas()
    );
    public static final EntityType<FishingHook> FISHING_BOBBER = register(""",
    ),
    (
        "/leaf reload also reloads leaf-pvp.yml",
        LEAF + "org/dreeam/leaf/command/subcommands/ReloadCommand.java",
        """        LeafConfig.reloadAsync(sender);
""",
        """        LeafConfig.reloadAsync(sender);
        // Leaf-PvP start - knockback profiles
        io.github.lmalololol.leafpvp.Knockback.reload();
        Command.broadcastCommandMessage(sender, Component.text("Reloaded Leaf-PvP knockback profiles (leaf-pvp.yml).", NamedTextColor.GREEN));
        // Leaf-PvP end - knockback profiles
""",
    ),
    (
        "brand name",
        "leaf-server/build.gradle.kts",
        """"Brand-Name" to "Leaf", // Leaf - Rebrand""",
        """"Brand-Name" to "Leaf-PvP", // Leaf-PvP - Rebrand""",
    ),
]

failed = False
for what, rel, old, new in CHANGES:
    path = root / rel
    if not path.is_file():
        print(f"Leaf-PvP: {what}: {rel} is missing", file=sys.stderr)
        failed = True
        continue
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        print(f"Leaf-PvP: {what}: Leaf's original text is in {rel} {count} times, not once; Leaf changed it, so this"
              f" change needs updating", file=sys.stderr)
        failed = True
        continue
    path.write_text(text.replace(old, new), encoding="utf-8", newline="\n")
    print(f"Leaf-PvP: {what} ({rel})")
if failed:
    sys.exit(1)
