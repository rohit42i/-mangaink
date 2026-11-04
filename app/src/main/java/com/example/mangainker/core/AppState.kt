package com.example.mangainker.core
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
sealed interface PlayerCommand {
 data object Idle : PlayerCommand
 data class Start(val id:Long):PlayerCommand
 data class Pause(val id:Long):PlayerCommand
 data class Resume(val id:Long):PlayerCommand
 data class Stop(val id:Long):PlayerCommand
}
object AppState {
 private val ids=AtomicLong()
 private val _command=MutableStateFlow<PlayerCommand>(PlayerCommand.Idle)
 val command:StateFlow<PlayerCommand> = _command.asStateFlow()
 private val _status=MutableStateFlow("Ready. Enable both permissions.")
 val status:StateFlow<String> = _status.asStateFlow()
 private val _running=MutableStateFlow(false)
 val running:StateFlow<Boolean> = _running.asStateFlow()
 private val _paused=MutableStateFlow(false)
 val paused:StateFlow<Boolean> = _paused.asStateFlow()
 fun start(){_command.value=PlayerCommand.Start(ids.incrementAndGet())}
 fun pause(){_command.value=PlayerCommand.Pause(ids.incrementAndGet())}
 fun resume(){_command.value=PlayerCommand.Resume(ids.incrementAndGet())}
 fun stop(){_command.value=PlayerCommand.Stop(ids.incrementAndGet())}
 fun status(v:String){_status.value=v}
 fun running(v:Boolean){_running.value=v}
 fun paused(v:Boolean){_paused.value=v}
}
