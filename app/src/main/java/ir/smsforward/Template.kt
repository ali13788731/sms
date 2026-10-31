package ir.smsforward

import android.icu.text.SimpleDateFormat as IcuDateFormat
import android.icu.util.ULocale
import android.telephony.SmsMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * قالب پیامِ ارسالی. متغیرهای قابل استفاده:
 * {sender} فرستنده، {body} متن پیام، {sim} شماره‌ی سیم، {time} ساعت، {date} تاریخ شمسی.
 */
object Template {
    const val DEFAULT = "پیام از {sender} (سیم {sim}):\n{body}"

    /** نام متغیرها به ترتیب نمایش در رابط کاربری */
    val TOKENS = listOf("{sender}", "{body}", "{sim}", "{time}", "{date}")

    private val tokenRegex = Regex("\\{(sender|body|sim|time|date)\\}")

    fun render(template: String, sender: String, body: String, sim: String, millis: Long): String {
        val t = template.ifBlank { "{body}" }
        // جایگزینی در یک مرحله تا اگر متن پیام خودش شامل {sender} بود دوباره جایگزین نشود
        val out = tokenRegex.replace(t) { m ->
            when (m.groupValues[1]) {
                "sender" -> sender
                "body" -> body
                "sim" -> sim
                "time" -> time(millis)
                else -> date(millis)
            }
        }
        return out.ifBlank { body }
    }

    fun time(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))

    /** تاریخ شمسی با ارقام لاتین، مثلاً 1405/08/08 */
    fun date(millis: Long): String = try {
        IcuDateFormat("yyyy/MM/dd", ULocale("en_US@calendar=persian")).format(Date(millis))
    } catch (e: Exception) {
        SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(millis))
    }

    fun dateTime(millis: Long): String = "${date(millis)} · ${time(millis)}"

    /** (تعداد بخش‌های پیامک، تعداد نویسه) */
    fun segments(text: String): Pair<Int, Int> = try {
        val r = SmsMessage.calculateLength(text, false)
        r[0] to r[1]
    } catch (e: Exception) {
        1 to text.length
    }
}
