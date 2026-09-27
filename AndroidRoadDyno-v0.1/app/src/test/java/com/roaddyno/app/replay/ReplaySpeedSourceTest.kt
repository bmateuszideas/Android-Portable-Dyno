package com.roaddyno.app.replay

import com.roaddyno.app.domain.model.SpeedSample
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplaySpeedSourceTest {
    @Test fun maxSpeedPreservesOrderTimestampsAndValues() = runBlocking {
        val original = listOf(0L to 10.0, 100_000_000L to 10.2, 200_000_000L to 10.4, 300_000_000L to 10.6)
            .map { (time, speed) -> SpeedSample(time, speed, 0.1, null, null, null, null, null) }
        val source = ReplaySpeedSource(original, ReplayMode.MAX_SPEED)
        try {
            source.start()
            assertEquals(original, source.samples.toList())
        } finally {
            source.stop()
        }
    }
}
