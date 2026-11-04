# TankForge 2.5D visual prototype

This is a separate Godot 4 prototype branch. It does not replace the working Kotlin/Canvas Android app.

## Current visual target
- Isometric battlefield with layered terrain, bevelled cover, trees and water
- Custom-drawn 2.5D tank silhouettes with track details, layered armour, turrets and barrels
- Ambient ground lighting, contact shadows, muzzle flash and animated dust
- Landscape 1280x720 composition

## Run
Open this folder in Godot 4.3 or newer and press F6/F5 (main scene is configured).

## Next development passes
1. Validate the scene in Godot 4 and capture a reference screenshot.
2. Replace procedural tank drawings with proper layered sprite assets and skeletal/part animation.
3. Add destructible environment, projectiles, explosions, lighting and mobile touch controls.
4. Port the proven TankForge gameplay systems and prepare Android export templates/CI.

The prototype intentionally uses the Compatibility renderer as a conservative mobile baseline.
