// asknonwear/src/main/java/com/example/asknonwear/theme/Theme.kt

package com.example.asknonwear.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme

@Composable
fun WearMaterialTheme(
    content: @Composable () -> Unit
) =
    // Aquí usamos la paleta de colores y la tipografía que definimos antes
    MaterialTheme(
        colors = wearColorPalette,
        typography = Typography,
        content = content
    )