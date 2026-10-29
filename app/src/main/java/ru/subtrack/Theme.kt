package ru.subtrack

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun SubTrackTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary=Color(0xFF82DBC0), onPrimary=Color(0xFF00382B),
        primaryContainer=Color(0xFF194F40), onPrimaryContainer=Color(0xFFB9F5DF),
        secondaryContainer=Color(0xFF234238), onSecondaryContainer=Color(0xFFB9F5DF),
        background=Color(0xFF101916), surface=Color(0xFF18241F),
        surfaceVariant=Color(0xFF263830), onSurface=Color(0xFFE8F1EB),
        onSurfaceVariant=Color(0xFFAABFB3), outline=Color(0xFF698175)
    ) else lightColorScheme(
        primary=Color(0xFF176B50), onPrimary=Color.White,
        primaryContainer=Color(0xFFDDF2E6), onPrimaryContainer=Color(0xFF123D2E),
        secondaryContainer=Color(0xFFE0F0E6), onSecondaryContainer=Color(0xFF19543F),
        background=Color(0xFFF3F6F3), surface=Color.White,
        surfaceVariant=Color(0xFFEAF0EB), onSurface=Color(0xFF182C23),
        onSurfaceVariant=Color(0xFF61756A), outline=Color(0xFFA5B8AC)
    )
    MaterialTheme(
        colorScheme=colors,
        shapes=Shapes(small=RoundedCornerShape(12.dp), medium=RoundedCornerShape(18.dp), large=RoundedCornerShape(24.dp)),
        typography=Typography(
            headlineLarge=TextStyle(fontWeight=FontWeight.Bold,fontSize=34.sp,lineHeight=40.sp),
            headlineMedium=TextStyle(fontWeight=FontWeight.Bold,fontSize=28.sp,lineHeight=34.sp),
            titleLarge=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=22.sp,lineHeight=28.sp),
            titleMedium=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=24.sp)
        ), content=content
    )
}
