package posser.nativecamera.util

import android.content.Context

fun dp2px(context: Context, dp: Float): Float {
    return dp * getDensity(context)
}

fun px2dp(context: Context, px: Float): Float {
    return px / getDensity(context)
}

private fun getDensity(context: Context): Float {
    return context.resources.displayMetrics.density
}