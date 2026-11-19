package com.digitallife.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.digitallife.app.core.model.AgentState
import com.digitallife.app.ui.theme.BlueAccent
import com.digitallife.app.ui.theme.CyanAccent
import com.digitallife.app.ui.theme.ErrorRed
import com.digitallife.app.ui.theme.SuccessGreen

@Composable
fun AvatarView(
    state: AgentState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AgentState.IDLE -> 2200
                    AgentState.THINKING, AgentState.ANALYZING -> 900
                    AgentState.WORKING, AgentState.PLANNING, AgentState.LEARNING -> 700
                    else -> 1500
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val stateColor = when (state) {
        AgentState.IDLE -> CyanAccent.copy(alpha = 0.7f)
        AgentState.THINKING -> BlueAccent
        AgentState.LEARNING -> Color(0xFF7C4DFF)
        AgentState.ANALYZING -> CyanAccent
        AgentState.PLANNING -> Color(0xFF00B0FF)
        AgentState.WORKING -> Color(0xFFFFAB40)
        AgentState.COMPLETED -> SuccessGreen
        AgentState.ERROR -> ErrorRed
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(110.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 3.2f

            // Outer aura ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(stateColor.copy(alpha = 0.35f), Color.Transparent),
                    center = center,
                    radius = baseRadius * 1.6f * pulse
                ),
                radius = baseRadius * 1.6f * pulse,
                center = center
            )

            // Orbital ring
            drawCircle(
                color = stateColor.copy(alpha = 0.6f),
                radius = baseRadius * 1.25f,
                center = center,
                style = Stroke(width = 3f)
            )

            // Stylized virtual human head/core silhouette
            drawCircle(
                color = stateColor.copy(alpha = 0.2f),
                radius = baseRadius * 0.85f,
                center = center
            )
            drawCircle(
                color = stateColor,
                radius = baseRadius * 0.85f,
                center = center,
                style = Stroke(width = 4f)
            )

            // Eyes / neuralvisor
            val eyeY = center.y - baseRadius * 0.1f
            val eyeOffset = baseRadius * 0.28f
            drawCircle(color = stateColor, radius = 5f * pulse, center = Offset(center.x - eyeOffset, eyeY))
            drawCircle(color = stateColor, radius = 5f * pulse, center = Offset(center.x + eyeOffset, eyeY))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "LIFE-01 • ${state.name}",
            style = MaterialTheme.typography.labelMedium,
            color = stateColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}
