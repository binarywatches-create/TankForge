package com.tankforge.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

class GameView(context: Context) : View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val cols=22; private val rows=14
    private var cell=24f; private var ox=0f; private var oy=0f
    private val map=Array(rows){IntArray(cols)}
    private var tx=3.5f; private var ty=11.5f; private var dir=0
    private val enemies=mutableListOf<Pair<Float,Float>>()
    private val bullets=mutableListOf<FloatArray>()
    private var score=0; private var lives=3; private var started=false; private var over=false
    private var move=-1; private var fire=false; private var last=System.currentTimeMillis()

    private fun reset(){
        for(y in 0 until rows) for(x in 0 until cols) map[y][x]=when {
            x==0||y==0||x==cols-1||y==rows-1 -> 2
            Random.nextFloat()<.14f -> 1
            else -> 0
        }
        for(y in 9..12) for(x in 1..6) map[y][x]=0
        tx=3.5f; ty=11.5f; dir=0; enemies.clear(); bullets.clear()
        score=0; lives=3; over=false; started=true; last=System.currentTimeMillis()
    }

    override fun onDraw(c:Canvas){
        super.onDraw(c); c.drawColor(0xFF090E14.toInt())
        cell=min(height/(rows+1f),width*.72f/cols); ox=(width-cols*cell)/2; oy=(height-rows*cell)/2
        if(started&&!over) update()
        for(y in 0 until rows) for(x in 0 until cols) if(map[y][x]!=0){
            p.color=if(map[y][x]==1) 0xFFB96B3B.toInt() else 0xFF82909A.toInt()
            c.drawRect(ox+x*cell+1,oy+y*cell+1,ox+(x+1)*cell-1,oy+(y+1)*cell-1,p)
        }
        tank(c,tx,ty,dir,true); enemies.forEach{tank(c,it.first,it.second,2,false)}
        bullets.forEach{p.color=0xFFFFD36A.toInt();c.drawCircle(ox+(it[0]+.5f)*cell,oy+(it[1]+.5f)*cell,cell*.13f,p)}
        p.color=0xFFEAF0F5.toInt();p.textSize=cell*.55f;c.drawText("SCORE $score    LIVES $lives",ox,oy-cell*.25f,p)
        controls(c)
        if(!started||over){
            p.color=0xCC05090F.toInt();c.drawRect(0f,0f,width.toFloat(),height.toFloat(),p)
            p.color=0xFFFFC857.toInt();p.textAlign=Paint.Align.CENTER;p.textSize=cell*1.3f;c.drawText("TANKFORGE",width/2f,height*.4f,p)
            p.color=-1;p.textSize=cell*.58f;c.drawText(if(over)"MISSION FAILED" else "ARCADE TANK BATTLE",width/2f,height*.52f,p)
            c.drawText(if(over)"TAP TO RESTART" else "TAP TO START",width/2f,height*.65f,p)
        }
        if(started&&!over) postInvalidateDelayed(16)
    }

    private fun update(){
        val now=System.currentTimeMillis();val dt=((now-last).coerceIn(1,40))/16f;last=now
        if(move>=0){dir=move;val nx=tx+when(dir){1->.07f*dt;3->-.07f*dt;else->0f};val ny=ty+when(dir){2->.07f*dt;0->-.07f*dt;else->0f};if(free(nx,ny)){tx=nx;ty=ny}}
        if(fire&&now-last>180){val dx=if(dir==1)1f else if(dir==3)-1f else 0f;val dy=if(dir==2)1f else if(dir==0)-1f else 0f;bullets.add(floatArrayOf(tx+dx*.5f,ty+dy*.5f,dx,dy));last=now}
        if(enemies.size<4&&now-last>1200){enemies.add(Random.nextInt(2,cols-2)+.5f to 1.5f);last=now}
        val it=bullets.iterator()
        while(it.hasNext()){
            val b=it.next();b[0]+=b[2]*.2f;b[1]+=b[3]*.2f;val x=b[0].toInt();val y=b[1].toInt()
            if(x !in 0 until cols||y !in 0 until rows){it.remove();continue}
            if(map[y][x]!=0){if(map[y][x]==1)map[y][x]=0;it.remove();continue}
            val hit=enemies.indexOfFirst{abs(it.first-b[0])<.55f&&abs(it.second-b[1])<.55f}
            if(hit>=0){enemies.removeAt(hit);score+=100;it.remove()}
        }
        enemies.replaceAll{(x,y)->val ny=y+.01f*dt;if(abs(x-tx)<.6f&&abs(ny-ty)<.6f){lives--;tx=3.5f;ty=11.5f;if(lives<=0)over=true;x to 1.5f}else x to if(ny>rows-2)1.5f else ny}
        if(enemies.isEmpty()&&now-last>1800)enemies.add(10.5f to 1.5f)
    }

    private fun free(x:Float,y:Float):Boolean{val gx=x.toInt();val gy=y.toInt();return gx in 1 until cols-1&&gy in 1 until rows-1&&map[gy][gx]==0}
    private fun tank(c:Canvas,x:Float,y:Float,d:Int,player:Boolean){
        val cx=ox+(x+.5f)*cell;val cy=oy+(y+.5f)*cell
        p.color=if(player)0xFF55D6B0.toInt() else 0xFFE65B55.toInt()
        c.drawRoundRect(cx-cell*.36f,cy-cell*.36f,cx+cell*.36f,cy+cell*.36f,cell*.12f,cell*.12f,p)
        p.color=0xFF17212A.toInt();c.drawCircle(cx,cy,cell*.18f,p);p.strokeWidth=cell*.13f
        c.drawLine(cx,cy,cx+when(d){1->cell*.48f;3->-cell*.48f;else->0f},cy+when(d){2->cell*.48f;0->-cell*.48f;else->0f},p)
    }
    private fun controls(c:Canvas){
        val s=cell*1.5f;val cx=ox+s*.8f;val cy=height-s*1.25f;p.color=0x66536B80
        listOf(Triple(0,cx,cy-s),Triple(1,cx+s,cy),Triple(2,cx,cy+s),Triple(3,cx-s,cy)).forEach{(d,x,y)->
            c.drawCircle(x,y,s*.36f,p);p.color=-1;p.textAlign=Paint.Align.CENTER;p.textSize=s*.42f;c.drawText(listOf("▲","▶","▼","◀")[d],x,y+s*.14f,p);p.color=0x66536B80
        }
        val fx=width-s*1.3f;val fy=height-s*1.2f;p.color=0x668F3540;c.drawCircle(fx,fy,s*.55f,p);p.color=-1;p.textSize=s*.25f;c.drawText("FIRE",fx,fy+s*.1f,p)
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        if(e.action==MotionEvent.ACTION_DOWN&&(!started||over)){reset();invalidate();return true}
        if(e.action==MotionEvent.ACTION_UP||e.action==MotionEvent.ACTION_CANCEL){move=-1;fire=false;return true}
        val s=cell*1.5f;fire=e.x>width-s*2.2f&&e.y>height-s*2.2f
        if(!fire&&e.x<width*.48f){val dx=e.x-(ox+s*.8f);val dy=e.y-(height-s*1.25f);move=if(abs(dx)>abs(dy)){if(dx>0)1 else 3}else if(dy>0)2 else 0}
        invalidate();return true
    }
}
