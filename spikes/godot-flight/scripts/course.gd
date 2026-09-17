extends RefCounted
## Ordered one-way gate planes with swept intersection (no tunneling).
var points: Array[Vector3] = [Vector3(0,22,-65), Vector3(-28,28,-180), Vector3(28,36,-300), Vector3(55,29,-420), Vector3(-18,22,-545), Vector3(-48,32,-670), Vector3(0,25,-800)]
var index := 0
var complete := false
const RADIUS := 14.0

func reset() -> void:
	index = 0
	complete = false

func advance(previous: Vector3, current: Vector3) -> bool:
	if complete:
		return false
	var gate := points[index]
	if previous.z <= gate.z or current.z > gate.z:
		return false
	var fraction := (previous.z - gate.z) / (previous.z - current.z)
	var hit := previous.lerp(current, fraction)
	if Vector2(hit.x - gate.x, hit.y - gate.y).length() > RADIUS:
		return false
	index += 1
	complete = index == points.size()
	return true
