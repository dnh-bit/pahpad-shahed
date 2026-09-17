extends SceneTree
var checks := 0
var failures := 0
func check(ok: bool, label: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		print("FAIL: " + label)
func _initialize() -> void:
	if not FileAccess.file_exists("res://scripts/course.gd"):
		check(false, "ordered checkpoint course exists")
	else:
		var course = load("res://scripts/course.gd").new()
		check(course.points.size() == 7, "seven checkpoints")
		var p: Vector3 = course.points[1]
		course.advance(p + Vector3(0,0,30), p - Vector3(0,0,30))
		check(course.index == 0, "out of order checkpoint ignored")
		p = course.points[0]
		course.advance(p + Vector3(30,0,30), p + Vector3(30,0,-30))
		check(course.index == 0, "outside aperture ignored")
		course.advance(p, p)
		check(course.index == 0, "standing in gate does not score")
		course.advance(p - Vector3(0,0,30), p + Vector3(0,0,30))
		check(course.index == 0, "reverse crossing ignored")
		for point in course.points:
			course.advance(point + Vector3(0,0,30), point - Vector3(0,0,30))
		check(course.complete and course.index == 7, "swept high speed crossings complete course")
		course.advance(Vector3.ZERO, Vector3.ZERO)
		check(course.index == 7, "completed course cannot overflow")
		course.reset()
		check(course.index == 0 and not course.complete, "reset clears completion")
	print("COURSE TESTS: %d checks, %d failures" % [checks, failures])
	quit(1 if failures else 0)
