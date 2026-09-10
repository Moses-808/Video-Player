package com.innotrepid.videoplayer

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap

fun Bitmap.asImageBitmap(): ImageBitmap = androidx.compose.ui.graphics.asImageBitmap(this)
