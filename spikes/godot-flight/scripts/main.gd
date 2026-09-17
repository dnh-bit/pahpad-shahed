extends Node3D
## Standalone recreational gate course. No combat or native-app integration.
const Flight = preload("res://scripts/flight_state.gd")
const Course = preload("res://scripts/course.gd")
const Terrain = preload("res://scripts/terrain.gd")
var flight = Flight.new()
var course = Course.new()
var touch: Dictionary = {}
var gates: Array[Node3D] = []
var rig: Node3D
var terrain: Node3D
var hud: Label
var pause_panel: PanelContainer
var pause_label: Label

func named(parent: Node, child: Node, title: String) -> Node:
	child.name = title
	parent.add_child(child)
	return child

func material(color: Color) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = color
	m.roughness = 0.85
	return m

func box(parent: Node3D, title: String, pos: Vector3, size: Vector3, color: Color) -> MeshInstance3D:
	var node := MeshInstance3D.new()
	var shape := BoxMesh.new()
	shape.size = size
	node.mesh = shape
	node.material_override = material(color)
	named(parent, node, title)
	node.position = pos
	return node

func _ready() -> void:
	var world := named(self, Node3D.new(), "World") as Node3D
	var env := WorldEnvironment.new()
	env.environment = Environment.new()
	env.environment.background_mode = Environment.BG_COLOR
	env.environment.background_color = Color("9bb9c9")
	env.environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.environment.ambient_light_color = Color("e8dcc5")
	env.environment.ambient_light_energy = 0.65
	named(world, env, "WorldEnvironment")
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-50, -30, 0)
	sun.shadow_enabled = true
	sun.directional_shadow_max_distance = 220
	named(world, sun, "Sun")
	box(world, "Ground", Vector3(0, -4, -500), Vector3(1800, 2, 2200), Color("c9a16c"))
	terrain = named(world, Terrain.new(), "Hills") as Node3D
	terrain.build()
	var road := named(world, Node3D.new(), "Road") as Node3D
	for i in range(61):
		var z := 180.0 - i * 20.0
		box(road, "Segment%d" % i, Vector3(78, terrain.height_at(78, z) + 0.2, z), Vector3(12, 0.3, 20.1), Color("625c54"))
		box(road, "Mark%d" % i, Vector3(78, terrain.height_at(78, z) + 0.4, z), Vector3(0.4, 0.1, 6), Color("ead8aa"))
	var buildings := named(world, Node3D.new(), "Buildings") as Node3D
	for i in range(18):
		var x := 120.0 + float(i % 3) * 26.0
		var z := 60.0 - float(i / 3) * 160.0
		var height := 8.0 + float(i % 4) * 3.0
		box(buildings, "House%d" % i, Vector3(x, terrain.height_at(x, z) + height / 2, z), Vector3(16, height, 20), Color("e1c8a0"))
		box(buildings, "Roof%d" % i, Vector3(x, terrain.height_at(x, z) + height, z), Vector3(17, 0.8, 21), Color("aa7454"))
	for i in range(course.points.size()):
		var ring := MeshInstance3D.new()
		var shape := TorusMesh.new()
		shape.inner_radius = 13.2
		shape.outer_radius = 14.2
		shape.rings = 32
		shape.ring_segments = 8
		ring.mesh = shape
		ring.rotation_degrees.x = 90
		ring.material_override = material(Color("ffd16a"))
		named(world, ring, "Gate%d" % i)
		ring.position = course.points[i]
		gates.append(ring)
	box(world, "FinishBanner", Vector3(0, 6, -830), Vector3(36, 3, 1), Color("326e68"))
	rig = named(self, Node3D.new(), "DroneRig") as Node3D
	var drone := named(rig, Node3D.new(), "Drone") as Node3D
	box(drone, "Body", Vector3.ZERO, Vector3(0.8, 0.6, 3.4), Color("e8eee9"))
	box(drone, "Wing", Vector3(0, 0, 0.2), Vector3(6.5, 0.15, 0.9), Color("256f79"))
	box(drone, "Tail", Vector3(0, 0.3, 1.5), Vector3(2.4, 0.15, 0.6), Color("efb45a"))
	named(drone, Marker3D.new(), "CameraAnchor")
	var arm := named(rig, SpringArm3D.new(), "SpringArm3D") as SpringArm3D
	arm.position = Vector3(0, 4, 0)
	arm.rotation_degrees.x = -12
	arm.spring_length = 15
	var camera := named(arm, Camera3D.new(), "Camera3D") as Camera3D
	camera.current = true
	camera.far = 2200
	camera.fov = 72
	build_ui(world)
	reset_flight()

