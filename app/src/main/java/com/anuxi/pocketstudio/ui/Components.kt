package com.anuxi.pocketstudio.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Ps {
    val BgTop = Color(0xFF1A1D3A)
    val BgBottom = Color(0xFF2A1F55)
    val Mint = Color(0xFF7CF2C8)
    val OnMint = Color(0xFF10263A)
    val Text = Color(0xFFF2F3FF)
    val Muted = Color(0xFFB9BCE0)
    val Muted2 = Color(0xFF9DA1CC)
    val Amber = Color(0xFFFFC46B)
    val Card = Color(0x12FFFFFF)
    val CardStrong = Color(0x1AFFFFFF)
    val Line = Color(0x29FFFFFF)
    val Background = Brush.verticalGradient(listOf(BgTop, BgBottom))
}

val LocalReduceMotion = compositionLocalOf { false }

/** Clic con un pequeño efecto de "presionado" (se desactiva con "Reducir animaciones"). */
fun Modifier.pressable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce && enabled) 0.96f else 1f,
        animationSpec = tween(if (reduce) 0 else 120),
        label = "press",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
}

@Composable
fun PsIcon(res: Int, tint: Color, size: Dp = 22.dp, description: String? = null) {
    Icon(painterResource(res), contentDescription = description, tint = tint, modifier = Modifier.size(size))
}

@Composable
fun PsIcon(vector: ImageVector, tint: Color, size: Dp = 22.dp, description: String? = null) {
    Icon(vector, contentDescription = description, tint = tint, modifier = Modifier.size(size))
}

@Composable
fun MintButton(
    text: String,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(if (enabled) Ps.Mint else Ps.Mint.copy(alpha = 0.35f))
            .pressable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            PsIcon(iconRes, Ps.OnMint)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = Ps.OnMint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(30.dp))
            .border(BorderStroke(1.5.dp, Ps.Line), RoundedCornerShape(30.dp))
            .pressable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            PsIcon(iconRes, Ps.Text)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = Ps.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RoundIconButton(vector: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Ps.CardStrong)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PsIcon(vector, Ps.Text, description = description)
    }
}

@Composable
fun Badge(text: String, bg: Color, fg: Color) {
    Text(
        text,
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onBack)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            color = Ps.Text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Ps.Muted2,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
    )
}
