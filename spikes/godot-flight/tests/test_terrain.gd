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
	if not FileAccess.file_exists("res://scripts/terrain.gd"):
		check(false, "terrain generator exists")
		finish()
		return
	var script = load("res://scripts/terrain.gd")
	check(script != null, "terrain script loads")
	if script == null:
		finish()
		return
	var terrain = script.new()
	terrain.terrain_radius = 640.0
	terrain.build()
	var meshes := []
	var _stack = [terrain]
	while not _stack.is_empty():
		var node = _stack.pop_back()
		for child in node.get_children():
			_stack.append(child)
			if child is MeshInstance3D:
				meshes.append(child)
	check(meshes.size() >= 6, "terrain builds mesh nodes (%d)" % meshes.size())
	var verts := 0
	var faces := 0
	for mesh_instance in meshes:
		var mesh: Mesh = mesh_instance.mesh
		if mesh is ArrayMesh or mesh is PrimitiveMesh:
			for i in range(mesh.get_surface_count()):
				var arrays := mesh.surface_get_arrays(i)
				var positions: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
				var indices: PackedInt32Array = arrays[Mesh.ARRAY_INDEX]
				verts += positions.size()
				faces += indices.size() / 3
	check(verts > 5000, "terrain has real geometry (%d vertices)" % verts)
	check(faces > 5000, "terrain has real geometry (%d triangles)" % faces)
	var height: float = terrain.height_at(0.0, -120.0)
	check(height > 0.0 and height < 40.0, "height_at returns plausible elevation (%.1f)" % height)
	check(terrain.height_at(0.0, -120.0) == terrain.height_at(0.0, -120.0), "height_at is deterministic")
	check(terrain.height_at(10.0, -120.0) == terrain.height_at(10.0, -120.0), "height_at depends on position")
	check(terrain.height_at(0.0, -120.0) != terrain.height_at(300.0, 300.0), "height_at varies across terrain")
	terrain.free()
	finish()

func finish() -> void:
	print("TERRAIN TESTS: %d checks, %d failures" % [checks, failures])
	quit(1 if failures else 0)
