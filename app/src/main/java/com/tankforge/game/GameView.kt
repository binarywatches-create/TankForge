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
    // Power-up and enemy-class systems are being added in the next gameplay pass.
    private var tx = 3.5f
    private var ty = 11.5f
    private var dir = 0
    private val enemies = mutableListOf<Pair<Float, Float>>()
    private val enemyDirs = mutableListOf<Int>()
    private val bullets = mutableListOf<FloatArray>()
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
                else -> 0
            }
        }
        // Keep the player's starting area clear.
        for (y in 9..12) for (x in 1..6) map[y][x] = 0
        tx = 3f
        ty = 11f
        dir = 0
        enemies.clear()
        enemyDirs.clear()
        bullets.clear()
        bonuses.clear()
        shieldUntil = 0L
        rapidUntil = 0L
        weaponUntil = 0L
        lastBonusSpawn = now
        score = 0
        lives = 3
        over = false
        started = true
        val now = System.currentTimeMillis()
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
        if (System.currentTimeMillis() > playerHitUntil || (System.currentTimeMillis() / 100) % 2L == 0L) tank(c, tx, ty, dir, true)
        bonuses.forEach { drawBonus(c, it) }
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
        if (System.currentTimeMillis() < bonusMessageUntil) {
            p.color = 0xFFFFE28A.toInt()
            p.textAlign = Paint.Align.CENTER
            p.textSize = cell * .62f
            c.drawText(bonusMessage, width / 2f, oy + rows * cell + cell * .72f, p)
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

        if (move >= 0) {
            dir = move
            val speed = .075f * dt
            val nx = tx + when (dir) { 1 -> speed; 3 -> -speed; else -> 0f }
            val ny = ty + when (dir) { 2 -> speed; 0 -> -speed; else -> 0f }
            if (canTankMove(nx, ny) && !touchesEnemy(nx, ny)) { tx = nx; ty = ny }
        }

        if (fire && now - lastShot >= 260L) {
            shoot(tx, ty, dir, true)
            lastShot = now
        }

        if (enemies.size < 4 && now - lastSpawn >= 1450L) {
            val spawnX = listOf(2f, 10f, 18f).filter { canTankMove(it, 1f) && !touchesEnemy(it, 1f) }.randomOrNull()
            if (spawnX != null) {
                enemies.add(spawnX to 1f)
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
            if (b[4] == 0f && abs(tx - b[0]) < .42f && abs(ty - b[1]) < .42f && now >= playerHitUntil) {
                bulletIterator.remove()
                lives--
                playerHitUntil = now + 1400L
                damageFlashUntil = now + 260L
                tx = 3f
                ty = 11f
                if (lives <= 0) over = true
            }
        }

        for (i in enemies.indices) {
            val (ex, ey) = enemies[i]
            val ny = ey + .012f * dt
            enemyDirs[i] = 2
            if (canTankMove(ex, ny) && !touchesPlayer(ex, ny)) enemies[i] = ex to ny
            else if (ey > 1.1f) enemies[i] = ex to ey
        }
    }

    private fun shoot(x: Float, y: Float, direction: Int, player: Boolean) {
        val dx = when (direction) { 1 -> 1f; 3 -> -1f; else -> 0f }
        val dy = when (direction) { 2 -> 1f; 0 -> -1f; else -> 0f }
        bullets.add(floatArrayOf(x + dx * .48f, y + dy * .48f, dx, dy, if (player) 1f else 0f))
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
            if (gx !in 0 until cols || gy !in 0 until rows || map[gy][gx] != 0) return false
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

    private fun tank(c: Canvas, x: Float, y: Float, d: Int, player: Boolean) {
        val cx = ox + (x + .5f) * cell
        val cy = oy + (y + .5f) * cell
        val hull = if (player) 0xFF4FAD72.toInt() else 0xFFCF514A.toInt()
        val highlight = if (player) 0xFFB5F1B9.toInt() else 0xFFFFB0A0.toInt()
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
        c.drawCircle(cx, cy - cell * .18f, cell * .075f, p)
        p.color = 0xFFFFD27B.toInt()
        c.drawCircle(cx, cy - cell * .18f, cell * .025f, p)

        // Small side insignia.
        p.color = if (player) 0xFFFFD66F.toInt() else 0xFFFFD6C9.toInt()
        c.drawCircle(cx, cy + cell * .255f, cell * .045f, p)
        c.restoreToCount(save)
    }

    private fun drawHud(c: Canvas) {
        p.color = 0xFF101E28.toInt()
        c.drawRoundRect(ox - cell * .02f, oy - cell * .8f, ox + cols * cell + cell * .02f, oy - cell * .08f, cell * .16f, cell * .16f, p)
        p.color = 0xFF4B6976.toInt()
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, cell * .035f)
        c.drawRoundRect(ox, oy - cell * .78f, ox + cols * cell, oy - cell * .1f, cell * .13f, cell * .13f, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFEAF0F2.toInt()
        p.textSize = cell * .48f
        p.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
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
