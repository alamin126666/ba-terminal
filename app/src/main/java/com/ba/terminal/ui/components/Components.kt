package com.ba.terminal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ba.terminal.ui.theme.GlassBlue
import com.ba.terminal.ui.theme.GlassCard
import com.ba.terminal.ui.theme.GlassMuted
import com.ba.terminal.ui.theme.GlassText

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val m = if (onClick != null) modifier.clip(RoundedCornerShape(20.dp)).clickable { onClick() } else modifier.clip(RoundedCornerShape(20.dp))
    Column(m.background(GlassCard.copy(alpha = 0.92f)).padding(16.dp), content = content)
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = GlassText, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
fun Muted(text: String) {
    Text(text, color = GlassMuted, fontSize = 13.sp)
}

@Composable
fun Chip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(GlassBlue.copy(alpha = 0.16f)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(text, color = GlassBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}
