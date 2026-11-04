extends Node2D

# TankForge visual target: an isometric 2.5D battlefield rendered with layered
# polygons, ambient lighting, cast shadows, animated tracks and muzzle flashes.
const MAP_W := 24
const MAP_H := 18
const TILE_W := 64.0
const TILE_H := 32.0
const ORIGIN := Vector2(640.0, 360.0)

var clock := 0.0
var muzzle_flash := 0.0
var recoil := 0.0
var track_phase := 0.0
var player_pos := Vector2.ZERO
var player_angle := 0.0
var fire_cooldown := 0.0
var smoke: Array[Dictionary] = []
var sparks: Array[Dictionary] = []
var shells: Array[Dictionary] = []
var enemies: Array[Dictionary] = []
var player_health := 3
var score := 12450
var touch_move := Vector2.ZERO
var touch_fire := false

func _ready() -> void:
    enemies = [
        {"pos": Vector2(4.0, -2.0), "angle": PI, "hp": 2, "cooldown": 1.0, "paint": Color(0.66, 0.30, 0.22)},
        {"pos": Vector2(-5.0, 2.0), "angle": -0.35, "hp": 2, "cooldown": 2.0, "paint": Color(0.72, 0.57, 0.25)}
    ]
    queue_redraw()

func _input(event: InputEvent) -> void:
    if event is InputEventScreenTouch:
        _set_touch(event.position, event.pressed)
    elif event is InputEventScreenDrag:
        _set_touch(event.position, true)

func _set_touch(point: Vector2, pressed: bool) -> void:
    if not pressed:
        touch_move = Vector2.ZERO
        touch_fire = false
        return
    var p := point * Vector2(1280.0 / get_viewport_rect().size.x, 720.0 / get_viewport_rect().size.y)
    if p.x < 330.0 and p.y > 430.0:
        touch_move = (p - Vector2(150.0, 570.0)) / 78.0
        if touch_move.length() > 1.0:
            touch_move = touch_move.normalized()
    elif p.x > 930.0 and p.y > 420.0:
        touch_fire = true

func _process(delta: float) -> void:
    clock += delta
    track_phase += delta * 7.0
    _update_player(delta)
    muzzle_flash = maxf(0.0, muzzle_flash - delta * 3.5)
    recoil = move_toward(recoil, 0.0, delta * 4.0)
    if fmod(clock, 0.18) < delta:
        _spawn_dust()
    _update_particles(delta)
    _update_combat(delta)
    queue_redraw()

func _draw() -> void:
    _draw_background()
    _draw_world()
    _draw_tank(player_pos, player_angle, Color(0.24, 0.57, 0.39), true)
    for enemy in enemies:
        _draw_tank(enemy.pos, enemy.angle, enemy.paint, false)
    _draw_shells()
    _draw_particles()
    _draw_vignette()
    _draw_hud()

func _iso(x: float, y: float, z: float = 0.0) -> Vector2:
    return ORIGIN + Vector2((x - y) * TILE_W * 0.5, (x + y) * TILE_H * 0.5 - z)

func _update_player(delta: float) -> void:
    var movement := Vector2(
        float(Input.is_key_pressed(KEY_D) or Input.is_key_pressed(KEY_RIGHT)) - float(Input.is_key_pressed(KEY_A) or Input.is_key_pressed(KEY_LEFT)),
        float(Input.is_key_pressed(KEY_S) or Input.is_key_pressed(KEY_DOWN)) - float(Input.is_key_pressed(KEY_W) or Input.is_key_pressed(KEY_UP))
    )
    movement += touch_move
    if movement.length() > 0.01:
        movement = movement.normalized()
        player_pos += movement * delta * 2.7
        player_pos.x = clampf(player_pos.x, -9.0, 9.0)
        player_pos.y = clampf(player_pos.y, -6.0, 6.0)
        player_angle = movement.angle()
    fire_cooldown = maxf(0.0, fire_cooldown - delta)
    if (Input.is_key_pressed(KEY_SPACE) or touch_fire) and fire_cooldown <= 0.0:
        muzzle_flash = 1.0
        recoil = 1.0
        fire_cooldown = 0.22
        var direction := Vector2(cos(player_angle), sin(player_angle))
        shells.append({"pos": player_pos + direction, "vel": direction * 10.0, "team": 0, "life": 1.5})
        var muzzle := _iso(player_pos.x, player_pos.y) + Vector2(cos(player_angle), sin(player_angle) * 0.48) * 52.0
        for i in range(9):
            var a := randf_range(0.0, TAU)
            var speed := randf_range(65.0, 210.0)
            sparks.append({"pos": muzzle, "vel": Vector2(cos(a), sin(a)) * speed, "life": randf_range(0.18, 0.42)})

