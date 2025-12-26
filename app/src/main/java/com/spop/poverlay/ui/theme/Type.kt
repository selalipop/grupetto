package com.spop.poverlay.ui.theme

import androidx.compose.material.Typography
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
        body1 = TextStyle(
                fontFamily = LatoFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp
        )
)
