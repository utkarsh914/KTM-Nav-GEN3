package com.navigator.app.ui.feature.settings.picker

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.navigator.app.ui.theme.KtmLayout
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app's launcher icon, loaded off the main thread; an empty 40 dp square until it arrives (or when there is none). */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val pm = LocalContext.current.packageManager
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { pm.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier.size(KtmLayout.appIcon)) {
        icon?.let { Image(it, null, Modifier.size(KtmLayout.appIcon)) }
    }
}

/** The decoded size: 2.4x the 40 dp slot at 1x density, sharp up to xxhdpi. */
private const val ICON_PX = 96
