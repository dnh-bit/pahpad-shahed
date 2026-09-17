extends SceneTree

var failures := 0
var checks := 0

func check(condition: bool, message: String) -> void:
	checks += 1
	if not condition:
		failures += 1
		print("FAIL: " + message)

func _initialize() -> void:
	if not FileAccess.file_exists("res://scripts/flight_state.gd"):
		check(false, "flight model exists and moves the drone")
		finish()
		return
	var script = load("res://scripts/flight_state.gd")
	var flight = script.new()
	var start: Vector3 = flight.position
	flight.step(1.0, 0.0, 0.0, 0.0)
	check(flight.position.z < start.z, "neutral input flies forward")
	check(is_equal_approx(flight.position.y, start.y), "neutral input holds altitude")
	check(flight.elapsed > 0.0, "flight timer advances")
	if not flight.has_method("reset") or not flight.has_method("set_paused"):
		check(false, "flight supports pause, reset and safe bounds")
		finish()
		return
	flight.set_paused(true)
	var frozen: Vector3 = flight.position
	var frozen_time: float = flight.elapsed
	flight.step(1.0, 1.0, 1.0, 1.0)
	check(flight.position == frozen and flight.elapsed == frozen_time, "pause freezes flight and clock")
	flight.reset()
	check(flight.position == start and flight.elapsed == 0.0, "reset restores start and clock")
	flight.step(1.0, -1.0, 1.0, 1.0)
	check(flight.position.x > 0.0, "right turn moves right")
	check(flight.position.y > start.y, "climb raises altitude")
	check(flight.speed > 30.0, "throttle increases speed")
	for i in range(1000):
		flight.step(0.1, 0.0, -1.0, -1.0)
	check(flight.position.y >= 7.0 and flight.speed >= 18.0, "floor and minimum speed are safe")
	check(flight.position.z >= -1000.0 and absf(flight.position.x) <= 420.0, "course boundary recovers flight")
	for i in range(1000):
		flight.step(0.1, 0.0, 1.0, 1.0)
	check(flight.position.y <= 110.0 and flight.speed <= 52.0, "ceiling and maximum speed are safe")
	flight.reset()
	var a = script.new()
	for i in range(60):
		flight.step(1.0 / 60.0, 0.0, 0.0, 0.0)
	for i in range(120):
		a.step(1.0 / 120.0, 0.0, 0.0, 0.0)
	check(flight.position.distance_to(a.position) < 0.001, "neutral flight is timestep independent")
	finish()

func finish() -> void:
	print("FLIGHT TESTS: %d checks, %d failures" % [checks, failures])
	quit(1 if failures else 0)
