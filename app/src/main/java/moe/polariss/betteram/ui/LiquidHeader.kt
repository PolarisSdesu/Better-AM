package moe.polariss.betteram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle

@Suppress("unused")
@Composable fun LiquidHeader() {
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Brush.horizontalGradient(listOf(Color(0xFFFFB7C2), Color(0xFFE7D9FF), Color(0xFFFFDCE2)))))
    Box(
        Modifier.fillMaxSize().drawBackdrop(
            backdrop = backdrop, shape = { RoundedRectangle(24.dp) },
            effects = { vibrancy(); blur(8.dp.toPx()); lens(18.dp.toPx(), 20.dp.toPx()) },
            highlight = { Highlight.Plain },
            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.28f)) },
        ), contentAlignment = Alignment.Center
    ) {
        BasicText("Better AM", style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFF242129)))
    }
}
