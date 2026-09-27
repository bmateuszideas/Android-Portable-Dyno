package com.roaddyno.app.domain.source

import com.roaddyno.app.domain.model.SpeedSample
import kotlinx.coroutines.flow.Flow

interface SpeedSource {
    val samples: Flow<SpeedSample>

    suspend fun start()

    suspend fun stop()
}