func _draw_background() -> void:
    draw_rect(Rect2(-1000, -1000, 3000, 2500), Color(0.035, 0.055, 0.06))
    # Soft radial pools of light give the flat canvas a sense of atmosphere.
    for i in range(7, 0, -1):
        var radius := float(i) * 125.0
        draw_circle(Vector2(640, 350), radius, Color(0.24, 0.34, 0.27, 0.018))

func _draw_world() -> void:
    # Isometric ground plane with subtle per-tile variation and bevelled edges.
    for y in range(MAP_H):
        for x in range(MAP_W):
            var center := _iso(float(x) - 11.5, float(y) - 8.5)
            var tint := 0.92 + 0.08 * sin(float(x * 17 + y * 31))
            var base := Color(0.19, 0.27, 0.22) * tint
            var diamond := PackedVector2Array([
                center + Vector2(0, -TILE_H * 0.5),
                center + Vector2(TILE_W * 0.5, 0),
                center + Vector2(0, TILE_H * 0.5),
                center + Vector2(-TILE_W * 0.5, 0)
            ])
            draw_colored_polygon(diamond, base)
            draw_polyline(diamond + PackedVector2Array([diamond[0]]), Color(0.10, 0.16, 0.13, 0.42), 1.0, true)
            if (x * 7 + y * 13) % 9 == 0:
                draw_line(center + Vector2(-13, 2), center + Vector2(10, -3), Color(0.42, 0.48, 0.34, 0.22), 2.0)
            if (x * 11 + y * 5) % 17 == 0:
                draw_circle(center + Vector2(8, 5), 2.0, Color(0.08, 0.13, 0.11, 0.65))

    # Chunky destructible-looking brick barricades and steel cover.
    for cell in [Vector2i(-7, -3), Vector2i(-6, -3), Vector2i(-5, -3), Vector2i(6, 3), Vector2i(7, 3), Vector2i(8, 3)]:
        _draw_wall(_iso(float(cell.x), float(cell.y)), Color(0.56, 0.30, 0.20))
    for cell in [Vector2i(-8, 4), Vector2i(-7, 4), Vector2i(7, -4)]:
        _draw_wall(_iso(float(cell.x), float(cell.y)), Color(0.38, 0.48, 0.53), true)

    # Small conifers are drawn after terrain so their canopies overlap nearby ground.
    for cell in [Vector2i(-9, -5), Vector2i(-8, -6), Vector2i(-9, -7), Vector2i(8, -5), Vector2i(9, -6), Vector2i(10, -6)]:
        _draw_tree(_iso(float(cell.x), float(cell.y)))

    # Shallow water strip with highlights.
    for i in range(5):
        var p := _iso(-10.0 + float(i), 6.0)
        draw_colored_polygon(PackedVector2Array([p + Vector2(0,-12), p + Vector2(32,0), p + Vector2(0,12), p + Vector2(-32,0)]), Color(0.10, 0.27, 0.34, 0.92))
        draw_line(p + Vector2(-12, -2), p + Vector2(7, -5), Color(0.45, 0.72, 0.75, 0.55), 2.0)
    # Craters and broken track marks add battlefield texture.
    for crater in [Vector2(-3.0, -5.0), Vector2(2.0, 5.0), Vector2(5.0, -1.0), Vector2(-6.0, 1.0)]:
        var cp := _iso(crater.x, crater.y)
        _draw_ellipse(cp, Vector2(20, 9), Color(0.06, 0.10, 0.09, 0.42))
        draw_arc(cp + Vector2(-1, -1), 17.0, PI * 0.08, PI * 0.92, 20, Color(0.48, 0.51, 0.39, 0.22), 2.0)

func _draw_wall(p: Vector2, color: Color, metal: bool = false) -> void:
    var w := 27.0
    var h := 14.0
    var top := PackedVector2Array([p + Vector2(0,-h-12), p + Vector2(w,-12), p + Vector2(0,h-12), p + Vector2(-w,-12)])
    draw_colored_polygon(PackedVector2Array([top[3], top[2], top[2] + Vector2(0, 18), top[3] + Vector2(0,18)]), color.darkened(0.42))
    draw_colored_polygon(PackedVector2Array([top[1], top[2], top[2] + Vector2(0,18), top[1] + Vector2(0,18)]), color.darkened(0.25))
    draw_colored_polygon(top, color.lightened(0.12))
    draw_polyline(top + PackedVector2Array([top[0]]), color.lightened(0.35), 1.5, true)
    if not metal:
        draw_line(p + Vector2(-12,-17), p + Vector2(-2,-12), Color(0.25,0.16,0.12,0.8), 2.0)
        draw_line(p + Vector2(4,-10), p + Vector2(15,-15), Color(0.25,0.16,0.12,0.8), 2.0)
    else:
        draw_line(p + Vector2(-16,-16), p + Vector2(12,-16), Color(0.8,0.88,0.9,0.45), 2.0)

