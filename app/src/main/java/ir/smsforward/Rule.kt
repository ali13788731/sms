package ir.smsforward

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * یک «قانون انتقال»: چه پیامک‌هایی (سیم و فرستنده)، به چه کسانی (یک یا چند مقصد)،
 * با چه قالبی و از کدام سیم ارسال شود. هر قانون مستقل از قانون‌های دیگر است.
 */
data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    /** سیم دریافت: 0=هر دو، 1=سیم ۱، 2=سیم ۲ */
    val recvSim: Int = 0,
    val allSenders: Boolean = true,
    val senders: List<String> = emptyList(),
    val targets: List<String> = emptyList(),
    /** ارسال با: 0=همان سیم دریافت، 1=سیم ۱، 2=سیم ۲ */
    val sendSim: Int = 0,
    val template: String = Template.DEFAULT
) {
    fun displayName(c: Context, index: Int): String =
        name.ifBlank { c.getString(R.string.rule_default_name, index + 1) }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("enabled", enabled)
        .put("recvSim", recvSim)
        .put("allSenders", allSenders)
        .put("senders", JSONArray(senders))
        .put("targets", JSONArray(targets))
        .put("sendSim", sendSim)
        .put("template", template)

    companion object {
        private fun strings(a: JSONArray?): List<String> =
            if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

        fun fromJson(o: JSONObject) = Rule(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name"),
            enabled = o.optBoolean("enabled", true),
            recvSim = o.optInt("recvSim", 0).coerceIn(0, 2),
            allSenders = o.optBoolean("allSenders", true),
            senders = strings(o.optJSONArray("senders")),
            targets = strings(o.optJSONArray("targets")),
            sendSim = o.optInt("sendSim", 0).coerceIn(0, 2),
            template = o.optString("template", Template.DEFAULT)
        )
    }
}
