package com.spop.poverlay.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun StatCard(
    name: String,
    value: String,
    unit: String,
    modifier: Modifier,
    iconDrawable: Int? = null,
    maxValue: String? = null,
    totalValue: String? = null,
    totalUnit: String? = null,
    color: Color = Color.White,
    onClick: () -> Unit = {},
    onUnitClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconDrawable != null) {
                Image(
                    modifier = Modifier
                        .requiredHeight(18.dp)
                        .requiredWidth(14.dp)
                        .padding(end = 4.dp),
                    painter = painterResource(id = iconDrawable),
                    contentDescription = null,
                )
            }
            Text(
                text = name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            )
        }
        Text(
            text = value,
            color = color,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        // Total and max row
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (totalValue != null && totalUnit != null) {
                Text(
                    text = "$totalValue $totalUnit",
                    color = Color.Gray,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                )
                if (maxValue != null && maxValue != "0") {
                    Text(
                        text = " | ",
                        color = Color.Gray,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Light
                    )
                }
            }
            if (maxValue != null && maxValue != "0") {
                Text(
                    text = maxValue,
                    color = Color.Gray,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
        // Unit row
        if (onUnitClick != null) {
            Text(
                text = unit,
                fontSize = 14.sp,
                color = color,
                fontWeight = FontWeight.Light,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onUnitClick() }
            )
        } else {
            Text(
                text = unit,
                fontSize = 14.sp,
                color = color,
                fontWeight = FontWeight.Light
            )
        }
    }
}

