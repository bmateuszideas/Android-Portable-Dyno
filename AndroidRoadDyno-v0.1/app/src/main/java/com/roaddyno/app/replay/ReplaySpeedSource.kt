package com.roaddyno.app.replay

import com.roaddyno.app.domain.model.SpeedSample
import com.roaddyno.app.domain.source.SpeedSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class ReplayMode { REALTIME, MAX_SPEED }

class ReplaySpeedSource(
    private val recorded: List<SpeedSample>,
    private val mode: ReplayMode,
) : SpeedSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val channel = Channel<SpeedSample>(Channel.UNLIMITED)
    private var playback: Job? = null
    override val samples: Flow<SpeedSample> = channel.receiveAsFlow()

    override suspend fun start() {
        check(playback == null) { "Replay is already running or has finished." }
        playback = scope.launch {
            try {
                val firstTimestampNs = recorded.firstOrNull()?.timestampNs ?: 0L
                val startedNs = System.nanoTime()
                for (sample in recorded) {
                    if (mode == ReplayMode.REALTIME) {
                        val targetElapsedNs = (sample.timestampNs - firstTimestampNs).coerceAtLeast(0L)
                        val remainingNs = targetElapsedNs - (System.nanoTime() - startedNs)
                        if (remainingNs > 0) delay((remainingNs + 999_999L) / 1_000_000L)
                    }
                    channel.send(sample)
                }
            } finally {
                channel.close()
            }
        }
    }

    override suspend fun stop() {
        playback?.cancel()
        channel.close()
        scope.cancel()
    }
}
