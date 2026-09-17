# Saffron Sky — standalone Godot flight experiment

A separate, noncombat checkpoint course for Godot **4.3 stable**. Open `project.godot` and press F6/F5, or run `godot --path .` from this directory. This is **not integrated into the native Android APK** and does not migrate native save data.

## Controls
- A/D or left/right arrows: turn; W/S or up/down: climb/descend.
- Q/E (Page Down/Up): slower/faster.
- P or Escape: pause/resume. R: restart.
- Six on-screen hold buttons provide the same controls; pause/reset buttons are top-right. Simultaneous multitouch on a phone has not been verified.
- Pass through seven amber rings in order. The HUD gives the next ring's X position, altitude and distance. Passed rings disappear. Finish freezes the timer; Restart replays.
- Terrain contact returns to the beginning. Map bounds return to the start and reset the ring sequence. Buildings are decorative, without collision physics.

## Scope
Generated flat-shaded heightfield hills, road, sparse buildings, simple visible aircraft, third-person camera, depth-tested materials and directional shadows. No external models/textures/audio. It is an arcade experiment, not realistic aerodynamics or a finished game.

## Verification actually executed
Engine: `Godot Engine v4.3.stable.official.77dcf97d8`.

Before implementation: `--headless --path spikes/godot-flight --script tests/test_scene.gd` failed with `playable scene implementation exists` (1 check, 1 failure).

After implementation, using Xvfb display :88 and Mesa llvmpipe OpenGL Compatibility:
```
DISPLAY=:88 HOME=/tmp/godot-flight-home LIBGL_ALWAYS_SOFTWARE=1 \
  /tmp/godot-flight-tools/Godot_v4.3-stable_linux.x86_64 \
  --audio-driver Dummy --path spikes/godot-flight --script tests/test_scene.gd
```
Repeat with `test_flight.gd`, `test_course.gd`, `test_terrain.gd`:
- FLIGHT TESTS: 12 checks, 0 failures
- COURSE TESTS: 8 checks, 0 failures
- TERRAIN TESTS: 8 checks, 0 failures
- SCENE TESTS: 26 checks, 0 failures
- Normal scene startup with `--quit-after 120`: exit 0.

The scene test exercises movement, pause, reset, touch steering, clearing held inputs and completing all seven gates. These 54 assertions do not constitute phone visual, FPS or battery testing.

Environment warnings: missing optional `libXinerama.so.1`; V-Sync unsupported by the software display. Godot 4.3's dummy headless renderer emits `mesh_get_surface_count: Parameter m is null` when freeing primitive meshes; this was reproduced with an isolated BoxMesh, then tests were rerun on the real software OpenGL backend without that error. No screenshots or FPS claims are inferred from these logs.

## Verdict: PARTIAL
The standalone course runs and its scripted behaviors pass. Actual phone controls, rendering quality, performance and Android export remain unverified. The native 0.0.6 APK uses the original Kotlin/Canvas renderer, not this Godot scene.

## License
New code and generated geometry in this directory: MIT (see LICENSE). Godot is separately MIT-licensed; see https://godotengine.org/license/.
