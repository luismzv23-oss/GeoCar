package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.data.model.MotionState

@Composable
fun TelemetryPulseOrb(
  isTracking: Boolean,
  motionState: MotionState,
  modifier: Modifier = Modifier,
) {
  val activeColor =
    when {
      !isTracking -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
      motionState == MotionState.STATIONARY -> MaterialTheme.colorScheme.secondary
      else -> MaterialTheme.colorScheme.primary
    }

  val transition = rememberInfiniteTransition(label = "telemetry_pulse")
  val pulseProgress by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 2200, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "pulse_ring",
    )

  Box(modifier = modifier.size(64.dp), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.size(64.dp)) {
      val centerRadius = size.minDimension * 0.28f
      if (isTracking) {
        val expandingRadius = centerRadius + (size.minDimension * 0.22f * pulseProgress)
        drawCircle(
          color = activeColor.copy(alpha = (1f - pulseProgress) * 0.55f),
          radius = expandingRadius,
          style = Stroke(width = 3.dp.toPx()),
        )
      }
      drawCircle(
        color = activeColor.copy(alpha = 0.16f),
        radius = size.minDimension * 0.36f,
      )
    }
    Icon(
      imageVector =
        when {
          !isTracking -> Icons.Default.GpsOff
          motionState == MotionState.STATIONARY -> Icons.Default.PauseCircle
          else -> Icons.Default.GpsFixed
        },
      contentDescription = null,
      tint = activeColor,
      modifier = Modifier.size(28.dp),
    )
  }
}
