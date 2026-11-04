package com.example.mangainker.gesture
import android.graphics.PointF
import com.example.mangainker.core.AppState
import com.example.mangainker.core.PlayerCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
class PathPlayer{
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private var playback:Job?=null
 private var points=emptyList<PointF>()
 private var cursor=0
 init{scope.launch{AppState.command.collect{cmd->when(cmd){is PlayerCommand.Start->start();is PlayerCommand.Pause->pause();is PlayerCommand.Resume->resume();is PlayerCommand.Stop->stop();PlayerCommand.Idle->Unit}}}}
 private fun start(){if(playback?.isActive==true)return;val service=GestureAccessibilityService.instance;if(service==null){AppState.status("Accessibility is not enabled.");return};if(!service.isIbisPaintForeground){AppState.status("Open ibisPaint X and tap Start in the floating panel.");return};points=makeCurve();cursor=0;AppState.running(true);AppState.paused(false);AppState.status("RUNNING • Test S-curve");play()}
 private fun pause(){if(!AppState.running.value)return;AppState.paused(true);AppState.status("PAUSED • Current gesture may finish within 500 ms");playback?.cancel();playback=null}
 private fun resume(){if(!AppState.running.value||!AppState.paused.value)return;AppState.paused(false);AppState.status("RUNNING • Resuming");play()}
 private fun stop(){playback?.cancel();playback=null;cursor=points.size;AppState.running(false);AppState.paused(false);AppState.status("STOPPED • No further segments will be sent")}
 private fun play(){if(playback?.isActive==true)return;playback=scope.launch{try{val service=GestureAccessibilityService.instance?:error("Accessibility disconnected");while(cursor<points.lastIndex&&AppState.running.value&&!AppState.paused.value){val end=minOf(cursor+20,points.lastIndex);val ok=service.drawPath(points.subList(cursor,end+1),466L);if(!ok){AppState.running(false);AppState.paused(false);AppState.status("GESTURE CANCELLED OR REJECTED");return@launch};cursor=end;if(cursor<points.lastIndex)delay(50L)};if(cursor>=points.lastIndex&&AppState.running.value){AppState.running(false);AppState.paused(false);AppState.status("Dispatch completed. Check ibisPaint to confirm the mark appeared.")}}catch(e:CancellationException){throw e}catch(e:Exception){AppState.running(false);AppState.paused(false);AppState.status("ERROR • ${e.message?:"Unknown gesture error"}")}}}
 private fun makeCurve():List<PointF>{val m=GestureAccessibilityService.instance?.resources?.displayMetrics?:error("Accessibility unavailable");val cx=m.widthPixels/2f;val cy=m.heightPixels/2f;val halfW=minOf(m.widthPixels*.22f,150f);return(0..60).map{i->val t=i/60f;PointF(cx+sin(2.0*PI*t-PI/2.0).toFloat()*halfW,cy-200f+400f*t)}}
}
