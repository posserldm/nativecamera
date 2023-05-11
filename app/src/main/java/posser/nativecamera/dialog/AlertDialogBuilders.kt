package posser.nativecamera.dialog

import android.content.Context
import androidx.appcompat.app.AlertDialog

fun buildMediaDetail(
    context: Context,
    msg: String
) {
    buildAlterDialog(context, "媒体文件详情", msg, false, {}).show()
}

private fun buildAlterDialog(
    context: Context,
    title: String,
    msg: String,
    cancelAble: Boolean = false,
    okCallback: () -> Unit = {},
    cancelCallback: () -> Unit = {}
): AlertDialog.Builder {
    val builder = AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(msg)
        .setCancelable(cancelAble)
    builder.setPositiveButton("确定") { _, _ ->
        okCallback()
    }

    builder.setNegativeButton("取消") { _, _ ->
        cancelCallback()
    }
    return builder
}