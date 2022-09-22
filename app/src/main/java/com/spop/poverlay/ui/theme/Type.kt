package com.spop.poverlay.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.spop.poverlay.R

val LatoFontFamily = FontFamily(
        Font(R.font.lato_bold, weight = FontWeight.Bold),
        Font(R.font.lato_bolditalic, weight = FontWeight.Bold, style = FontStyle.Italic),

        Font(R.font.lato_black, weight = FontWeight.ExtraBold),
        Font(R.font.lato_blackitalic, weight = FontWeight.ExtraBold, style = FontStyle.Italic),

        Font(R.font.lato_regular, weight = FontWeight.Normal),
        Font(R.font.lato_italic, weight = FontWeight.Normal, style = FontStyle.Italic),

        Font(R.font.lato_thin, weight = FontWeight.Thin),
        Font(R.font.lato_thinitalic, weight = FontWeight.Thin,style = FontStyle.Italic),

        Font(R.font.lato_light, weight = FontWeight.Light),
        Font(R.font.lato_lightitalic, weight = FontWeight.Light,style = FontStyle.Italic),
)
// Set of Material typography styles to start with
val Typography = Typography(
        bodyLarge = TextStyle(
                fontFamily = LatoFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp
        )
        /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)