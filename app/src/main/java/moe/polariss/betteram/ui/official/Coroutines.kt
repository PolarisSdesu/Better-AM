package moe.polariss.betteram.ui.official

internal suspend fun awaitFrame() { androidx.compose.runtime.withFrameNanos { } }
