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
    private val cols = 22
    private val rows = 14
    private var cell = 24f
    private var ox = 0f
    private var oy = 0f
    private val map = Array(rows) { IntArray(cols) }
    private var tx = 3.5f
    private var ty = 11.5f
    private var dir = 0
    private val enemies = mutableListOf<Pair<Float, Float>>()
    private val enemyDirs = mutableListOf<Int>()
    private val bullets = mutableListOf<FloatArray>()
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

    private val brick = 0xFFB85F3D.toInt()
    private val steel = 0xFF778895.toInt()
    private val green = 0xFF4EAD78.toInt()

    private fun reset() {
        for (y in 0 until rows) for (x in 0 until cols) {
            map[y][x] = when {
                x == 0 || y == 0 || x == cols - 1 || y == rows - 1 -> 2
                Random.nextFloat() < .105f -> 1
                else -> 0
            }
        }
        // Keep the player's starting area clear.
        for (y in 9..12) for (x in 1..6) map[y][x] = 0
        tx = 3.5f
        ty = 11.5f
        dir = 0
        enemies.clear()
        enemyDirs.clear()
        bullets.clear()
        score = 0
        lives = 3
        over = false
        started = true
        val now = System.currentTimeMillis()
        lastFrame = now
        lastShot = 0L
        lastSpawn = now
        lastEnemyShot = now
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawColor(0xFF101B20.toInt())
        cell = min(height / (rows + 1.25f), width * .72f / cols)
        ox = (width - cols * cell) / 2f
        oy = (height - rows * cell) / 2f

        if (started && !over) updateGame()
        drawField(c)
        for (y in 0 until rows) for (x in 0 until cols) {
            when (map[y][x]) {
                1 -> drawBrick(c, x, y)
                2 -> drawSteel(c, x, y)
            }
        }
        tank(c, tx, ty, dir, true)
        enemies.forEachIndexed { i, pos -> tank(c, pos.first, pos.second, enemyDirs.getOrElse(i) { 2 }, false) }
        bullets.forEach { b ->
            val bx = ox + (b[0] + .5f) * cell
            val by = oy + (b[1] + .5f) * cell
            p.color = 0xFFFFE08A.toInt()
            c.drawCircle(bx, by, cell * .17f, p)
            p.color = 0xFFFFA52E.toInt()
            c.drawCircle(bx, by, cell * .085f, p)
        }
        drawHud(c)
        drawControls(c)

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

        if (move >= 0) {
            dir = move
            val speed = .075f * dt
            val nx = tx + when (dir) { 1 -> speed; 3 -> -speed; else -> 0f }
            val ny = ty + when (dir) { 2 -> speed; 0 -> -speed; else -> 0f }
            if (canTankMove(nx, ny)) { tx = nx; ty = ny }
        }

        if (fire && now - lastShot >= 260L) {
            shoot(tx, ty, dir, true)
            lastShot = now
        }

        if (enemies.size < 4 && now - lastSpawn >= 1450L) {
            val spawnX = listOf(2.5f, 10.5f, 18.5f).filter { canTankMove(it, 1.5f) }.randomOrNull()
            if (spawnX != null) {
                enemies.add(spawnX to 1.5f)
                enemyDirs.add(2)
            }
            lastSpawn = now
        }

        if (now - lastEnemyShot > 1700L && enemies.isNotEmpty()) {
            val i = Random.nextInt(enemies.size)
            shoot(enemies[i].first, enemies[i].second, enemyDirs[i], false)
            lastEnemyShot = now
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
            if (map[y][x] != 0) {
                if (map[y][x] == 1) map[y][x] = 0
                bulletIterator.remove()
                continue
            }
            val hit = enemies.indexOfFirst { abs(it.first - b[0]) < .52f && abs(it.second - b[1]) < .52f }
            if (hit >= 0 && b[4] == 1f) {
                enemies.removeAt(hit)
                enemyDirs.removeAt(hit)
                score += 100
                bulletIterator.remove()
                continue
            }
            if (b[4] == 0f && abs(tx - b[0]) < .42f && abs(ty - b[1]) < .42f) {
                bulletIterator.remove()
                lives--
                tx = 3.5f
                ty = 11.5f
                if (lives <= 0) over = true
            }
        }

        for (i in enemies.indices) {
            val (ex, ey) = enemies[i]
            val ny = ey + .012f * dt
            enemyDirs[i] = 2
            if (canTankMove(ex, ny)) enemies[i] = ex to ny
            else if (ey > 1.6f) enemies[i] = ex to ey
            if (abs(ex - tx) < .58f && abs(ey - ty) < .58f) {
                lives--
                tx = 3.5f
                ty = 11.5f
                enemies[i] = ex to 1.5f
                if (lives <= 0) over = true
            }
        }
    }

    private fun shoot(x: Float, y: Float, direction: Int, player: Boolean) {
        val dx = when (direction) { 1 -> 1f; 3 -> -1f; else -> 0f }
        val dy = when (direction) { 2 -> 1f; 0 -> -1f; else -> 0f }
        bullets.add(floatArrayOf(x + dx * .48f, y + dy * .48f, dx, dy, if (player) 1f else 0f))
    }

    // Test all four corners of the tank footprint, not only its center cell.
    private fun canTankMove(x: Float, y: Float): Boolean {
        val r = .31f
        val points = arrayOf(
            (x - r) to (y - r), (x + r) to (y - r),
            (x - r) to (y + r), (x + r) to (y + r)
        )
        return points.all { (px, py) ->
            val gx = px.toInt()
            val gy = py.toInt()
            gx in 1 until cols - 1 && gy in 1 until rows - 1 && map[gy][gx] == 0
        }
    }

    private fun drawField(c: Canvas) {
        p.color = 0xFF263D35.toInt()
        c.drawRect(ox, oy, ox + cols * cell, oy + rows * cell, p)
        p.color = 0xFF30483C.toInt()
        for (y in 0 until rows) for (x in 0 until cols) {
            if ((x * 7 + y * 11) % 5 == 0) {
                val left = ox + x * cell
                val top = oy + y * cell
                c.drawLine(left + cell * .22f, top + cell * .72f, left + cell * .35f, top + cell * .58f, p)
                c.drawLine(left + cell * .65f, top + cell * .28f, left + cell * .78f, top + cell * .42f, p)
            }
        }
    }

    private fun drawBrick(c: Canvas, x: Int, y: Int) {
        val l = ox + x * cell
        val t = oy + y * cell
        p.color = 0xFF603A2D.toInt()
        c.drawRect(l + 1, t + 1, l + cell - 1, t + cell - 1, p)
        p.color = brick
        c.drawRect(l + 2, t + 2, l + cell - 2, t + cell * .46f, p)
        c.drawRect(l + 2, t + cell * .54f, l + cell - 2, t + cell - 2, p)
        p.color = 0xFFDA8B5D.toInt()
        c.drawLine(l + 3, t + 3, l + cell - 3, t + 3, p)
        c.drawLine(l + cell * .5f, t + 3, l + cell * .5f, t + cell * .44f, p)
        c.drawLine(l + cell * .25f, t + cell * .56f, l + cell * .25f, t + cell - 3, p)
    }

    private fun drawSteel(c: Canvas, x: Int, y: Int) {
        val l = ox + x * cell
        val t = oy + y * cell
        p.color = 0xFF35434B.toInt()
        c.drawRect(l + 1, t + 1, l + cell - 1, t + cell - 1, p)
        p.color = steel
        c.drawRect(l + 3, t + 3, l + cell - 3, t + cell - 3, p)
        p.color = 0xFFB8C4C8.toInt()
        c.drawLine(l + 4, t + 4, l + cell - 4, t + 4, p)
        c.drawCircle(l + cell * .2f, t + cell * .2f, cell * .045f, p)
        c.drawCircle(l + cell * .8f, t + cell * .8f, cell * .045f, p)
    }

    private fun tank(c: Canvas, x: Float, y: Float, d: Int, player: Boolean) {
        val cx = ox + (x + .5f) * cell
        val cy = oy + (y + .5f) * cell
        val body = if (player) green else 0xFFD95B50.toInt()
        // Tracks
        p.color = 0xFF172126.toInt()
        c.drawRoundRect(cx - cell * .43f, cy - cell * .39f, cx + cell * .43f, cy + cell * .39f, cell * .12f, cell * .12f, p)
        p.color = 0xFF78847D.toInt()
        for (i in -2..2) {
            val yy = cy + i * cell * .14f
            c.drawLine(cx - cell * .39f, yy, cx - cell * .32f, yy, p)
            c.drawLine(cx + cell * .32f, yy, cx + cell * .39f, yy, p)
        }
        // Armoured hull
        p.color = body
        c.drawRoundRect(cx - cell * .31f, cy - cell * .31f, cx + cell * .31f, cy + cell * .31f, cell * .09f, cell * .09f, p)
        p.color = if (player) 0xFF9BE3A9.toInt() else 0xFFFF9A83.toInt()
        c.drawLine(cx - cell * .22f, cy - cell * .22f, cx + cell * .18f, cy - cell * .22f, p)
        // Turret and barrel
        p.color = 0xFF25332F.toInt()
        c.drawCircle(cx, cy, cell * .19f, p)
        p.color = 0xFFE0D8B8.toInt()
        val ex = cx + when (d) { 1 -> cell * .49f; 3 -> -cell * .49f; else -> 0f }
        val ey = cy + when (d) { 2 -> cell * .49f; 0 -> -cell * .49f; else -> 0f }
        p.strokeWidth = cell * .12f
        p.strokeCap = Paint.Cap.SQUARE
        c.drawLine(cx, cy, ex, ey, p)
        p.strokeCap = Paint.Cap.BUTT
        p.color = 0xFF10191B.toInt()
        c.drawCircle(cx, cy, cell * .065f, p)
    }

    private fun drawHud(c: Canvas) {
        p.color = 0xFF0C151B.toInt()
        c.drawRoundRect(ox, oy - cell * .78f, ox + cols * cell, oy - cell * .12f, cell * .12f, cell * .12f, p)
        p.color = 0xFFEAF0F2.toInt()
        p.textSize = cell * .48f
        p.textAlign = Paint.Align.LEFT
        c.drawText("SCORE  $score", ox + cell * .3f, oy - cell * .32f, p)
        p.textAlign = Paint.Align.RIGHT
        p.color = 0xFFFFD36A.toInt()
        c.drawText("LIVES  $lives", ox + cols * cell - cell * .3f, oy - cell * .32f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawControls(c: Canvas) {
        val s = cell * 1.5f
        val cx = ox + s * .8f
        val cy = height - s * 1.25f
        val labels = listOf("▲", "▶", "▼", "◀")
        for (d in 0..3) {
            val x = cx + when (d) { 1 -> s; 3 -> -s; else -> 0f }
            val y = cy + when (d) { 2 -> s; 0 -> -s; else -> 0f }
            p.color = 0xAA253A43.toInt()
            c.drawCircle(x, y, s * .38f, p)
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
        p.color = 0xCC8F3540.toInt()
        c.drawCircle(fx, fy, s * .56f, p)
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
                val dx = e.x - (ox + s * .8f)
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
