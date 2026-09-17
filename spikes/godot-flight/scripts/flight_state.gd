extends RefCounted
## Deterministic arcade flight model; coordinates are meters, forward is -Z.
var position := Vector3(0, 22, 50)
var heading := 0.0
var speed := 30.0
var elapsed := 0.0
var paused := false
var recoveries := 0

func reset() -> void:
	position = Vector3(0, 22, 50)
	heading = 0.0
	speed = 30.0
	elapsed = 0.0
	paused = false
	recoveries = 0

func set_paused(value: bool) -> void:
	paused = value

func step(delta: float, turn: float, climb: float, throttle: float) -> void:
	if paused:
		return
	heading += turn * delta * 0.8
	speed = clampf(speed + throttle * delta * 12.0, 18.0, 52.0)
	position += Vector3(-sin(heading) * speed, climb * 13.0, -cos(heading) * speed) * delta
	position.y = clampf(position.y, 7.0, 110.0)
	if absf(position.x) > 420.0 or position.z < -1000.0 or position.z > 220.0:
		position = Vector3(0, 22, 50)
		heading = 0.0
		recoveries += 1
	elapsed += delta
