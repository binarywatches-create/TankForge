package com.tankforge.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

class GameView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cols = 40
    private val rows = 30
    private var cell = 24f
    private var ox = 0f
    private var oy = 0f
    private var viewTop = 0f
    private var viewBottom = 0f
    private val map = Array(rows) { IntArray(cols) }
    private val brickHp = Array(rows) { IntArray(cols) }
    // Short-lived visual particles: x, y, vx, vy, remaining frames, color.
    // Power-up and enemy-class systems are being added in the next gameplay pass.
    private var tx = 3.5f
    private var ty = 11.5f
    private var dir = 0
    private val enemies = mutableListOf<Pair<Float, Float>>()
    private val enemyDirs = mutableListOf<Int>()
    private val enemyTypes = mutableListOf<Int>()
    private val enemyHp = mutableListOf<Int>()
    private val enemyLastShot = mutableListOf<Long>()
    private val bullets = mutableListOf<FloatArray>()
    private val effects = mutableListOf<FloatArray>()
    private val bonuses = mutableListOf<FloatArray>()
    private var shieldUntil = 0L
    private var rapidUntil = 0L
    private var weaponUntil = 0L
    private var lastBonusSpawn = 0L
    private var bonusMessage = ""
    private var bonusMessageUntil = 0L
    private var score = 0
    private var lives = 3
    private var started = false
    private var over = false
    private var move = -1
    private var fire = false
    private var lastFrame = System.currentTimeMillis()
    private var lastShot = 0L
    private var lastSpawn = 0L
    private var lastEnemyShot = 0L
    private var playerHitUntil = 0L
    private var damageFlashUntil = 0L

    private val brick = 0xFFC76B43.toInt()
    private val steel = 0xFF91A7B8.toInt()
    private val green = 0xFF64C78A.toInt()

    private fun reset() {
        for (y in 0 until rows) for (x in 0 until cols) {
            map[y][x] = when {
                x == 0 || y == 0 || x == cols - 1 || y == rows - 1 -> 2
                Random.nextFloat() < .105f -> 1
                else -> when (val roll = Random.nextFloat()) {
                    in 0f..0.025f -> 3 // water
                    in 0.025f..0.065f -> 4 // forest
                    in 0.065f..0.10f -> 5 // ice
                    else -> 0
                }
            }
        }
        for (y in 0 until rows) for (x in 0 until cols) brickHp[y][x] = if (map[y][x] == 1) 2 else 0
        effects.clear()
        // Keep the player's starting area clear.
        for (y in 9..12) for (x in 1..6) map[y][x] = 0
        tx = 3f
        ty = 11f
        dir = 0
        enemies.clear()
        enemyDirs.clear()
        enemyTypes.clear()
        enemyHp.clear()
        enemyLastShot.clear()
        bullets.clear()
        bonuses.clear()
        shieldUntil = 0L
        rapidUntil = 0L
        weaponUntil = 0L
        val now = System.currentTimeMillis()
        lastBonusSpawn = now
        score = 0
        lives = 3
        over = false
        started = true
        lastFrame = now
        lastShot = 0L
        lastSpawn = now
        lastEnemyShot = now
        playerHitUntil = 0L
        damageFlashUntil = 0L
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawColor(0xFF101B20.toInt())
        // Size the world for landscape play. When the map is narrower than the
        // screen, centre it; only clamp the camera when the map is larger.
        val screenWidth = width.toFloat().coerceAtLeast(1f)
        val screenHeight = height.toFloat().coerceAtLeast(1f)
        cell = min(screenWidth / 13.2f, screenHeight / 20f).coerceAtLeast(1f)
        viewTop = (cell * 1.18f).coerceAtMost(screenHeight * .25f)
        viewBottom = (screenHeight - cell * 2.55f)
            .coerceAtLeast(viewTop + 1f)
            .coerceAtMost(screenHeight)

        val mapWidth = cols * cell
        ox = if (mapWidth <= screenWidth) {
            (screenWidth - mapWidth) / 2f
        } else {
            (screenWidth / 2f - (tx + .5f) * cell)
                .coerceIn(screenWidth - mapWidth, 0f)
        }

        val mapHeight = rows * cell
        val playfieldHeight = viewBottom - viewTop
        oy = if (mapHeight <= playfieldHeight) {
            viewTop + (playfieldHeight - mapHeight) / 2f
        } else {
            val cameraCenterY = (viewTop + viewBottom) / 2f
            (cameraCenterY - (ty + .5f) * cell)
                .coerceIn(viewBottom - mapHeight, viewTop)
        }

        if (started && !over) updateGame()
        val worldSave = c.save()
        c.clipRect(0f, viewTop, width.toFloat(), viewBottom)
        drawField(c)
        for (y in 0 until rows) for (x in 0 until cols) {
            when (map[y][x]) {
                1 -> drawBrick(c, x, y)
                2 -> drawSteel(c, x, y)
                3 -> drawWater(c, x, y)
                5 -> drawIce(c, x, y)
            }
        }
        if (System.currentTimeMillis() > playerHitUntil || (System.currentTimeMillis() / 100) % 2L == 0L) tank(c, tx, ty, dir, true)
        bonuses.forEach { drawBonus(c, it) }
        enemies.forEachIndexed { i, pos -> tank(c, pos.first, pos.second, enemyDirs.getOrElse(i) { 2 }, false, enemyTypes.getOrElse(i) { 0 }) }
        drawForestCanopies(c)
        drawEffects(c)
        bullets.forEach { b ->
            val bx = ox + (b[0] + .5f) * cell
            val by = oy + (b[1] + .5f) * cell
            p.color = 0xFFFFE08A.toInt()
            c.drawCircle(bx, by, cell * .17f, p)
            p.color = 0xFFFFA52E.toInt()
            c.drawCircle(bx, by, cell * .085f, p)
        }
        c.restoreToCount(worldSave)
        drawHud(c)
        drawControls(c)
        if (System.currentTimeMillis() < bonusMessageUntil) {
            p.color = 0xFFFFE28A.toInt()
            p.textAlign = Paint.Align.CENTER
            p.textSize = cell * .42f
            c.drawText(bonusMessage, width / 2f, viewBottom - cell * .18f, p)
            p.textAlign = Paint.Align.LEFT
        }

        if (!started || over) {
            p.color = 0xD9081118.toInt()
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
            p.textAlign = Paint.Align.CENTER
            p.color = 0xFFFFC857.toInt()
            p.textSize = cell * 1.45f
            p.setShadowLayer(cell * .12f, 0f, 2f, 0xFF000000.toInt())
            c.drawText("TANKFORGE", width / 2f, height * .39f, p)
            p.clearShadowLayer()
            p.color = 0xFFE6F0F2.toInt()
            p.textSize = cell * .58f
            c.drawText(if (over) "MISSION FAILED" else "ARCADE TANK BATTLE", width / 2f, height * .51f, p)
            p.color = 0xFFFFD36A.toInt()
            c.drawText(if (over) "TAP TO PLAY AGAIN" else "TAP TO START", width / 2f, height * .64f, p)
            p.textAlign = Paint.Align.LEFT
        }
        if (started && !over) postInvalidateDelayed(16)
    }

    private fun updateGame() {
        val now = System.currentTimeMillis()
        val dt = ((now - lastFrame).coerceIn(1, 40)) / 16f
        lastFrame = now
        updateEffects(dt)

        if (move >= 0) {
            dir = move
            val onIce = terrainAt(tx, ty) == 5
            val speed = (if (onIce) .095f else .075f) * dt
            val nx = tx + when (dir) { 1 -> speed; 3 -> -speed; else -> 0f }
            val ny = ty + when (dir) { 2 -> speed; 0 -> -speed; else -> 0f }
            if (canTankMove(nx, ny) && !touchesEnemy(nx, ny)) { tx = nx; ty = ny }
        }

        if (fire && now - lastShot >= if (now < rapidUntil) 105L else 260L) {
            shoot(tx, ty, dir, true)
            lastShot = now
        }

        if (now - lastBonusSpawn > 8500L && bonuses.size < 2 && Random.nextFloat() < .05f) {
            val bx = Random.nextInt(2, cols - 2)
            val by = Random.nextInt(2, rows - 2)
            if (isWalkableTile(map[by][bx]) && abs(tx - bx) + abs(ty - by) > 3f) {
                bonuses.add(floatArrayOf(bx.toFloat(), by.toFloat(), Random.nextInt(0, 5).toFloat(), (now + 12000L).toFloat()))
                lastBonusSpawn = now
            }
        }
        val bonusIterator = bonuses.iterator()
        while (bonusIterator.hasNext()) {
            val b = bonusIterator.next()
            if (now > b[3].toLong()) { bonusIterator.remove(); continue }
            if (abs(tx - b[0]) < .58f && abs(ty - b[1]) < .58f) {
                when (b[2].toInt()) {
                    0 -> { shieldUntil = now + 8000L; bonusMessage = "SHIELD ACTIVATED" }
                    1 -> { lives = (lives + 1).coerceAtMost(5); bonusMessage = "REPAIR +1 LIFE" }
                    2 -> { rapidUntil = now + 9000L; bonusMessage = "RAPID FIRE" }
                    3 -> { enemies.forEach { spawnExplosion(it.first, it.second, 0xFFFFA64D.toInt(), 22) }; enemies.clear(); enemyDirs.clear(); enemyTypes.clear(); enemyHp.clear(); enemyLastShot.clear(); score += 250; bonusMessage = "MEGA BOMB!" }
                    else -> { weaponUntil = now + 12000L; bonusMessage = "HEAVY CANNON" }
                }
                bonusMessageUntil = now + 1800L
                bonusIterator.remove()
            }
        }

        if (enemies.size < 4 && now - lastSpawn >= 1450L) {
            val spawnX = listOf(2f, 10f, 18f).filter { canTankMove(it, 1f) && !touchesEnemy(it, 1f) }.randomOrNull()
            if (spawnX != null) {
                val type = Random.nextInt(0, 4)
                enemies.add(spawnX to 1f)
                enemyDirs.add(2)
                enemyTypes.add(type)
                enemyHp.add(if (type == 1) 3 else if (type == 3) 2 else 1)
                enemyLastShot.add(now)
            }
            lastSpawn = now
        }

        val bulletIterator = bullets.iterator()
        while (bulletIterator.hasNext()) {
            val b = bulletIterator.next()
            // Enemy shells are slower, giving the player a fair chance to dodge.
            val bulletSpeed = if (b[4] == 0f) .17f else .24f
            b[0] += b[2] * bulletSpeed * dt
            b[1] += b[3] * bulletSpeed * dt
            val x = b[0].toInt()
            val y = b[1].toInt()
            if (x !in 0 until cols || y !in 0 until rows) {
                bulletIterator.remove()
                continue
            }
            if (map[y][x] == 1 || map[y][x] == 2) {
                if (map[y][x] == 1) {
                    val damage = if (b.getOrElse(5) { 1f } > 1.5f) 2 else 1
                    brickHp[y][x] = (brickHp[y][x] - damage).coerceAtLeast(0)
                    if (brickHp[y][x] == 0) {
                        map[y][x] = 0
                        spawnExplosion(x.toFloat(), y.toFloat(), 0xFFC98255.toInt(), 9)
                    } else {
                        spawnExplosion(b[0], b[1], 0xFFFFC078.toInt(), 4)
                    }
                } else if (map[y][x] == 2 && b[4] == 1f && b.getOrElse(5) { 1f } > 1.5f) {
                    map[y][x] = 0
                    spawnExplosion(x.toFloat(), y.toFloat(), 0xFFB8D8E8.toInt(), 12)
                } else {
                    spawnExplosion(b[0], b[1], 0xFFB8D8E8.toInt(), 3)
                }
                bulletIterator.remove()
                continue
            }
            val hit = enemies.indexOfFirst { abs(it.first - b[0]) < .52f && abs(it.second - b[1]) < .52f }
            if (hit >= 0 && b[4] == 1f) {
                enemyHp[hit] = enemyHp[hit] - if (b.getOrElse(5) { 1f } > 1.5f) 2 else 1
                if (enemyHp[hit] <= 0) {
                    val destroyedType = enemyTypes.getOrElse(hit) { 0 }
                    enemies.removeAt(hit)
                    enemyDirs.removeAt(hit)
                    enemyTypes.removeAt(hit)
                    enemyHp.removeAt(hit)
                    enemyLastShot.removeAt(hit)
                    score += 100
                    spawnExplosion(b[0], b[1], when (destroyedType) { 2 -> 0xFFFFC05A.toInt(); 3 -> 0xFFD7A6FF.toInt(); else -> 0xFFFF765C.toInt() }, if (destroyedType == 2) 24 else 16)
                    if (Random.nextFloat() < .28f && bonuses.size < 2) bonuses.add(floatArrayOf(b[0].coerceIn(2f, cols - 3f), b[1].coerceIn(2f, rows - 3f), Random.nextInt(0, 5).toFloat(), (now + 12000L).toFloat()))
                }
                bulletIterator.remove()
                continue
            }
            if (b[4] == 0f && abs(tx - b[0]) < .42f && abs(ty - b[1]) < .42f && now >= playerHitUntil && now >= shieldUntil) {
                bulletIterator.remove()
                lives--
                spawnExplosion(tx, ty, 0xFF69D68A.toInt(), 18)
                playerHitUntil = now + 1850L
                damageFlashUntil = now + 260L
                tx = 3f
                ty = 11f
                if (lives <= 0) over = true
            }
        }

        for (i in enemies.indices) {
            val (ex, ey) = enemies[i]
            val type = enemyTypes.getOrElse(i) { 0 }
            val attackDir = alignedAttackDirection(ex, ey, tx, ty)
            if (attackDir >= 0 && hasClearShot(ex, ey, tx, ty, attackDir)) {
                enemyDirs[i] = attackDir
                // More deliberate enemy fire keeps pressure without overwhelming the player.
                val cooldown = when (type) { 0 -> 1850L; 1 -> 1500L; 2 -> 2650L; else -> 1950L }
                if (now - enemyLastShot.getOrElse(i) { now } >= cooldown) {
                    shoot(ex, ey, attackDir, false)
                    enemyLastShot[i] = now
                }
                continue
            }

            val nextDir = findEnemyPathDirection(ex, ey, tx, ty, type, i)
            if (nextDir >= 0) {
                enemyDirs[i] = nextDir
                val terrainSpeed = if (terrainAt(ex, ey) == 5) 1.22f else 1f
                val speed = when (type) { 0 -> .032f; 1 -> .017f; 2 -> .013f; else -> .022f } * dt * terrainSpeed
                val nx = ex + when (nextDir) { 1 -> speed; 3 -> -speed; else -> 0f }
                val ny = ey + when (nextDir) { 2 -> speed; 0 -> -speed; else -> 0f }
                val blockedByEnemy = enemies.indices.any { j ->
                    j != i && abs(enemies[j].first - nx) < .62f && abs(enemies[j].second - ny) < .62f
                }
                if (canTankMove(nx, ny) && !touchesPlayer(nx, ny) && !blockedByEnemy) {
                    enemies[i] = nx to ny
                }
            }
        }
    }


    private fun spawnExplosion(x: Float, y: Float, color: Int, count: Int) {
        repeat(count) {
            val angle = Random.nextFloat() * (Math.PI.toFloat() * 2f)
            val speed = .035f + Random.nextFloat() * .12f
            effects.add(floatArrayOf(
                x, y, kotlin.math.cos(angle) * speed, kotlin.math.sin(angle) * speed,
                12f + Random.nextFloat() * 18f, color.toFloat()
            ))
        }
    }

    private fun updateEffects(dt: Float) {
        val iterator = effects.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            e[0] += e[2] * dt
            e[1] += e[3] * dt
            e[2] *= .965f
            e[3] *= .965f
            e[4] -= dt
            if (e[4] <= 0f) iterator.remove()
        }
    }

    private fun drawEffects(c: Canvas) {
        effects.forEach { e ->
            val alpha = (255f * (e[4] / 30f).coerceIn(0f, 1f)).toInt()
            p.color = (e[5].toInt() and 0x00FFFFFF) or (alpha shl 24)
            val x = ox + (e[0] + .5f) * cell
            val y = oy + (e[1] + .5f) * cell
            val radius = cell * (.035f + .09f * (e[4] / 30f).coerceIn(0f, 1f))
            c.drawCircle(x, y, radius, p)
        }
    }

    // Return a cardinal direction when the player is on the same clear row or column.
    private fun alignedAttackDirection(ex: Float, ey: Float, px: Float, py: Float): Int {
        if (abs(ex - px) < .42f) {
            if (py < ey && hasClearShot(ex, ey, px, py, 0)) return 0
            if (py > ey && hasClearShot(ex, ey, px, py, 2)) return 2
        }
        if (abs(ey - py) < .42f) {
            if (px > ex && hasClearShot(ex, ey, px, py, 1)) return 1
            if (px < ex && hasClearShot(ex, ey, px, py, 3)) return 3
        }
        return -1
    }

    private fun hasClearShot(ex: Float, ey: Float, px: Float, py: Float, direction: Int): Boolean {
        val steps = when (direction) {
            0 -> (ey - py).toInt()
            1 -> (px - ex).toInt()
            2 -> (py - ey).toInt()
            else -> (ex - px).toInt()
        }
        if (steps <= 0) return false
        for (step in 1 until steps) {
            val x = (ex + when (direction) { 1 -> step.toFloat(); 3 -> -step.toFloat(); else -> 0f }).toInt()
            val y = (ey + when (direction) { 2 -> step.toFloat(); 0 -> -step.toFloat(); else -> 0f }).toInt()
            if (x !in 0 until cols || y !in 0 until rows || map[y][x] != 0) return false
        }
        return true
    }

    // Breadth-first search over walkable tile centres. Enemies seek a tile beside the player,
    // rather than trying to occupy the player's tile.
    private fun findEnemyPathDirection(ex: Float, ey: Float, px: Float, py: Float, type: Int, enemyIndex: Int): Int {
        val sx = ex.toInt().coerceIn(1, cols - 2)
        val sy = ey.toInt().coerceIn(1, rows - 2)
        val targetX = px.toInt().coerceIn(1, cols - 2)
        val targetY = py.toInt().coerceIn(1, rows - 2)
        val goals = if (type >= 2) {
            // Tactical tanks seek a reachable firing lane several tiles away,
            // favouring side angles instead of driving straight into the player.
            buildSet {
                for (gy in 1 until rows - 1) for (gx in 1 until cols - 1) {
                    val distance = abs(gx - targetX) + abs(gy - targetY)
                    if (isWalkableTile(map[gy][gx]) && distance in 2..6 &&
                        alignedAttackDirection(gx.toFloat(), gy.toFloat(), px, py) >= 0) {
                        add(gy * cols + gx)
                    }
                }
            }
        } else setOf(
            targetY * cols + targetX - 1,
            targetY * cols + targetX + 1,
            (targetY - 1) * cols + targetX,
            (targetY + 1) * cols + targetX
        )
        val start = sy * cols + sx
        val visited = BooleanArray(cols * rows)
        val firstDirection = IntArray(cols * rows) { -1 }
        val queue = IntArray(cols * rows)
        var head = 0
        var tail = 0
        queue[tail++] = start
        visited[start] = true
        val directions = if (type == 3) {
            when (enemyIndex % 4) {
                0 -> intArrayOf(1, 2, 3, 0)
                1 -> intArrayOf(3, 0, 1, 2)
                2 -> intArrayOf(0, 1, 2, 3)
                else -> intArrayOf(2, 3, 0, 1)
            }
        } else intArrayOf(0, 1, 2, 3)
        while (head < tail) {
            val current = queue[head++]
            if (current in goals && current != start) return firstDirection[current]
            val x = current % cols
            val y = current / cols
            for (direction in directions) {
                val nx = x + when (direction) { 1 -> 1; 3 -> -1; else -> 0 }
                val ny = y + when (direction) { 2 -> 1; 0 -> -1; else -> 0 }
                if (nx !in 1 until cols - 1 || ny !in 1 until rows - 1 || !isWalkableTile(map[ny][nx])) continue
                val next = ny * cols + nx
                if (visited[next]) continue
                visited[next] = true
                firstDirection[next] = if (current == start) direction else firstDirection[current]
                queue[tail++] = next
            }
        }
        return -1
    }

    private fun shoot(x: Float, y: Float, direction: Int, player: Boolean) {
        val dx = when (direction) { 1 -> 1f; 3 -> -1f; else -> 0f }
        val dy = when (direction) { 2 -> 1f; 0 -> -1f; else -> 0f }
        val heavy = player && System.currentTimeMillis() < weaponUntil
        bullets.add(floatArrayOf(x + dx * .48f, y + dy * .48f, dx, dy, if (player) 1f else 0f, if (heavy) 1.8f else 1f))
    }

    // Tank coordinates are measured from tile centres. A tile spans [n-.5, n+.5].
    // Check the complete tank AABB against every overlapped map tile.
    private fun canTankMove(x: Float, y: Float): Boolean {
        val half = .34f
        val minX = kotlin.math.floor(x - half + .5f).toInt()
        val maxX = kotlin.math.floor(x + half + .5f).toInt()
        val minY = kotlin.math.floor(y - half + .5f).toInt()
        val maxY = kotlin.math.floor(y + half + .5f).toInt()
        for (gy in minY..maxY) for (gx in minX..maxX) {
            if (gx !in 0 until cols || gy !in 0 until rows || map[gy][gx] == 1 || map[gy][gx] == 2 || map[gy][gx] == 3) return false
        }
        return true
    }

    private fun touchesEnemy(x: Float, y: Float): Boolean =
        enemies.any { abs(it.first - x) < .68f && abs(it.second - y) < .68f }

    private fun touchesPlayer(x: Float, y: Float): Boolean =
        abs(tx - x) < .68f && abs(ty - y) < .68f

    private fun drawField(c: Canvas) {
        p.color = 0xFF182B32.toInt()
        c.drawRect(ox - cell * .25f, oy - cell * .25f, ox + cols * cell + cell * .25f, oy + rows * cell + cell * .25f, p)
        p.color = 0xFF263F3B.toInt()
        c.drawRoundRect(ox, oy, ox + cols * cell, oy + rows * cell, cell * .12f, cell * .12f, p)
        p.color = 0xFF34534A.toInt()
        for (y in 0 until rows) for (x in 0 until cols) {
            if ((x * 7 + y * 11) % 5 == 0) {
                val left = ox + x * cell
                val top = oy + y * cell
                c.drawLine(left + cell * .22f, top + cell * .72f, left + cell * .35f, top + cell * .58f, p)
                c.drawLine(left + cell * .65f, top + cell * .28f, left + cell * .78f, top + cell * .42f, p)
            }
        }
    }


    private fun isWalkableTile(tile: Int): Boolean = tile == 0 || tile == 4 || tile == 5

    private fun terrainAt(x: Float, y: Float): Int {
        val gx = x.toInt().coerceIn(0, cols - 1)
        val gy = y.toInt().coerceIn(0, rows - 1)
        return map[gy][gx]
    }

    private fun drawWater(c: Canvas, x: Int, y: Int) {
        val l = ox + x * cell
        val t = oy + y * cell
        p.color = 0xFF174F78.toInt()
        c.drawRect(l, t, l + cell, t + cell, p)
        p.color = 0xFF4C9CC4.toInt()
        p.strokeWidth = maxOf(1f, cell * .045f)
        c.drawLine(l + cell * .14f, t + cell * .32f, l + cell * .72f, t + cell * .32f, p)
        c.drawLine(l + cell * .34f, t + cell * .68f, l + cell * .88f, t + cell * .68f, p)
    }

    private fun drawIce(c: Canvas, x: Int, y: Int) {
        val l = ox + x * cell
        val t = oy + y * cell
        p.color = 0xFF9ACBD5.toInt()
        c.drawRect(l, t, l + cell, t + cell, p)
        p.color = 0xFFDDF7F5.toInt()
        p.strokeWidth = maxOf(1f, cell * .035f)
        c.drawLine(l + cell * .16f, t + cell * .25f, l + cell * .72f, t + cell * .25f, p)
        c.drawLine(l + cell * .38f, t + cell * .68f, l + cell * .86f, t + cell * .68f, p)
    }

    private fun drawForestCanopies(c: Canvas) {
        for (y in 0 until rows) for (x in 0 until cols) {
            if (map[y][x] != 4) continue
            val cx = ox + (x + .5f) * cell
            val cy = oy + (y + .5f) * cell
            p.color = 0xDD174A35.toInt()
            c.drawCircle(cx, cy, cell * .43f, p)
            p.color = 0xEE26734A.toInt()
            c.drawCircle(cx - cell * .12f, cy - cell * .10f, cell * .25f, p)
            c.drawCircle(cx + cell * .13f, cy + cell * .08f, cell * .24f, p)
            p.color = 0xFF78A96A.toInt()
            c.drawCircle(cx - cell * .13f, cy - cell * .16f, cell * .055f, p)
        }
    }

    private fun drawBrick(c: Canvas, x: Int, y: Int) {
        val l = ox + x * cell
        val t = oy + y * cell
        p.color = 0xFF60372D.toInt()
        c.drawRoundRect(l + 1, t + 2, l + cell - 1, t + cell - 1, cell * .07f, cell * .07f, p)
        p.color = brick
        c.drawRoundRect(l + 2, t + 2, l + cell - 2, t + cell * .46f, cell * .06f, cell * .06f, p)
        c.drawRoundRect(l + 2, t + cell * .54f, l + cell - 2, t + cell - 2, cell * .06f, cell * .06f, p)
        p.color = 0xFFFFB17A.toInt()
        p.strokeWidth = maxOf(1f, cell * .045f)
        c.drawLine(l + 3, t + 3, l + cell - 3, t + 3, p)
        c.drawLine(l + cell * .5f, t + 3, l + cell * .5f, t + cell * .44f, p)
        c.drawLine(l + cell * .25f, t + cell * .56f, l + cell * .25f, t + cell - 3, p)
        p.color = 0xFF8D4937.toInt()
        c.drawLine(l + 3, t + cell * .48f, l + cell - 3, t + cell * .48f, p)
    }

    private fun drawSteel(c: Canvas, x: Int, y: Int) {
        val l = ox + x * cell
        val t = oy + y * cell
        p.color = 0xFF263D4C.toInt()
        c.drawRoundRect(l + 1, t + 2, l + cell - 1, t + cell - 1, cell * .09f, cell * .09f, p)
        p.color = steel
        c.drawRoundRect(l + 3, t + 3, l + cell - 3, t + cell - 3, cell * .08f, cell * .08f, p)
        p.color = 0xFFDCEAF0.toInt()
        p.strokeWidth = maxOf(1f, cell * .04f)
        c.drawLine(l + 4, t + 4, l + cell - 4, t + 4, p)
        c.drawCircle(l + cell * .2f, t + cell * .2f, cell * .055f, p)
        c.drawCircle(l + cell * .8f, t + cell * .8f, cell * .055f, p)
        p.color = 0xFF506B7B.toInt()
        c.drawLine(l + cell * .22f, t + cell * .52f, l + cell * .78f, t + cell * .52f, p)
    }

    private fun tank(c: Canvas, x: Float, y: Float, d: Int, player: Boolean, type: Int = 0) {
        val cx = ox + (x + .5f) * cell
        val cy = oy + (y + .5f) * cell
        val hull = if (player) 0xFF4FAD72.toInt() else when (type) { 1 -> 0xFF657B9D.toInt(); 2 -> 0xFFD49A42.toInt(); 3 -> 0xFF9A68B8.toInt(); else -> 0xFFCF514A.toInt() }
        val highlight = if (player) 0xFFB5F1B9.toInt() else when (type) { 1 -> 0xFFB8D4F5.toInt(); 2 -> 0xFFFFD88A.toInt(); 3 -> 0xFFE0B8FF.toInt(); else -> 0xFFFFB0A0.toInt() }
        val save = c.save()
        c.rotate(d * 90f, cx, cy)

        // Soft ground shadow and rugged tracks.
        p.color = 0x77071318.toInt()
        c.drawOval(cx - cell * .43f, cy + cell * .27f, cx + cell * .43f, cy + cell * .48f, p)
        p.color = 0xFF111B20.toInt()
        c.drawRoundRect(cx - cell * .45f, cy - cell * .39f, cx - cell * .23f, cy + cell * .39f, cell * .07f, cell * .07f, p)
        c.drawRoundRect(cx + cell * .23f, cy - cell * .39f, cx + cell * .45f, cy + cell * .39f, cell * .07f, cell * .07f, p)
        p.color = 0xFF65747A.toInt()
        for (i in -2..2) {
            val yy = cy + i * cell * .145f
            c.drawRoundRect(cx - cell * .43f, yy - cell * .035f, cx - cell * .25f, yy + cell * .035f, cell * .025f, cell * .025f, p)
            c.drawRoundRect(cx + cell * .25f, yy - cell * .035f, cx + cell * .43f, yy + cell * .035f, cell * .025f, cell * .025f, p)
        }

        // Layered armoured hull with bevels.
        p.color = if (player) 0xFF1F704D.toInt() else 0xFF873A38.toInt()
        c.drawRoundRect(cx - cell * .31f, cy - cell * .34f, cx + cell * .31f, cy + cell * .34f, cell * .09f, cell * .09f, p)
        p.color = hull
        c.drawRoundRect(cx - cell * .285f, cy - cell * .31f, cx + cell * .285f, cy + cell * .29f, cell * .075f, cell * .075f, p)
        p.color = highlight
        c.drawRoundRect(cx - cell * .22f, cy - cell * .27f, cx + cell * .18f, cy - cell * .21f, cell * .025f, cell * .025f, p)
        p.color = if (player) 0xFF26744E.toInt() else 0xFF923C37.toInt()
        c.drawRect(cx - cell * .22f, cy + cell * .13f, cx + cell * .22f, cy + cell * .22f, p)

        // Turret ring, commander hatch and cannon.
        p.color = 0xFF17262A.toInt()
        c.drawCircle(cx, cy, cell * .205f, p)
        p.color = if (player) 0xFF75D58D.toInt() else 0xFFE97965.toInt()
        c.drawCircle(cx, cy, cell * .17f, p)
        p.color = 0xFF263A3C.toInt()
        c.drawCircle(cx, cy, cell * .115f, p)
        p.color = 0xFFE3E9D0.toInt()
        c.drawRoundRect(cx - cell * .055f, cy - cell * .39f, cx + cell * .055f, cy - cell * .06f, cell * .025f, cell * .025f, p)
        p.color = 0xFF26343A.toInt()
        c.drawCircle(cx, cy - cell * .18f, cell * if (!player && type == 1) .105f else .075f, p)
        p.color = if (!player && type == 2) 0xFFFFF0A0.toInt() else 0xFFFFD27B.toInt()
        c.drawCircle(cx, cy - cell * .18f, cell * .025f, p)
        if (!player && type == 3) {
            p.color = 0xFFE5C4FF.toInt()
            c.drawCircle(cx - cell * .34f, cy, cell * .055f, p)
            c.drawCircle(cx + cell * .34f, cy, cell * .055f, p)
        }

        // Small side insignia.
        p.color = if (player) 0xFFFFD66F.toInt() else 0xFFFFD6C9.toInt()
        c.drawCircle(cx, cy + cell * .255f, cell * .045f, p)
        c.restoreToCount(save)
    }

    private fun drawBonus(c: Canvas, b: FloatArray) {
        val x = ox + (b[0] + .5f) * cell
        val y = oy + (b[1] + .5f) * cell
        val colors = intArrayOf(0xFF4CB9F0.toInt(), 0xFF67D58A.toInt(), 0xFFFFD34F.toInt(), 0xFFE86D55.toInt(), 0xFFB88BFF.toInt())
        val labels = arrayOf("S", "+", "R", "B", "W")
        p.color = 0xCC101820.toInt()
        c.drawCircle(x, y, cell * .39f, p)
        p.color = colors[b[2].toInt().coerceIn(0, 4)]
        c.drawCircle(x, y, cell * .31f, p)
        p.color = 0xFF17242B.toInt()
        p.textAlign = Paint.Align.CENTER
        p.textSize = cell * .36f
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        c.drawText(labels[b[2].toInt().coerceIn(0, 4)], x, y + cell * .12f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawHud(c: Canvas) {
        val left = cell * .16f
        val right = width - cell * .16f
        val top = cell * .12f
        val bottom = viewTop - cell * .12f
        p.color = 0xFF101E28.toInt()
        c.drawRoundRect(left, top, right, bottom, cell * .16f, cell * .16f, p)
        p.color = 0xFF4B6976.toInt()
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, cell * .035f)
        c.drawRoundRect(left, top, right, bottom, cell * .13f, cell * .13f, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFEAF0F2.toInt()
        p.textSize = cell * .42f
        p.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        p.textAlign = Paint.Align.LEFT
        c.drawText("SCORE  $score", left + cell * .22f, bottom - cell * .22f, p)
        p.textAlign = Paint.Align.CENTER
        p.color = when { System.currentTimeMillis() < shieldUntil -> 0xFF67D8FF.toInt(); System.currentTimeMillis() < rapidUntil -> 0xFFFFD34F.toInt(); System.currentTimeMillis() < weaponUntil -> 0xFFB88BFF.toInt(); else -> 0xFFB8C8CC.toInt() }
        c.drawText(when { System.currentTimeMillis() < shieldUntil -> "SHIELD"; System.currentTimeMillis() < rapidUntil -> "RAPID"; System.currentTimeMillis() < weaponUntil -> "HEAVY"; else -> "" }, width / 2f, bottom - cell * .22f, p)
        p.textAlign = Paint.Align.RIGHT
        p.color = 0xFFFFD36A.toInt()
        c.drawText("LIVES  $lives", right - cell * .22f, bottom - cell * .22f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawControls(c: Canvas) {
        val s = cell * 1.5f
        // Keep the whole directional pad comfortably inside the screen.
        val cx = s * 1.65f
        val cy = height - s * 1.65f
        val labels = listOf("▲", "▶", "▼", "◀")
        for (d in 0..3) {
            val x = cx + when (d) { 1 -> s; 3 -> -s; else -> 0f }
            val y = cy + when (d) { 2 -> s; 0 -> -s; else -> 0f }
            p.color = 0xCC13252D.toInt()
            c.drawCircle(x, y, s * .4f, p)
            p.color = 0xAA3F6870.toInt()
            c.drawCircle(x, y, s * .32f, p)
            p.color = 0xFF9CB5B8.toInt()
            p.style = Paint.Style.STROKE
            p.strokeWidth = cell * .045f
            c.drawCircle(x, y, s * .38f, p)
            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.textSize = s * .42f
            c.drawText(labels[d], x, y + s * .14f, p)
        }
        val fx = width - s * 1.3f
        val fy = height - s * 1.2f
        p.color = 0xCC702E38.toInt()
        c.drawCircle(fx, fy, s * .58f, p)
        p.color = 0xCCB94A4C.toInt()
        c.drawCircle(fx, fy, s * .48f, p)
        p.color = 0xFFFFB4A8.toInt()
        p.style = Paint.Style.STROKE
        p.strokeWidth = cell * .06f
        c.drawCircle(fx, fy, s * .56f, p)
        p.style = Paint.Style.FILL
        p.color = -1
        p.textSize = s * .25f
        c.drawText("FIRE", fx, fy + s * .1f, p)
        p.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN && (!started || over)) {
            reset()
            invalidate()
            return true
        }
        if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
            move = -1
            fire = false
            return true
        }
        if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
            val s = cell * 1.5f
            fire = e.x > width - s * 2.2f && e.y > height - s * 2.2f
            if (!fire && e.x < width * .48f) {
                // Match the touch origin to the repositioned on-screen controls.
                val dx = e.x - (s * 1.65f)
                val dy = e.y - (height - s * 1.65f)
                move = if (abs(dx) > abs(dy)) { if (dx > 0) 1 else 3 } else { if (dy > 0) 2 else 0 }
            } else if (!fire) {
                move = -1
            }
            invalidate()
        }
        return true
    }
}