func _draw_tree(p: Vector2) -> void:
    _draw_ellipse(p + Vector2(3, 4), Vector2(23, 10), Color(0.02,0.04,0.03,0.42))
    draw_colored_polygon(PackedVector2Array([p + Vector2(0,-35), p + Vector2(18,-5), p + Vector2(0,2), p + Vector2(-18,-5)]), Color(0.10,0.23,0.16))
    draw_colored_polygon(PackedVector2Array([p + Vector2(0,-51), p + Vector2(13,-24), p + Vector2(0,-18), p + Vector2(-13,-24)]), Color(0.18,0.34,0.22))
    draw_line(p + Vector2(0,-35), p + Vector2(0,-8), Color(0.48,0.52,0.31,0.65), 2.0)


func _draw_ellipse(center: Vector2, radii: Vector2, color: Color) -> void:
    var points := PackedVector2Array()
    for i in range(25):
        var a := TAU * float(i) / 24.0
        points.append(center + Vector2(cos(a) * radii.x, sin(a) * radii.y))
    draw_colored_polygon(points, color)

func _draw_tank(pos: Vector2, angle: float, paint: Color, player: bool) -> void:
    var ground := _iso(pos.x, pos.y)
    var bob := sin(clock * 8.0 + pos.x) * 1.2
    var body_pos := ground + Vector2(0, -8 + bob)
    # Soft multi-layer contact shadow.
    _draw_ellipse(ground + Vector2(4, 8), Vector2(37, 17), Color(0.01,0.02,0.02,0.34))
    _draw_ellipse(ground + Vector2(1, 4), Vector2(28, 11), Color(0.01,0.02,0.02,0.28))
    # Tracks: dark side rails, raised links, and animated highlights.
    var track_color := Color(0.12, 0.15, 0.15)
    for side in [-1.0, 1.0]:
        var rail := PackedVector2Array([
            body_pos + Vector2(-27, -3 + side * 12),
            body_pos + Vector2(-19, -12 + side * 12),
            body_pos + Vector2(23, -12 + side * 12),
            body_pos + Vector2(29, -2 + side * 12),
            body_pos + Vector2(21, 8 + side * 12),
            body_pos + Vector2(-21, 8 + side * 12)
        ])
        draw_colored_polygon(rail, track_color)
        draw_polyline(rail + PackedVector2Array([rail[0]]), Color(0.47,0.51,0.46,0.75), 1.4, true)
        for link in range(7):
            var lx := -17.0 + float(link) * 5.2
            draw_line(body_pos + Vector2(lx, -7 + side * 12), body_pos + Vector2(lx + 2, 4 + side * 12), Color(0.62,0.64,0.55,0.35), 1.0)
    # Lower hull and upper glacis create a stacked 2.5D silhouette.
    var hull := PackedVector2Array([body_pos + Vector2(-23,-8), body_pos + Vector2(-11,-17), body_pos + Vector2(20,-12), body_pos + Vector2(25,-2), body_pos + Vector2(10,9), body_pos + Vector2(-20,4)])
    draw_colored_polygon(hull, paint.darkened(0.34))
    var top_hull := PackedVector2Array([body_pos + Vector2(-20,-10), body_pos + Vector2(-10,-19), body_pos + Vector2(17,-14), body_pos + Vector2(22,-4), body_pos + Vector2(8,5), body_pos + Vector2(-17,1)])
    draw_colored_polygon(top_hull, paint)
    draw_polyline(top_hull + PackedVector2Array([top_hull[0]]), paint.lightened(0.38), 1.5, true)
    draw_colored_polygon(PackedVector2Array([body_pos + Vector2(-10,-18), body_pos + Vector2(17,-14), body_pos + Vector2(8,-8), body_pos + Vector2(-16,-11)]), paint.lightened(0.18))
    # Rear engine vents and front armour plate.
    draw_line(body_pos + Vector2(-15,-7), body_pos + Vector2(-8,-13), Color(0.08,0.11,0.10,0.8), 2.0)
    draw_line(body_pos + Vector2(-10,-5), body_pos + Vector2(-3,-11), Color(0.08,0.11,0.10,0.8), 2.0)
    draw_colored_polygon(PackedVector2Array([body_pos + Vector2(14,-12), body_pos + Vector2(23,-4), body_pos + Vector2(11,3), body_pos + Vector2(6,-7)]), paint.lightened(0.12))
    # Rotating turret and barrel.
    var turret_center := body_pos + Vector2(-1, -14)
    var barrel_dir := Vector2(cos(angle), sin(angle) * 0.48)
    var barrel_start := turret_center + barrel_dir * 3.0
    var barrel_end := turret_center + barrel_dir * (35.0 if player else 32.0) - Vector2(0, recoil * 3.0)
    draw_line(barrel_start + Vector2(1, 3), barrel_end + Vector2(1, 3), Color(0.04,0.06,0.06), 8.0)
    draw_line(barrel_start, barrel_end, Color(0.24,0.29,0.27), 5.5)
    draw_line(barrel_start + Vector2(0,-1), barrel_end + Vector2(0,-1), Color(0.63,0.68,0.58,0.75), 1.3)
    draw_circle(turret_center + Vector2(0,2), 14.0, paint.darkened(0.42))
    draw_circle(turret_center, 13.0, paint.lightened(0.12))
    draw_arc(turret_center + Vector2(-1,-1), 12.0, PI, TAU * 0.92, 24, paint.lightened(0.48), 1.5)
    draw_circle(turret_center + Vector2(-3,-3), 4.0, paint.darkened(0.25))
    # Identification light and subtle team marking.
    draw_circle(body_pos + Vector2(-14,-10), 2.2, Color(0.95,0.72,0.30) if player else Color(0.85,0.30,0.22))
    if player:
        draw_arc(ground + Vector2(0,-3), 43.0, PI + 0.3, TAU - 0.3, 40, Color(0.38,0.95,0.65,0.34), 2.0)
    if muzzle_flash > 0.0 and player:
        var flash := barrel_end
        draw_circle(flash, 11.0 * muzzle_flash, Color(1.0,0.70,0.25, muzzle_flash))
        draw_colored_polygon(PackedVector2Array([flash + Vector2(15,0), flash + Vector2(0,-5), flash + Vector2(-7,0), flash + Vector2(0,5)]), Color(1.0,0.9,0.55,muzzle_flash))

