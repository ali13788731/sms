package ir.smsforward

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(c: Context) {
    private val ctx = c.applicationContext
    private val sp = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    /** کلید اصلی؛ وقتی خاموش باشد هیچ قانونی اجرا نمی‌شود. */
    var enabled: Boolean
        get() = sp.getBoolean("enabled", false) && UpdateGate.state(ctx) == UpdateGate.State.OK
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    /** حالت نمایش: 0=خودکار (طبق ساعت)، 1=روشن، 2=تاریک */
    var themeMode: Int
        get() = sp.getInt("themeMode", 0).coerceIn(0, 2)
        set(v) = sp.edit().putInt("themeMode", v).apply()

    // ---------------------------------------------------------------- قانون‌ها

    fun rules(): List<Rule> = synchronized(LOCK) {
        val raw = sp.getString("rules", null) ?: return@synchronized migrateLegacy()
        try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Rule.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun rule(id: String): Rule? = rules().firstOrNull { it.id == id }

    /**
     * commit همزمان تا خطای ذخیره‌سازی قابل تشخیص باشد؛ true یعنی ذخیره شد.
     * قاعده: ارسال خودکار فقط وقتی روشن می‌ماند که حداقل یک قانون (فعال یا غیرفعال) وجود داشته باشد؛
     * غیرفعال‌کردن قانون‌ها کلید اصلی را خاموش نمی‌کند.
     */
    fun saveRules(list: List<Rule>, masterOn: Boolean? = null): Boolean = synchronized(LOCK) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val on = (masterOn ?: sp.getBoolean("enabled", false)) && list.isNotEmpty()
        sp.edit().putString("rules", arr.toString()).putBoolean("enabled", on).commit()
    }

    /** اگر شناسه موجود باشد جایگزین می‌شود، وگرنه به انتهای لیست اضافه می‌شود. */
    fun upsertRule(r: Rule, enableMaster: Boolean = false): Boolean = synchronized(LOCK) {
        val all = rules()
        val i = all.indexOfFirst { it.id == r.id }
        val next = if (i >= 0) all.toMutableList().also { it[i] = r } else all + r
        saveRules(next, if (enableMaster) true else null)
    }

    /**
     * خاموش‌کردن ارسال خودکار: کلید اصلی و همه‌ی قانون‌ها با هم غیرفعال می‌شوند.
     * شناسه‌ی قانون‌هایی که فعال بودند نگه داشته می‌شود تا با روشن‌کردن دوباره برگردند.
     */
    fun disableAll(): Boolean = synchronized(LOCK) {
        val all = rules()
        val active = all.filter { it.enabled }.map { it.id }
        val ok = saveRules(all.map { it.copy(enabled = false) }, false)
        if (ok && active.isNotEmpty()) sp.edit().putString("restore_ids", JSONArray(active).toString()).commit()
        ok
    }

    /**
     * روشن‌کردن ارسال خودکار با قانون‌های موجود: قانون‌هایی که قبل از خاموش‌شدن فعال بودند
     * دوباره فعال می‌شوند. اگر هیچ قانونی وجود نداشته باشد false برمی‌گرداند.
     */
    fun enableWithExisting(): Boolean = synchronized(LOCK) {
        val all = rules()
        if (all.isEmpty()) return@synchronized false
        val ids = try {
            val a = JSONArray(sp.getString("restore_ids", "[]"))
            (0 until a.length()).map { a.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
        val restored = all.map { if (it.id in ids) it.copy(enabled = true) else it }
        saveRules(restored, true)
    }

    fun deleteRule(id: String): Boolean = synchronized(LOCK) { saveRules(rules().filterNot { it.id == id }) }

    fun setRuleEnabled(id: String, on: Boolean): Boolean = synchronized(LOCK) {
        saveRules(rules().map { if (it.id == id) it.copy(enabled = on) else it })
    }

    /** آیا درخواست یک‌جای دسترسی‌های ابتدای برنامه حداقل یک بار انجام شده است */
    var permAsked: Boolean
        get() = sp.getBoolean("perm_asked", false)
        set(v) = sp.edit().putBoolean("perm_asked", v).apply()

    // ---------------------------------------------------------------- بروزرسانی اجباری

    /** کمترین نسخه‌ای که از این به بعد اجازه‌ی کار دارد (از سرور گرفته و ذخیره شده است) */
    var reqCode: Int
        get() = sp.getInt("upd_code", 0)
        set(v) = sp.edit().putInt("upd_code", v).apply()

    val reqName: String get() = sp.getString("upd_name", "") ?: ""
    val reqReason: String get() = sp.getString("upd_reason", "") ?: ""
    val reqUrl: String get() = sp.getString("upd_url", "") ?: ""
    val reqChanges: List<String>
        get() = try {
            val a = JSONArray(sp.getString("upd_changes", "[]"))
            (0 until a.length()).map { a.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }

    /** نسخه‌ای که برایش اعلان فرستاده شده، تا اعلان تکراری نرود */
    var notifiedCode: Int
        get() = sp.getInt("upd_notified", 0)
        set(v) = sp.edit().putInt("upd_notified", v).apply()

    fun saveRequiredUpdate(code: Int, name: String, reason: String, url: String, changes: List<String>) {
        sp.edit()
            .putInt("upd_code", code)
            .putString("upd_name", name)
            .putString("upd_reason", reason)
            .putString("upd_url", url)
            .putString("upd_changes", JSONArray(changes).toString())
            .commit()
    }

    fun clearRequiredUpdate() {
        sp.edit().remove("upd_code").remove("upd_name").remove("upd_reason")
            .remove("upd_url").remove("upd_changes").apply()
    }

    /** آخرین بررسی موفق نسخه (میلی‌ثانیه) */
    var lastOk: Long
        get() = sp.getLong("upd_last_ok", 0L)
        set(v) = sp.edit().putLong("upd_last_ok", v).apply()

    /** آخرین تلاش برای بررسی، موفق یا ناموفق */
    var lastTry: Long
        get() = sp.getLong("upd_last_try", 0L)
        set(v) = sp.edit().putLong("upd_last_try", v).apply()

    var firstRun: Long
        get() = sp.getLong("first_run", 0L)
        set(v) = sp.edit().putLong("first_run", v).apply()

    /** بیشترین زمانی که ساعت گوشی دیده شده؛ برای تشخیص عقب‌بردن ساعت */
    var lastSeen: Long
        get() = sp.getLong("last_seen", 0L)
        set(v) = sp.edit().putLong("last_seen", v).apply()

    fun markCheckOk(now: Long) {
        sp.edit().putLong("upd_last_ok", now).putLong("upd_last_try", now).putLong("last_seen", now).commit()
    }

    /** تنظیمات نسخه‌های قبل (یک شماره، یک فیلتر) به یک قانون تبدیل می‌شود. */
    private fun migrateLegacy(): List<Rule> {
        val target = sp.getString("target", "") ?: ""
        val list = if (target.isBlank()) emptyList() else listOf(
            Rule(
                name = ctx.getString(R.string.rule_default_name, 1),
                recvSim = sp.getInt("recvSim", 0).coerceIn(0, 2),
                allSenders = sp.getInt("filterMode", 0) != 1,
                senders = Forwarder.parseList(sp.getString("filterList", "") ?: ""),
                targets = listOf(target),
                sendSim = sp.getInt("sendSim", 0).coerceIn(0, 2),
                template = sp.getString("template", null)
                    ?: if (sp.getBoolean("header", true)) Template.DEFAULT else "{body}"
            )
        )
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("rules", arr.toString())
            .remove("target").remove("recvSim").remove("filterMode").remove("filterList")
            .remove("sendSim").remove("template").remove("header")
            .apply()
        return list
    }

    // ---------------------------------------------------------------- گزارش
    // فقط فرستنده، زمان و وضعیت ذخیره می‌شود؛ متن پیام‌ها هرگز ذخیره نمی‌شود.

    data class LogEntry(val time: Long, val sender: String, val ok: Boolean, val note: String)

    fun logEntries(): List<LogEntry> = synchronized(LOCK) {
        try {
            val arr = JSONArray(sp.getString("log2", "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                LogEntry(o.getLong("t"), o.getString("s"), o.getBoolean("ok"), o.optString("n"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addLog(sender: String, ok: Boolean, note: String) = synchronized(LOCK) {
        val all = listOf(LogEntry(System.currentTimeMillis(), sender, ok, note)) + logEntries()
        val arr = JSONArray()
        all.take(MAX_LOG).forEach {
            arr.put(JSONObject().put("t", it.time).put("s", it.sender).put("ok", it.ok).put("n", it.note))
        }
        sp.edit().putString("log2", arr.toString()).apply()
    }

    fun clearLog() = synchronized(LOCK) { sp.edit().remove("log2").apply() }

    private companion object {
        val LOCK = Any()
        const val MAX_LOG = 300
    }
}
