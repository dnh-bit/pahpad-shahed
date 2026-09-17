extends Node3D
## Six deterministic flat-shaded terrain strips. No imported assets.
var terrain_radius := 640.0

func height_at(x: float, z: float) -> float:
	var edge := smoothstep(95.0, 390.0, absf(x))
	return 1.8 + sin(x * 0.013 + z * 0.008) * 1.1 + edge * (22.0 + 19.0 * sin(x * 0.022) * cos(z * 0.017))

func build() -> void:
	if get_child_count() > 0:
		return
	var mat := StandardMaterial3D.new()
	mat.vertex_color_use_as_albedo = true
	mat.roughness = 0.96
	for strip in range(6):
		var st := SurfaceTool.new()
		st.begin(Mesh.PRIMITIVE_TRIANGLES)
		for iz in range(16):
			for ix in range(64):
				var x := -terrain_radius + ix * terrain_radius / 32.0
				var z := 320.0 - (strip * 16 + iz) * 20.0
				var step_x := terrain_radius / 32.0
				var a := Vector3(x, height_at(x, z), z)
				var b := Vector3(x + step_x, height_at(x + step_x, z), z)
				var c := Vector3(x, height_at(x, z - 20.0), z - 20.0)
				var d := Vector3(x + step_x, height_at(x + step_x, z - 20.0), z - 20.0)
				var shade := 0.96 + 0.045 * sin(float(ix * 31 + iz * 17 + strip * 5))
				st.set_color(Color(0.69, 0.48, 0.28) * shade if absf(x) > 170 else Color(0.80, 0.64, 0.41) * shade)
				# Clockwise front face, flat normals preserve low-poly facets.
				for vertex in [a, b, c, b, d, c]:
					st.set_smooth_group(-1)
					st.add_vertex(vertex)
		st.generate_normals()
		st.index()
		var mesh := MeshInstance3D.new()
		mesh.name = "DuneStrip%d" % strip
		mesh.mesh = st.commit()
		mesh.material_override = mat
		add_child(mesh)