func _update_combat(delta: float) -> void:
    for i in range(enemies.size() - 1, -1, -1):
        var enemy: Dictionary = enemies[i]
        var to_player: Vector2 = player_pos - enemy.pos
        var distance := to_player.length()
        if distance > 3.0:
            var direction := to_player.normalized()
            enemy.pos += direction * delta * 0.48
            enemy.angle = direction.angle()
        enemy.cooldown = maxf(0.0, enemy.cooldown - delta)
        if distance < 10.0 and enemy.cooldown <= 0.0:
            var aim := to_player.normalized()
            shells.append({"pos": enemy.pos + aim, "vel": aim * 7.0, "team": 1, "life": 1.8})
            enemy.cooldown = randf_range(1.3, 2.1)
        enemies[i] = enemy

    for i in range(shells.size() - 1, -1, -1):
        var shell: Dictionary = shells[i]
        shell.pos += shell.vel * delta
        shell.life -= delta
        var hit := false
        if int(shell.team) == 0:
            for j in range(enemies.size() - 1, -1, -1):
                var enemy: Dictionary = enemies[j]
                if shell.pos.distance_to(enemy.pos) < 0.85:
                    enemy.hp -= 1
                    _burst(_iso(shell.pos.x, shell.pos.y), Color(1.0, 0.55, 0.22))
                    hit = true
                    if enemy.hp <= 0:
                        score += 500
                        enemies.remove_at(j)
                        var replacement_pos := Vector2(randf_range(-8.0, 8.0), randf_range(-5.0, 5.0))
                        enemies.append({"pos": replacement_pos, "angle": PI, "hp": 2, "cooldown": 1.4, "paint": Color(0.66, 0.30, 0.22)})
                    else:
                        enemies[j] = enemy
                    break
        elif shell.pos.distance_to(player_pos) < 0.8:
            player_health = maxi(0, player_health - 1)
            _burst(_iso(player_pos.x, player_pos.y), Color(1.0, 0.25, 0.16))
            hit = true
        if hit or shell.life <= 0.0 or absf(shell.pos.x) > 13.0 or absf(shell.pos.y) > 10.0:
            shells.remove_at(i)
        else:
            shells[i] = shell

