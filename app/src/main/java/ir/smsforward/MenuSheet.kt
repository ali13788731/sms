package ir.smsforward

import android.app.Activity
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetDialog
import ir.smsforward.databinding.SheetMenuBinding

/** منوی اصلی برنامه: یک صفحه‌ی کشویی از پایین با گزینه‌های مرتب و آیکون‌دار. */
object MenuSheet {

    fun show(
        activity: Activity,
        onSettings: () -> Unit,
        onLog: () -> Unit,
        onPrivacy: () -> Unit,
        onAbout: () -> Unit
    ) {
        val dialog = BottomSheetDialog(activity)
        val sb = SheetMenuBinding.inflate(activity.layoutInflater)
        fun View.act(action: () -> Unit) = setOnClickListener {
            dialog.dismiss()
            action()
        }
        sb.rowSettings.act(onSettings)
        sb.rowLog.act(onLog)
        sb.rowPrivacy.act(onPrivacy)
        sb.rowAbout.act(onAbout)
        dialog.setContentView(sb.root)
        dialog.show()
    }
}
