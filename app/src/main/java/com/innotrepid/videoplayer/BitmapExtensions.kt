package com.innotrepid.videoplayer

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap as composeAsImageBitmap

fun Bitmap.asImageBitmap(): ImageBitmap = this.composeAsImageBitmap()