func _burst(point: Vector2, tint: Color) -> void:
    for n in range(12):
        var a := randf_range(0.0, TAU)
        var speed := randf_range(25.0, 115.0)
        sparks.append({"pos": point, "vel": Vector2(cos(a), sin(a)) * speed, "life": randf_range(0.2, 0.55), "tint": tint})

func _draw_shells() -> void:
    for shell in shells:
        var p := _iso(shell.pos.x, shell.pos.y) + Vector2(0, -10)
        draw_circle(p, 4.0, Color(1.0, 0.72, 0.31))
        draw_circle(p, 2.0, Color(1.0, 0.95, 0.68))

func _draw_particles() -> void:
    for particle in smoke:
        var life: float = particle.life
        draw_circle(particle.pos, particle.size, Color(0.55,0.61,0.57, clampf(life,0,0.28)))
    for particle in sparks:
        draw_line(particle.pos, particle.pos - particle.vel * 0.035, particle.get("tint", Color(1.0,0.66,0.27)).lerp(Color.WHITE, 0.25), 2.0)

func _spawn_dust() -> void:
    if smoke.size() > 48:
        return
    smoke.append({"pos": _iso(player_pos.x,player_pos.y) + Vector2(randf_range(-22,22),randf_range(7,15)), "vel": Vector2(randf_range(-5,5),randf_range(-13,-5)), "life": randf_range(0.15,0.28), "size": randf_range(2,5)})

func _update_particles(delta: float) -> void:
    for i in range(smoke.size() - 1, -1, -1):
        smoke[i].pos += smoke[i].vel * delta
        smoke[i].life -= delta * 0.55
        smoke[i].size += delta * 4.0
        if smoke[i].life <= 0.0:
            smoke.remove_at(i)
    for i in range(sparks.size() - 1, -1, -1):
        sparks[i].pos += sparks[i].vel * delta
        sparks[i].life -= delta * 1.7
        if sparks[i].life <= 0.0:
            sparks.remove_at(i)

func _draw_vignette() -> void:
    # Frame darkening adds cinematic focus without expensive post-processing.
    draw_rect(Rect2(0,0,1280,26), Color(0.01,0.02,0.02,0.40))
    draw_rect(Rect2(0,694,1280,26), Color(0.01,0.02,0.02,0.40))

func _draw_hud() -> void:
    draw_string(ThemeDB.fallback_font, Vector2(34,48), "TANKFORGE  /  FRONTLINE", HORIZONTAL_ALIGNMENT_LEFT, -1, 18, Color(0.82,0.88,0.79))
    draw_string(ThemeDB.fallback_font, Vector2(34,76), "SECTOR 04     •     OPERATION IRON VEIL", HORIZONTAL_ALIGNMENT_LEFT, -1, 12, Color(0.55,0.68,0.59))
    draw_string(ThemeDB.fallback_font, Vector2(1040,48), "SCORE  %06d" % score, HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color(0.96,0.78,0.43))
    draw_string(ThemeDB.fallback_font, Vector2(1040,74), "ARMOUR  " + "♥ ".repeat(player_health), HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color(0.78,0.86,0.78))
    draw_line(Vector2(32,92), Vector2(1248,92), Color(0.75,0.82,0.72,0.25), 1.0)
    draw_string(ThemeDB.fallback_font, Vector2(34,680), "WASD / ARROWS  MOVE     SPACE  FIRE", HORIZONTAL_ALIGNMENT_LEFT, -1, 13, Color(0.63,0.73,0.65))
    draw_string(ThemeDB.fallback_font, Vector2(1040,680), "2.5D  /  TOUCH READY", HORIZONTAL_ALIGNMENT_LEFT, -1, 12, Color(0.63,0.73,0.65))
    draw_circle(Vector2(150,570), 66.0, Color(0.12,0.18,0.16,0.48))
    draw_arc(Vector2(150,570), 66.0, 0.0, TAU, 48, Color(0.64,0.76,0.66,0.55), 2.0)
    draw_circle(Vector2(150,570) + touch_move * 36.0, 23.0, Color(0.42,0.62,0.49,0.72))
    draw_circle(Vector2(1120,570), 58.0, Color(0.38,0.12,0.09,0.55))
    draw_arc(Vector2(1120,570), 58.0, 0.0, TAU, 48, Color(0.96,0.58,0.37,0.65), 2.0)
    draw_string(ThemeDB.fallback_font, Vector2(1084,576), "FIRE", HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color(1.0,0.86,0.72))
