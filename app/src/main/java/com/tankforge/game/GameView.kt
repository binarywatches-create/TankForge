package com.tankforge.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
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
    private var level = 1
    private var remainingSpawns = 0
    private var bossPending = false
    private var bossActive = false
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
        score = 0
        lives = 3
        level = 1
        started = true
        over = false
        startLevel(1)
    }

    // Score and lives carry over between stages; every fifth stage is a boss encounter.
    private fun startLevel(nextLevel: Int) {
        level = nextLevel
        for (y in 0 until rows) for (x in 0 until cols) {
            map[y][x] = when {
                x == 0 || y == 0 || x == cols - 1 || y == rows - 1 -> 2
                Random.nextFloat() < (.085f + level.coerceAtMost(8) * .003f) -> 1
                else -> when (Random.nextFloat()) {
                    in 0f..0.025f -> 3
                    in 0.025f..0.065f -> 4
                    in 0.065f..0.10f -> 5
                    else -> 0
                }
            }
        }
        for (y in 0 until rows) for (x in 0 until cols) brickHp[y][x] = if (map[y][x] == 1) 2 else 0
        for (y in 9..12) for (x in 1..6) map[y][x] = 0
        val spawnColumns = listOf(2, 10, 20, 30, 37)
        for (sx in spawnColumns) for (y in 1..4) for (x in (sx - 1)..(sx + 1)) {
            if (x in 1 until cols - 1) map[y][x] = 0
        }
        for (y in 1..6) for (x in 17..23) map[y][x] = 0
        effects.clear()
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
        remainingSpawns = if (level % 5 == 0) 0 else 4 + level * 2
        bossPending = level % 5 == 0
        bossActive = false
        over = false
        lastFrame = now
        lastShot = 0L
        lastSpawn = now - 900L
        lastEnemyShot = now
        playerHitUntil = 0L
        damageFlashUntil = 0L
        bonusMessage = if (bossPending) "BOSS STAGE $level" else "LEVEL $level"
        bonusMessageUntil = now + 2200L
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawColor(0xFF101B20.toInt())
        cell = min(width / 13.2f, height / 20f).coerceAtLeast(1f)
        viewTop = cell * 1.18f
        viewBottom = height - cell * 2.55f
        val cameraCenterY = (viewTop + viewBottom) / 2f
        ox = (width / 2f - (tx + .5f) * cell).coerceIn(width - cols * cell, 0f)
        oy = (cameraCenterY - (ty + .5f) * cell).coerceIn(viewBottom - rows * cell, viewTop)

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
        enemies.forEachIndexed { i, pos ->
            val type = enemyTypes.getOrElse(i) { 0 }
            tank(c, pos.first, pos.second, enemyDirs.getOrElse(i) { 2 }, false, type)
            if (type == 4) drawBossHealth(c, pos.first, pos.second, enemyHp.getOrElse(i) { 1 }, 14 + level * 2)
        }
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
                    3 -> {
                        // Wipe ordinary enemies; a boss takes heavy damage but survives.
                        for (i in enemies.indices.reversed()) {
                            if (enemyTypes[i] == 4) {
                                enemyHp[i] = (enemyHp[i] - 5).coerceAtLeast(1)
                                spawnExplosion(enemies[i].first, enemies[i].second, 0xFFFFC857.toInt(), 18)
                            } else {
                                spawnExplosion(enemies[i].first, enemies[i].second, 0xFFFFA64D.toInt(), 22)
                                enemies.removeAt(i)
                                enemyDirs.removeAt(i)
                                enemyTypes.removeAt(i)
                                enemyHp.removeAt(i)
                                enemyLastShot.removeAt(i)
                            }
                        }
                        score += 250
                        bonusMessage = if (bossActive) "BOSS DAMAGED!" else "MEGA BOMB!"
                    }
                    else -> { weaponUntil = now + 12000L; bonusMessage = "HEAVY CANNON" }
                }
                bonusMessageUntil = now + 1800L
                bonusIterator.remove()
            }
        }

        if (bossPending && enemies.isEmpty() && now - lastSpawn >= 900L) {
            val bossX = 10f
            enemies.add(bossX to 2f)
            enemyDirs.add(2)
            enemyTypes.add(4)
            enemyHp.add(14 + level * 2)
            enemyLastShot.add(now)
            bossPending = false
            bossActive = true
            bonusMessage = "BOSS INCOMING!"
            bonusMessageUntil = now + 2200L
            lastSpawn = now
        } else if (remainingSpawns > 0 && enemies.size < (2 + level / 2).coerceAtMost(5) &&
            now - lastSpawn >= (1450L - level.coerceAtMost(8) * 65L).coerceAtLeast(850L)) {
            val spawnX = listOf(2f, 10f, 20f, 30f, 37f).filter { canTankMove(it, 2f) && !touchesEnemy(it, 2f) }.randomOrNull()
            if (spawnX != null) {
                val type = Random.nextInt(0, 4)
                enemies.add(spawnX to 2f)
                enemyDirs.add(2)
                enemyTypes.add(type)
                val baseHp = if (type == 1) 3 else if (type == 3) 2 else 1
                enemyHp.add(baseHp + (level - 1) / 4)
                enemyLastShot.add(now)
                remainingSpawns--
            }
            lastSpawn = now
        }

        val bulletIterator = bullets.iterator()
        while (bulletIterator.hasNext()) {
            val b = bulletIterator.next()
            b[0] += b[2] * .24f * dt
            b[1] += b[3] * .24f * dt
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
            val hit = enemies.indexOfFirst { index ->
                val radius = if (enemyTypes.getOrElse(index) { 0 } == 4) .78f else .52f
                abs(enemies[index].first - b[0]) < radius && abs(enemies[index].second - b[1]) < radius
            }
            if (hit >= 0 && b[4] == 1f) {
                enemyHp[hit] = enemyHp[hit] - if (b.getOrElse(5) { 1f } > 1.5f) 2 else 1
                if (enemyHp[hit] <= 0) {
                    val destroyedType = enemyTypes.getOrElse(hit) { 0 }
                    enemies.removeAt(hit)
                    enemyDirs.removeAt(hit)
                    enemyTypes.removeAt(hit)
                    enemyHp.removeAt(hit)
                    enemyLastShot.removeAt(hit)
                    score += if (destroyedType == 4) 1500 + level * 100 else 100 + level * 10
                    if (destroyedType == 4) {
                        bossActive = false
                        lives = (lives + 1).coerceAtMost(5)
                        bonusMessage = "BOSS DEFEATED! EXTRA LIFE"
                        bonusMessageUntil = now + 2600L
                    }
                    spawnExplosion(b[0], b[1], when (destroyedType) { 2 -> 0xFFFFC05A.toInt(); 3 -> 0xFFD7A6FF.toInt(); 4 -> 0xFFFFC857.toInt(); else -> 0xFFFF765C.toInt() }, if (destroyedType == 4) 42 else if (destroyedType == 2) 24 else 16)
                    if (Random.nextFloat() < .28f && bonuses.size < 2) bonuses.add(floatArrayOf(b[0].coerceIn(2f, cols - 3f), b[1].coerceIn(2f, rows - 3f), Random.nextInt(0, 5).toFloat(), (now + 12000L).toFloat()))
                }
                bulletIterator.remove()
                continue
            }
            if (b[4] == 0f && abs(tx - b[0]) < .42f && abs(ty - b[1]) < .42f && now >= playerHitUntil && now >= shieldUntil) {
                bulletIterator.remove()
                lives--
                spawnExplosion(tx, ty, 0xFF69D68A.toInt(), 18)
                playerHitUntil = now + 1400L
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
                val cooldown = when (type) { 0 -> 1250L; 1 -> 900L; 2 -> 2050L; 4 -> 760L; else -> 1100L }
                if (now - enemyLastShot.getOrElse(i) { now } >= cooldown) {
                    shoot(ex, ey, attackDir, false)
                    if (type == 4) {
                        shoot(ex, ey, (attackDir + 1) % 4, false)
                        shoot(ex, ey, (attackDir + 3) % 4, false)
                    }
                    enemyLastShot[i] = now
                }
                continue
            }

            val nextDir = findEnemyPathDirection(ex, ey, tx, ty, type, i)
            if (nextDir >= 0) {
                enemyDirs[i] = nextDir
                val terrainSpeed = if (terrainAt(ex, ey) == 5) 1.22f else 1f
                val speed = when (type) { 0 -> .032f; 1 -> .017f; 2 -> .013f; 4 -> .012f; else -> .022f } * dt * terrainSpeed
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

        if (!over && enemies.isEmpty() && remainingSpawns == 0 && !bossPending) {
            startLevel(level + 1)
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
        val goals = if (type == 3) {
            // Tactical tanks seek a reachable firing lane several tiles away,
            // favouring side angles instead of driving straight into the player.
            buildSet {
                for (gy in 1 until rows - 1) for (gx in 1 until cols - 1) {
                    val distance = abs(gx - targetX) + abs(gy - targetY)
                    if (isWalkableTile(map[gy][gx]) && distance in 2..5 &&
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
        val phase = (System.currentTimeMillis() / 170L + x * 3L + y * 5L).toInt()
        p.color = 0xFF103C61.toInt()
        c.drawRect(l, t, l + cell, t + cell, p)
        p.color = 0xFF17618A.toInt()
        c.drawRect(l + cell * .04f, t + cell * .08f, l + cell * .96f, t + cell * .92f, p)
        p.color = 0xFF2C83A9.toInt()
        c.drawRect(l + cell * .08f, t + cell * .16f, l + cell * .92f, t + cell * .22f, p)
        p.color = 0xFF0D4B70.toInt()
        c.drawRect(l + cell * .08f, t + cell * .78f, l + cell * .92f, t + cell * .9f, p)
        p.color = 0xFF8BD5E5.toInt()
        p.strokeWidth = maxOf(1f, cell * .045f)
        val shift = (phase % 5) * cell * .035f
        c.drawLine(l + cell * .12f + shift, t + cell * .39f, l + cell * .48f + shift, t + cell * .39f, p)
        c.drawLine(l + cell * .52f - shift, t + cell * .65f, l + cell * .86f - shift, t + cell * .65f, p)
        p.color = 0x668CE1F0
        c.drawCircle(l + cell * (.22f + (phase % 3) * .18f), t + cell * .52f, cell * .035f, p)
    }    private fun drawIce(c: Canvas, x: Int, y: Int) {
        val l = ox + x * cell
        val t = oy + y * cell
        p.color = 0xFF6C9EAD.toInt()
        c.drawRect(l, t, l + cell, t + cell, p)
        p.color = 0xFFB5DDE2.toInt()
        c.drawRect(l + cell * .04f, t + cell * .04f, l + cell * .96f, t + cell * .96f, p)
        p.color = 0xFFDDF7F5.toInt()
        c.drawRect(l + cell * .08f, t + cell * .1f, l + cell * .72f, t + cell * .16f, p)
        p.color = 0xFF8CBCC8.toInt()
        p.strokeWidth = maxOf(1f, cell * .035f)
        val variant = (x * 13 + y * 7) % 4
        c.drawLine(l + cell * .18f, t + cell * .28f, l + cell * (.42f + variant * .04f), t + cell * .5f, p)
        c.drawLine(l + cell * (.42f + variant * .04f), t + cell * .5f, l + cell * .34f, t + cell * .78f, p)
        c.drawLine(l + cell * .42f, t + cell * .5f, l + cell * .72f, t + cell * .42f, p)
        p.color = 0x99FFFFFF.toInt()
        c.drawCircle(l + cell * .78f, t + cell * .22f, cell * .055f, p)
    }    private fun drawForestCanopies(c: Canvas) {
        for (y in 0 until rows) for (x in 0 until cols) {
            if (map[y][x] != 4) continue
            val cx = ox + (x + .5f) * cell
            val cy = oy + (y + .5f) * cell
            // Dark trunk and layered canopy make forest tiles read as dense cover.
            p.color = 0xFF493B29.toInt()
            c.drawRect(cx - cell * .07f, cy + cell * .02f, cx + cell * .07f, cy + cell * .39f, p)
            p.color = 0xFF102F27.toInt()
            c.drawCircle(cx, cy + cell * .04f, cell * .46f, p)
            p.color = 0xFF18543A.toInt()
            c.drawCircle(cx - cell * .17f, cy - cell * .06f, cell * .29f, p)
            c.drawCircle(cx + cell * .17f, cy - cell * .04f, cell * .29f, p)
            c.drawCircle(cx, cy - cell * .22f, cell * .28f, p)
            p.color = 0xFF28774B.toInt()
            c.drawCircle(cx - cell * .15f, cy - cell * .17f, cell * .17f, p)
            c.drawCircle(cx + cell * .12f, cy - cell * .10f, cell * .16f, p)
            p.color = 0xFF72A96A.toInt()
            c.drawCircle(cx - cell * .19f, cy - cell * .22f, cell * .055f, p)
            c.drawCircle(cx + cell * .08f, cy - cell * .29f, cell * .045f, p)
            p.color = 0xFF0B281F.toInt()
            c.drawArc(cx - cell * .38f, cy - cell * .34f, cx + cell * .38f, cy + cell * .38f, 205f, 125f, false, p)
        }
    }    private fun drawBrick(c: Canvas, x: Int, y: Int) {
        val l = ox + x * cell
        val t = oy + y * cell
        val variant = (x * 17 + y * 31) % 5
        p.color = 0xFF4A2925.toInt()
        c.drawRect(l + 1, t + 2, l + cell - 1, t + cell - 1, p)
        p.color = when (variant) { 0 -> 0xFFB85C3D.toInt(); 1 -> 0xFFC9754B.toInt(); 2 -> 0xFFD18456.toInt(); else -> brick }
        c.drawRect(l + 2, t + 2, l + cell - 2, t + cell * .46f, p)
        p.color = when (variant) { 1 -> 0xFFB96543.toInt(); 3 -> 0xFFD58A5C.toInt(); else -> 0xFFB65C40.toInt() }
        c.drawRect(l + 2, t + cell * .54f, l + cell - 2, t + cell - 2, p)
        p.color = 0xFF713B30.toInt()
        p.strokeWidth = maxOf(1f, cell * .055f)
        c.drawLine(l + cell * .5f, t + 2, l + cell * .5f, t + cell * .45f, p)
        c.drawLine(l + cell * .12f, t + cell * .5f, l + cell * .88f, t + cell * .5f, p)
        c.drawLine(l + cell * .28f, t + cell * .55f, l + cell * .28f, t + cell - 2, p)
        p.color = 0xFFFFB27C.toInt()
        p.strokeWidth = maxOf(1f, cell * .035f)
        c.drawLine(l + 3, t + 3, l + cell * .44f, t + 3, p)
        c.drawLine(l + 3, t + 3, l + 3, t + cell * .4f, p)
        // Small deterministic chips and hairline cracks.
        if (variant == 2 || variant == 4) {
            p.color = 0xFF5C3029.toInt()
            c.drawLine(l + cell * .68f, t + cell * .13f, l + cell * .59f, t + cell * .27f, p)
            c.drawLine(l + cell * .59f, t + cell * .27f, l + cell * .7f, t + cell * .34f, p)
        }
        p.color = 0x66F3C19A
        c.drawRect(l + cell * .08f, t + cell * .08f, l + cell * .13f, t + cell * .12f, p)
    }    private fun drawSteel(c: Canvas, x: Int, y: Int) {
        val l = ox + x * cell
        val t = oy + y * cell
        p.color = 0xFF182C3A.toInt()
        c.drawRect(l + 1, t + 2, l + cell - 1, t + cell - 1, p)
        p.color = 0xFF526F80.toInt()
        c.drawRect(l + 2, t + 2, l + cell - 2, t + cell - 2, p)
        p.color = 0xFF9FB6C2.toInt()
        c.drawRect(l + cell * .12f, t + cell * .1f, l + cell * .88f, t + cell * .24f, p)
        p.color = 0xFF718D9B.toInt()
        c.drawRect(l + cell * .12f, t + cell * .29f, l + cell * .88f, t + cell * .86f, p)
        p.color = 0xFF405C6B.toInt()
        c.drawRect(l + cell * .2f, t + cell * .4f, l + cell * .8f, t + cell * .72f, p)
        p.color = 0xFFD7E7EC.toInt()
        val r = cell * .055f
        c.drawCircle(l + cell * .2f, t + cell * .2f, r, p)
        c.drawCircle(l + cell * .8f, t + cell * .2f, r, p)
        c.drawCircle(l + cell * .2f, t + cell * .8f, r, p)
        c.drawCircle(l + cell * .8f, t + cell * .8f, r, p)
        p.color = 0xFF526D7B.toInt()
        c.drawCircle(l + cell * .2f, t + cell * .2f, r * .35f, p)
        c.drawCircle(l + cell * .8f, t + cell * .2f, r * .35f, p)
        c.drawCircle(l + cell * .2f, t + cell * .8f, r * .35f, p)
        c.drawCircle(l + cell * .8f, t + cell * .8f, r * .35f, p)
        p.color = 0xFFDBE8EC.toInt()
        p.strokeWidth = maxOf(1f, cell * .035f)
        c.drawLine(l + cell * .28f, t + cell * .48f, l + cell * .72f, t + cell * .48f, p)
    }    // Pixel-art tank sprites are cached as tiny 24x24 bitmaps and scaled without filtering.
    // This keeps edges crisp while giving every tank class its own readable silhouette.
    private val tankSprites = HashMap<Int, android.graphics.Bitmap>()

    private fun tankSprite(player: Boolean, type: Int): android.graphics.Bitmap {
        val key = (if (player) 100 else 0) + type
        return tankSprites.getOrPut(key) {
            val bmp = android.graphics.Bitmap.createBitmap(24, 24, android.graphics.Bitmap.Config.ARGB_8888)
            val sc = Canvas(bmp)
            val q = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
            val body = if (player) 0xFF48B875.toInt() else when (type) {
                1 -> 0xFF6889B5.toInt()
                2 -> 0xFFD79A3D.toInt()
                3 -> 0xFF9A68C2.toInt()
                else -> 0xFFD45449.toInt()
            }
            val light = if (player) 0xFFB8F2B8.toInt() else when (type) {
                1 -> 0xFFC8E0FF.toInt()
                2 -> 0xFFFFD98B.toInt()
                3 -> 0xFFE9C7FF.toInt()
                else -> 0xFFFFB2A1.toInt()
            }
            val dark = 0xFF17262B.toInt()
            fun px(color: Int, x1: Int, y1: Int, x2: Int, y2: Int) {
                q.color = color
                sc.drawRect(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), q)
            }
            // Track bands and individual tread blocks.
            px(dark, 2, 3, 6, 21); px(dark, 18, 3, 22, 21)
            for (yy in 4..20 step 3) {
                px(0xFF879397.toInt(), 2, yy, 6, yy + 1)
                px(0xFF879397.toInt(), 18, yy, 22, yy + 1)
            }
            // Class-specific hull proportions: heavy is broad, scout is compact.
            val inset = if (!player && type == 2) 5 else if (!player && type == 0) 7 else 6
            px(0xFF0D171B.toInt(), inset - 1, 4, 24 - inset + 1, 20)
            px(if (player) 0xFF286C4C.toInt() else 0xFF663B39.toInt(), inset, 4, 24 - inset, 20)
            px(body, inset + 1, 5, 23 - inset, 19)
            px(light, inset + 2, 5, 15, 7)
            px(if (player) 0xFF2F8054.toInt() else 0xFF8B5145.toInt(), 8, 16, 16, 18)
            // Rear engine grille and side armour.
            px(0xFF25383A.toInt(), 9, 15, 15, 17)
            px(0xFFB8C5B4.toInt(), 10, 15, 11, 16)
            px(0xFFB8C5B4.toInt(), 13, 15, 14, 16)
            // Turret ring, turret block, hatch and barrel.
            px(0xFF101D22.toInt(), 8, 8, 16, 16)
            px(if (player) 0xFF75D98E.toInt() else when (type) {
                1 -> 0xFFA9C9ED.toInt(); 2 -> 0xFFEBC06B.toInt()
                3 -> 0xFFD0A5F0.toInt(); else -> 0xFFEC806C.toInt()
            }, 9, 9, 15, 15)
            px(0xFF263B3D.toInt(), 10, 10, 14, 14)
            px(light, 10, 9, 12, 10)
            px(0xFF18262A.toInt(), 11, 1, 13, 10)
            px(if (player) 0xFFDCE9D0.toInt() else 0xFFE5D8CF.toInt(), 11, 2, 12, 8)
            px(0xFF27383B.toInt(), 10, 1, 14, 3)
            px(0xFFFFD27B.toInt(), 11, 1, 13, 2)
            // Distinct class insignia.
            if (!player) when (type) {
                1 -> { px(0xFFDCEBFF.toInt(), 8, 6, 10, 7); px(0xFFDCEBFF.toInt(), 14, 6, 16, 7) }
                2 -> { px(0xFFFFE6A5.toInt(), 10, 18, 14, 19) }
                3 -> { px(0xFFE9CEFF.toInt(), 7, 10, 8, 14); px(0xFFE9CEFF.toInt(), 16, 10, 17, 14) }
                else -> px(0xFFFFD5C8.toInt(), 10, 18, 14, 19)
            }
            bmp
        }
    }

    private fun tank(c: Canvas, x: Float, y: Float, d: Int, player: Boolean, type: Int = 0) {
        val cx = ox + (x + .5f) * cell
        val cy = oy + (y + .5f) * cell
        val tankSize = if (!player && type == 4) 1.48f else 1f
        val save = c.save()
        c.rotate(d * 90f, cx, cy)
        // Soft ground shadow separates the sprite from detailed terrain.
        p.color = 0x77071318
        c.drawOval(cx - cell * .43f, cy + cell * .25f, cx + cell * .43f, cy + cell * .48f, p)
        val sprite = tankSprite(player, type)
        val halfSize = cell * .5f * tankSize
        val dst = RectF(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize)
        val spritePaint = Paint().apply {
            isFilterBitmap = false
            isAntiAlias = false
        }
        c.drawBitmap(sprite, null, dst, spritePaint)
        // Small specular glint changes subtly with the frame for a living arcade look.
        if (player && System.currentTimeMillis() % 700L < 350L) {
            p.color = 0x66FFFFFF
            c.drawRect(cx - cell * .20f, cy - cell * .22f, cx - cell * .08f, cy - cell * .17f, p)
        }
        c.restoreToCount(save)
    }

    private fun drawBossHealth(c: Canvas, x: Float, y: Float, hp: Int, maxHp: Int) {
        val cx = ox + (x + .5f) * cell
        val top = oy + (y + .5f) * cell - cell * .88f
        val barWidth = cell * 1.45f
        val left = cx - barWidth / 2f
        p.color = 0xEE101820.toInt()
        c.drawRoundRect(left, top, left + barWidth, top + cell * .13f, cell * .05f, cell * .05f, p)
        p.color = 0xFFFF665C.toInt()
        c.drawRoundRect(left, top, left + barWidth * (hp.toFloat() / maxHp).coerceIn(0f, 1f), top + cell * .13f, cell * .05f, cell * .05f, p)
        p.color = 0xFFFFD36A.toInt()
        p.textAlign = Paint.Align.CENTER
        p.textSize = cell * .22f
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        c.drawText("BOSS", cx, top - cell * .08f, p)
        p.textAlign = Paint.Align.LEFT
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
        c.drawText("LV $level   SCORE $score", left + cell * .22f, bottom - cell * .22f, p)
        p.textAlign = Paint.Align.CENTER
        p.color = when {
            bossActive -> 0xFFFF7568.toInt()
            System.currentTimeMillis() < shieldUntil -> 0xFF67D8FF.toInt()
            System.currentTimeMillis() < rapidUntil -> 0xFFFFD34F.toInt()
            System.currentTimeMillis() < weaponUntil -> 0xFFB88BFF.toInt()
            else -> 0xFFB8C8CC.toInt()
        }
        c.drawText(when {
            bossActive -> "BOSS"
            System.currentTimeMillis() < shieldUntil -> "SHIELD"
            System.currentTimeMillis() < rapidUntil -> "RAPID"
            System.currentTimeMillis() < weaponUntil -> "HEAVY"
            else -> "WAVE"
        }, width / 2f, bottom - cell * .22f, p)
        p.textAlign = Paint.Align.RIGHT
        p.color = 0xFFFFD36A.toInt()
        c.drawText("LIVES  $lives", right - cell * .22f, bottom - cell * .22f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawControls(c: Canvas) {
        val s = cell * 1.5f
        val cx = s * .8f
        val cy = height - s * 1.25f
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
                val dx = e.x - (s * .8f)
                val dy = e.y - (height - s * 1.25f)
                move = if (abs(dx) > abs(dy)) { if (dx > 0) 1 else 3 } else { if (dy > 0) 2 else 0 }
            } else if (!fire) {
                move = -1
            }
            invalidate()
        }
        return true
    }
}
