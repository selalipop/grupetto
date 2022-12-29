package com.spop.poverlay.overlay.composables

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spop.poverlay.R
import com.spop.poverlay.overlay.BackgroundColorDefault
import com.spop.poverlay.overlay.OverlayLocation


@Composable
fun OverlayMinimizedContent(
    isMinimized: Boolean,
    showTimerWhenMinimized: Boolean,
    location: OverlayLocation,
    powerLabel: String,
    cadenceLabel: String,
    speedLabel: String,
    contentAlpha: Float,
    timerLabel: String,
    timerPaused: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val backgroundShape = if (isMinimized) {
        RoundedCornerShape(8.dp)
    } else {
        when (location) {
            OverlayLocation.Top -> RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
            OverlayLocation.Bottom -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
        }
    }
    val expandedVerticalPadding = if (isMinimized) {
        1.dp
    } else {
        0.dp
    }
    Row(
        modifier = Modifier
            .alpha(contentAlpha)
            .wrapContentSize()
            .padding(vertical = expandedVerticalPadding)
            .background(
                color = BackgroundColorDefault,
                shape = backgroundShape,
            )
            .padding(horizontal = 10.dp)
            .padding(top = 1.dp)
            .animateContentSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onTap()
                    },
                    onLongPress = {
                        onLongPress()
                    }
                )
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val infiniteTransition = rememberInfiniteTransition()
        if (!isMinimized || showTimerWhenMinimized || timerPaused) {

            val timerAlpha = if (timerPaused) {
                infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                ).value
            } else {
                1f
            }

            OverlayTimerField(
                modifier = Modifier
                    .width(80.dp)
                    .alpha(timerAlpha),
                timerLabel = timerLabel,
                icon = painterResource(id = R.drawable.ic_timer)
            )
        }

        if (isMinimized) {
            Spacer(modifier = Modifier.width(4.dp))
            OverlayTimerField(
                modifier = Modifier.width(56.dp),
                timerLabel = powerLabel,
                icon = painterResource(id = R.drawable.ic_power)
            )
            Spacer(modifier = Modifier.width(4.dp))
            OverlayTimerField(
                modifier = Modifier.width(56.dp),
                timerLabel = cadenceLabel,
                icon = painterResource(id = R.drawable.ic_cadence)
            )
            Spacer(modifier = Modifier.width(4.dp))
            OverlayTimerField(
                modifier = Modifier.width(58.dp),
                timerLabel = speedLabel,
                icon = painterResource(id = R.drawable.ic_speed)
            )
        }
    }
}

@Composable
private fun OverlayTimerField(
    modifier: Modifier,
    timerLabel: String,
    icon: Painter,
) {
    Row(
        modifier = modifier
            .wrapContentHeight(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Image(
            modifier = Modifier
                .requiredHeight(20.dp)
                .requiredWidth(16.dp)
                .padding(vertical = 4.dp),
            painter = icon,
            contentDescription = null,
        )
        Text(
            timerLabel,
            color = Color.White,
            fontSize = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
        )
    }
}
