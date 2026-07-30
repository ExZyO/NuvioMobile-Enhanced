package com.nuvio.app.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import com.nuvio.app.R

@Composable
actual fun appIconPainter(icon: AppIconResource): Painter {
    val resId = when (icon) {
        AppIconResource.DiscordMark -> R.drawable.discord_mark
        AppIconResource.GithubMark -> R.drawable.github_mark
        AppIconResource.PlayerPlay -> R.drawable.ic_player_play
        AppIconResource.PlayerPause -> R.drawable.ic_player_pause
        AppIconResource.PlayerAspectRatio -> R.drawable.ic_player_aspect_ratio
        AppIconResource.PlayerSubtitles -> R.drawable.ic_player_subtitles
        AppIconResource.PlayerAudioFilled -> R.drawable.ic_player_audio_filled
        AppIconResource.LibraryAddPlus -> R.drawable.library_add_plus
        AppIconResource.LauncherDefault -> R.mipmap.ic_launcher
        AppIconResource.LauncherEnhanced -> R.mipmap.ic_launcher_alt_enhanced
        AppIconResource.LauncherMonochrome -> R.mipmap.ic_launcher_alt_monochrome
        AppIconResource.LauncherNeon -> R.mipmap.ic_launcher_alt_neon
        AppIconResource.LauncherGear -> R.mipmap.ic_launcher_alt_gear
        AppIconResource.LauncherChrome -> R.mipmap.ic_launcher_alt_chrome
        AppIconResource.LauncherAurora -> R.mipmap.ic_launcher_alt_aurora
        AppIconResource.LauncherEmerald -> R.mipmap.ic_launcher_alt_emerald
    }

    val context = LocalContext.current
    return remember(resId, context) {
        runCatching {
            val drawable = ContextCompat.getDrawable(context, resId)
            if (drawable != null) {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 108
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 108
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                BitmapPainter(bitmap.asImageBitmap())
            } else {
                ColorPainter(Color.Transparent)
            }
        }.getOrElse {
            ColorPainter(Color.Transparent)
        }
    }
}
