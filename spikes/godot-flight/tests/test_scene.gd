extends SceneTree

var failures := 0
var checks := 0

func check(condition: bool, message: String) -> void:
	checks += 1
	if not condition:
		failures += 1
		print("FAIL: " + message)

func _initialize() -> void:
	call_deferred("run")

func run() -> void:
	if not FileAccess.file_exists("res://scripts/main.gd"):
		check(false, "playable scene implementation exists")
		finish()
		return
	var loaded := load("res://main.tscn")
	check(loaded != null, "main scene loads")
	if loaded == null:
		finish()
		return
	var root: Node3D = loaded.instantiate()
	check(root != null, "main scene instantiates")
	get_root().add_child(root)
	root.set_physics_process(false)
	var world := root.get_node_or_null("World/WorldEnvironment")
	check(world != null and world is WorldEnvironment, "WorldEnvironment exists")
	check(root.get_node_or_null("World/Sun") != null, "directional sun exists")
	check(root.get_node_or_null("World/Ground") != null, "ground mesh exists")
	check(root.get_node_or_null("World/Hills") != null, "hills exist")
	check(root.get_node_or_null("World/Road") != null, "road exists")
	check(root.get_node_or_null("World/Buildings") != null, "buildings exist")
	check(root.get_node_or_null("World/FinishBanner") != null, "finish banner exists")
	var drone := root.get_node_or_null("DroneRig/Drone")
	check(drone != null, "drone exists")
	check(root.get_node_or_null("DroneRig/SpringArm3D/Camera3D") != null, "third-person camera exists")
	check(root.get_node_or_null("DroneRig/Drone/CameraAnchor") != null, "camera anchor exists")
	check(root.get_node_or_null("World/UI") != null and root.get_node_or_null("World/UI") is CanvasLayer, "HUD canvas exists")
	check(root.get_node_or_null("World/UI/PauseLayer/PausePanel") != null, "pause panel exists")
	check(root.get_node_or_null("World/UI/TouchLayer") != null, "touch layer exists")
	var sun := root.get_node_or_null("World/Sun")
	if sun is DirectionalLight3D:
		check(sun.shadow_enabled, "sun casts shadows")
	if not root.has_method("reset_flight"):
		check(false, "playable flight controls exist")
	else:
		check(root.course.points.size() == 7, "level has seven gates")
		check(root.gates.size() == 7, "all gates rendered")
		var start: Vector3 = root.flight.position
		root._physics_process(0.1)
		check(root.flight.position.z < start.z, "scene updates real flight")
		root.toggle_pause()
		var frozen: Vector3 = root.flight.position
		root._physics_process(0.1)
		check(root.flight.position == frozen, "pause button freezes scene")
		root.reset_flight()
		check(root.flight.position == start and not root.flight.paused, "reset resumes at start")
		check(root.get_node("World/UI/TouchLayer").get_child_count() >= 6, "six touch controls available")
		root.set_touch("right", true)
		root._physics_process(0.1)
		check(root.flight.heading < 0.0, "touch right steers right")
		root.toggle_pause()
		check(root.touch.is_empty(), "pause clears held touches")
		root.reset_flight()
		for point in root.course.points:
			root.flight.position = point + Vector3(0, 0, 1)
			root.flight.heading = 0.0
			root._physics_process(0.1)
		check(root.course.complete and root.flight.paused, "scene can finish course and stop timer")
		root.reset_flight()
		check(root.course.index == 0 and not root.course.complete, "replay resets all progress")
	root.free()
	finish()

func finish() -> void:
	print("SCENE TESTS: %d checks, %d failures" % [checks, failures])
	quit(1 if failures else 0)
