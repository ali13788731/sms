package ir.smsforward

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ir.smsforward.databinding.ActivityMainBinding
import ir.smsforward.databinding.ItemRuleBinding

class MainActivity : BaseActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs

    override val rootView: View get() = b.root

    /** true وقتی مقدارها از کد تنظیم می‌شوند تا شنونده‌ها دوباره ذخیره نکنند */
    private var binding = false

    /** تعداد قانون‌هایی که نمایش داده می‌شوند (صفحه‌بندی) */
    private var shown = PAGE

    /** نتیجه‌ی درخواست یک‌جای دسترسی‌ها در ابتدای برنامه */
    private val startupLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            prefs.permAsked = true
            if (!hasSmsPermissions()) {
                if (smsPermissionBlocked()) showSettingsDialog() else snack(R.string.perm_denied)
            }
            render()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        applyEdgeToEdge(b.root, b.topBar, b.scroll)
        prefs = Prefs(this)

        b.switchEnabled.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            if (checked) tryEnable() else {
                // خاموش‌شدن کلید اصلی یعنی همه‌ی قانون‌ها هم غیرفعال می‌شوند
                if (!prefs.disableAll()) {
                    showErrors(R.string.error_title, listOf(getString(R.string.err_save_failed)))
                }
                render()
            }
        }
        b.btnAddRule.setOnClickListener { openEditor(null, false) }
        b.btnMoreRules.setOnClickListener {
            shown += PAGE
            render()
        }

        // دسترسی‌ها همان اول و فقط یک بار در هر اجرای تازه گرفته می‌شوند
        if (savedInstanceState == null) b.root.post { if (!isFinishing) askStartupPermissions() }
    }

    override fun onResume() {
        super.onResume()
        // اگر کاربر دسترسی‌ها را از تنظیمات سیستم لغو کرده باشد
        if (prefs.enabled && !hasSmsPermissions()) {
            prefs.disableAll()
            snack(R.string.perm_revoked)
        }
        render()
    }

    override fun onSmsPermissionsDenied() = render()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_menu -> {
            MenuSheet.show(
                this,
                onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                onLog = { startActivity(Intent(this, LogActivity::class.java)) },
                onPrivacy = { showPrivacy() },
                onAbout = { showAbout() }
            )
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun openEditor(id: String?, enableMaster: Boolean) {
        startActivity(Intent(this, RuleActivity::class.java).apply {
            if (id != null) putExtra(RuleActivity.EXTRA_RULE_ID, id)
            if (enableMaster) putExtra(RuleActivity.EXTRA_ENABLE_MASTER, true)
        })
    }

    // ------------------------------------------------------------------ دسترسی‌های ابتدای برنامه

    private fun startupPermissions(): List<String> = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** دسترسی پیامک را کاربر برای همیشه رد کرده و دیگر پنجره‌ی سیستم نمایش داده نمی‌شود */
    private fun smsPermissionBlocked(): Boolean =
        listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
            .any { !granted(it) && !ActivityCompat.shouldShowRequestPermissionRationale(this, it) }

    /** اول توضیح می‌دهد، بعد با «ادامه» همه‌ی دسترسی‌ها را یک‌جا می‌خواهد. */
    private fun askStartupPermissions() {
        val missing = startupPermissions().filter { !granted(it) }
        if (missing.isEmpty()) return
        // فقط دسترسی‌های اختیاری مانده و قبلاً پرسیده شده‌اند: دوباره مزاحم نمی‌شویم
        if (hasSmsPermissions() && prefs.permAsked) return
        if (!hasSmsPermissions() && prefs.permAsked && smsPermissionBlocked()) {
            showSettingsDialog()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.perm_startup_title)
            .setMessage(R.string.perm_startup_message)
            .setCancelable(false)
            .setPositiveButton(R.string.perm_startup_continue) { _, _ ->
                startupLauncher.launch(missing.toTypedArray())
            }
            .show()
    }

    // ------------------------------------------------------------------ نمایش

    private fun attrColor(attr: Int): Int = MaterialColors.getColor(b.root, attr)

    private fun render() {
        val on = prefs.enabled
        val rules = prefs.rules()
        val activeCount = rules.count { it.enabled }

        binding = true
        b.switchEnabled.isChecked = on
        binding = false

        b.statusSubtitle.text = when {
            !on -> getString(R.string.status_off)
            activeCount == 0 -> getString(R.string.status_no_active)
            else -> getString(R.string.status_on, activeCount)
        }
        val primary = androidx.appcompat.R.attr.colorPrimary
        b.statusSubtitle.setTextColor(
            attrColor(if (on) primary else com.google.android.material.R.attr.colorOnSurfaceVariant)
        )
        b.cardStatus.strokeColor = attrColor(
            if (on) primary else com.google.android.material.R.attr.colorOutlineVariant
        )

        // تا وقتی ارسال خودکار روشن نشده، قانون‌ها اصلاً نمایش داده نمی‌شوند
        b.rulesSection.isVisible = on
        b.rulesHint.isVisible = !on

        renderRules(rules, on)
    }

    private fun renderRules(rules: List<Rule>, masterOn: Boolean) {
        b.emptyRules.isVisible = rules.isEmpty()
        b.rulesList.removeAllViews()
        val visible = rules.take(shown)
        b.btnMoreRules.isVisible = rules.size > visible.size
        b.btnMoreRules.text = getString(R.string.rules_more, rules.size - visible.size)
        visible.forEachIndexed { index, rule ->
            val row = ItemRuleBinding.inflate(layoutInflater, b.rulesList, false)
            row.ruleName.text = rule.displayName(this, index)

            val senders = if (rule.allSenders) getString(R.string.sum_all_senders)
            else getString(R.string.sum_n_senders, rule.senders.size)
            row.ruleSenders.text = getString(R.string.sum_from, senders)

            val shown = rule.targets.take(3).joinToString("، ") { bidi(it) }
            val more = if (rule.targets.size > 3) getString(R.string.sum_more, rule.targets.size - 3) else ""
            row.ruleTargets.text = getString(R.string.sum_to, rule.targets.size, shown + more)

            row.ruleSims.text = getString(R.string.sum_sims, simName(rule.recvSim, true), simName(rule.sendSim, false))

            binding = true
            row.ruleSwitch.isChecked = rule.enabled
            binding = false
            row.ruleSwitch.isEnabled = masterOn
            row.root.alpha = if (rule.enabled) 1f else 0.6f
            row.ruleSwitch.setOnCheckedChangeListener { _, checked ->
                if (binding) return@setOnCheckedChangeListener
                val wasOn = prefs.enabled
                if (!prefs.setRuleEnabled(rule.id, checked)) {
                    showErrors(R.string.error_title, listOf(getString(R.string.err_save_failed)))
                }
                if (wasOn && !prefs.enabled) snack(R.string.auto_off_no_rules)
                render()
            }
            row.ruleDelete.contentDescription = getString(R.string.rule_delete)
            row.ruleDelete.setOnClickListener { confirmDelete(rule, rule.displayName(this, index)) }
            row.root.setOnClickListener { openEditor(rule.id, false) }
            b.rulesList.addView(row.root)
        }
    }

    private fun confirmDelete(rule: Rule, name: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(name)
            .setMessage(R.string.rule_delete_confirm)
            .setPositiveButton(R.string.rule_delete) { _, _ ->
                val wasOn = prefs.enabled
                if (!prefs.deleteRule(rule.id)) {
                    showErrors(R.string.error_title, listOf(getString(R.string.err_save_failed)))
                }
                if (wasOn && !prefs.enabled) snack(R.string.auto_off_no_rules)
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private companion object {
        const val PAGE = 10
    }

    private fun simName(v: Int, recv: Boolean): String = when (v) {
        1 -> getString(R.string.chip_sim1)
        2 -> getString(R.string.chip_sim2)
        else -> getString(if (recv) R.string.chip_all_sims else R.string.chip_send_same)
    }

    // ------------------------------------------------------------------ عملیات

    /**
     * روشن‌کردن ارسال خودکار: اگر قانونی وجود دارد همان‌ها برمی‌گردند؛
     * وگرنه صفحه‌ی «قانون جدید» باز می‌شود و کلید فقط با ذخیره‌شدن آن روشن می‌شود.
     */
    private fun tryEnable() {
        withSmsPermissions {
            if (!prefs.enableWithExisting()) openEditor(null, true)
            render()
        }
        render() // تا ذخیره‌شدن قانون، کلید در حالت خاموش می‌ماند
    }
}