func build_ui(world: Node3D) -> void:
	var ui := named(world, CanvasLayer.new(), "UI") as CanvasLayer
	hud = named(ui, Label.new(), "HUD") as Label
	hud.position = Vector2(24, 16)
	hud.add_theme_font_size_override("font_size", 24)
	var touches := named(ui, Control.new(), "TouchLayer") as Control
	touches.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	touches.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var actions := ["left", "right", "up", "down", "faster", "slower"]
	var labels := ["LEFT", "RIGHT", "CLIMB", "DESCEND", "FASTER", "SLOWER"]
	for i in range(actions.size()):
		var b := Button.new()
		b.text = labels[i]
		touches.add_child(b)
		b.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_LEFT)
		b.position = Vector2(22 + i * 135, -92)
		b.size = Vector2(125, 70)
		b.focus_mode = Control.FOCUS_NONE
		b.button_down.connect(set_touch.bind(actions[i], true))
		b.button_up.connect(set_touch.bind(actions[i], false))
	var pause := Button.new()
	pause.text = "PAUSE [P]"
	ui.add_child(pause)
	pause.position = Vector2(1030, 20)
	pause.size = Vector2(220, 48)
	pause.pressed.connect(toggle_pause)
	var reset := Button.new()
	reset.text = "RESTART [R]"
	ui.add_child(reset)
	reset.position = Vector2(1030, 76)
	reset.size = Vector2(220, 48)
	reset.pressed.connect(reset_flight)
	var layer := named(ui, Control.new(), "PauseLayer") as Control
	layer.mouse_filter = Control.MOUSE_FILTER_IGNORE
	pause_panel = named(layer, PanelContainer.new(), "PausePanel") as PanelContainer
	pause_panel.position = Vector2(410, 260)
	pause_panel.size = Vector2(460, 140)
	pause_label = Label.new()
	pause_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	pause_label.add_theme_font_size_override("font_size", 24)
	pause_panel.add_child(pause_label)

func set_touch(action: String, held: bool) -> void:
	if held and not flight.paused:
		touch[action] = true
	else:
		touch.erase(action)

func held(action: String, key: Key, alternative: Key) -> float:
	return 1.0 if touch.has(action) or Input.is_physical_key_pressed(key) or Input.is_physical_key_pressed(alternative) else 0.0

func _unhandled_key_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo:
		if event.keycode == KEY_P or event.keycode == KEY_ESCAPE:
			toggle_pause()
		elif event.keycode == KEY_R:
			reset_flight()

func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_FOCUS_OUT and is_instance_valid(pause_panel):
		touch.clear()
		flight.set_paused(true)
		refresh_ui()

func toggle_pause() -> void:
	touch.clear()
	if not course.complete:
		flight.set_paused(not flight.paused)
	refresh_ui()

func reset_flight() -> void:
	touch.clear()
	flight.reset()
	course.reset()
	for gate in gates:
		gate.visible = true
	rig.position = flight.position
	rig.rotation = Vector3.ZERO
	refresh_ui()

func _physics_process(delta: float) -> void:
	var previous: Vector3 = flight.position
	var recovery: int = flight.recoveries
	var turn := held("left", KEY_A, KEY_LEFT) - held("right", KEY_D, KEY_RIGHT)
	var climb := held("up", KEY_W, KEY_UP) - held("down", KEY_S, KEY_DOWN)
	var throttle := held("faster", KEY_E, KEY_PAGEUP) - held("slower", KEY_Q, KEY_PAGEDOWN)
	flight.step(delta, turn, climb, throttle)
	# Treat terrain contact as a forgiving return to the start, not a crash.
	if not flight.paused and flight.position.y < terrain.height_at(flight.position.x, flight.position.z) + 3.0:
		reset_flight()
		return
	if flight.recoveries != recovery:
		course.reset()
		for gate in gates:
			gate.visible = true
	elif not flight.paused and course.advance(previous, flight.position):
		gates[course.index - 1].visible = false
		if course.complete:
			flight.set_paused(true)
			touch.clear()
	rig.position = flight.position
	rig.rotation.y = flight.heading
	rig.get_node("Drone").rotation.z = -turn * 0.18
	refresh_ui()

func refresh_ui() -> void:
	if not is_instance_valid(hud):
		return
	hud.text = "SAFFRON SKY  /  FLIGHT COURSE\nGates %d / 7    Speed %d m/s    Time %.1f s\nWASD / arrows: steer & climb   Q/E: speed\n" % [course.index, int(flight.speed), flight.elapsed]
	if not course.complete:
		var target: Vector3 = course.points[course.index]
		hud.text += "Next gate: X %.0f   Altitude %.0f   Distance %.0f m" % [target.x, target.y, flight.position.distance_to(target)]
	pause_panel.visible = flight.paused
	pause_label.text = "COURSE COMPLETE\nPress RESTART to fly again" if course.complete else "PAUSED\nPress PAUSE to resume"
