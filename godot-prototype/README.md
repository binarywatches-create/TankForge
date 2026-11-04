# TankForge 2.5D prototype

This is a separate Godot 4 prototype branch. It does not replace the working Kotlin/Canvas Android app.

## Implemented prototype systems
- Isometric battlefield with varied ground tiles, cover, trees, water and crater details
- Layered tank silhouettes with hull, turret, barrel, tracks, contact shadows and team colors
- Animated muzzle flash, recoil, dust, shells, impact bursts, sparks and smoke
- Basic combat loop: enemy pursuit and firing, player damage, enemy damage and score
- Grid-based A* enemy navigation that routes around walls and trees
- Destructible brick and steel cover with different durability, damage marks and impact effects
- Player collision against solid cover with wall sliding
- Keyboard controls for desktop testing
- Multi-touch joystick and fire zones for Android landscape play
- Compatibility renderer and 1280x720 landscape viewport

## Run locally
Open this folder in Godot 4.3 or newer and press F6/F5 (main scene is configured).

## Android build
The Android export preset is configured for a debug APK. The GitHub Actions workflow `.github/workflows/godot-android.yml` exports the APK and uploads it as the `TankForge-2.5D-debug-apk` artifact when the workflow succeeds.

## Known prototype limitations
- Tank art is currently procedural vector drawing, not final sprite-sheet artwork or skeletal animation.
- Combat is a foundation: no obstacle-aware pathfinding, destructible cover, campaign progression, audio or persistence yet.
- CI validation confirms project/script loading; the Android APK still needs installation and play testing on a physical device.

The prototype intentionally uses the Compatibility renderer as a conservative mobile baseline. The existing 0.9.2 Android app remains separate and unchanged.
