package ir.smsforward

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.text.BidiFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/** منطق مشترک صفحه‌ها: درخواست دسترسی‌ها (با توضیح فارسی)، پیام کوتاه و کمکی‌های نمایش. */
abstract class BaseActivity : AppCompatActivity() {

    protected abstract val rootView: View

    /** کاری که بعد از دریافت دسترسی‌های پیامک باید انجام شود */
    private var pendingAction: (() -> Unit)? = null

    /** دسترسی اختیاری مخاطبین/پیامک که در حال درخواست است، با کاری که بعد از دریافت باید انجام شود */
    private var pendingPerm: Pair<String, () -> Unit>? = null

    /** وقتی کاربر دسترسی‌های پیامک را نداد یا انصراف داد */
    protected open fun onSmsPermissionsDenied() {}

    /** وقتی دسترسی اختیاری مخاطبین/پیامک داده شد */
    protected open fun onOptionalPermissionGranted() {}

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val action = pendingAction
            pendingAction = null
            if (hasSmsPermissions()) {
                action?.invoke()
            } else {
                val blocked = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
                    .any { !granted(it) && !ActivityCompat.shouldShowRequestPermissionRationale(this, it) }
                if (blocked) showSettingsDialog() else snack(R.string.perm_denied)
                onSmsPermissionsDenied()
            }
        }

    private val singlePermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            val (perm, action) = pendingPerm ?: return@registerForActivityResult
            pendingPerm = null
            if (ok) {
                action()
                onOptionalPermissionGranted()
            } else if (!ActivityCompat.shouldShowRequestPermissionRationale(this, perm)) {
                showSettingsDialog(R.string.perm_optional_settings_msg)
            } else {
                snack(R.string.perm_denied_optional)
            }
        }

    protected fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    protected fun hasSmsPermissions() =
        granted(Manifest.permission.RECEIVE_SMS) && granted(Manifest.permission.SEND_SMS)

    /**
     * دسترسی‌ها فقط در لحظه‌ی نیاز و بعد از توضیح کاربر درخواست می‌شوند،
     * نه هنگام باز شدن برنامه.
     */
    protected fun withSmsPermissions(onGranted: () -> Unit) {
        if (hasSmsPermissions()) {
            onGranted()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.perm_title)
            .setMessage(R.string.perm_message)
            .setPositiveButton(R.string.perm_continue) { _, _ ->
                pendingAction = onGranted
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.READ_PHONE_STATE
                    )
                )
            }
            .setNegativeButton(R.string.cancel) { _, _ -> onSmsPermissionsDenied() }
            .setOnCancelListener { onSmsPermissionsDenied() }
            .show()
    }

    /** دسترسی اختیاری مخاطبین/پیامک؛ بعد از توضیح فارسی درخواست می‌شود. */
    protected fun withOptionalPermission(kind: Sources.Kind, onGranted: () -> Unit) {
        if (Sources.has(this, kind)) {
            onGranted()
            return
        }
        val contacts = kind == Sources.Kind.CONTACT
        MaterialAlertDialogBuilder(this)
            .setTitle(if (contacts) R.string.perm_contacts_title else R.string.perm_sms_title)
            .setMessage(if (contacts) R.string.perm_contacts_message else R.string.perm_sms_message)
            .setPositiveButton(R.string.perm_continue) { _, _ ->
                val perm = Sources.permission(kind)
                pendingPerm = perm to onGranted
                singlePermLauncher.launch(perm)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    protected fun showSettingsDialog(message: Int = R.string.perm_settings_msg) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.perm_settings_title)
            .setMessage(message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** فهرست کامل خطاها در یک پنجره (هر خطا در یک خط) */
    protected fun showErrors(title: Int, errors: List<String>) {
        val text = errors.joinToString("\n\n") { "• $it" }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(title) + " (" + errors.size + ")")
            .setMessage(text)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    protected fun showPrivacy() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.privacy_title)
            .setMessage(R.string.privacy_text)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    protected fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_name)
            .setMessage(getString(R.string.about_text, versionName()))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    /** شماره‌ها داخل متن راست‌به‌چپ جابه‌جا نمایش داده نشوند */
    protected fun bidi(s: String): String = BidiFormatter.getInstance(true).unicodeWrap(s)

    protected fun snack(resId: Int) = Snackbar.make(rootView, resId, Snackbar.LENGTH_LONG).show()
}
