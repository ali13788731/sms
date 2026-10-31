package ir.smsforward

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDivider
import ir.smsforward.databinding.ActivityLogBinding
import ir.smsforward.databinding.ItemLogBinding

class LogActivity : AppCompatActivity() {

    private lateinit var b: ActivityLogBinding
    private lateinit var prefs: Prefs
    private var shown = PAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLogBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        applyEdgeToEdge(b.root, b.topBar, b.scroll)
        prefs = Prefs(this)
        b.btnMoreLog.setOnClickListener {
            shown += PAGE
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_log, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.action_clear) return super.onOptionsItemSelected(item)
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.log_clear_confirm)
            .setPositiveButton(R.string.log_clear) { _, _ ->
                prefs.clearLog()
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return true
    }

    private fun render() {
        val entries = prefs.logEntries()
        b.emptyText.isVisible = entries.isEmpty()
        b.listCard.isVisible = entries.isNotEmpty()
        b.list.removeAllViews()
        val visible = entries.take(shown)
        b.btnMoreLog.isVisible = entries.size > visible.size
        b.btnMoreLog.text = getString(R.string.log_more, entries.size - visible.size)

        val okColor = ContextCompat.getColor(this, R.color.success)
        val errColor = ContextCompat.getColor(this, R.color.error)

        visible.forEachIndexed { index, e ->
            if (index > 0) b.list.addView(MaterialDivider(this))
            val row = ItemLogBinding.inflate(layoutInflater, b.list, false)
            row.dot.backgroundTintList = ColorStateList.valueOf(if (e.ok) okColor else errColor)
            row.title.text = e.sender
            // شماره‌ها (مثل 0912***4567 یا +98…) همیشه چپ‌به‌راست نمایش داده شوند
            val first = e.sender.firstOrNull()
            row.title.textDirection =
                if (first != null && (first.isDigit() || first == '+')) View.TEXT_DIRECTION_LTR
                else View.TEXT_DIRECTION_ANY_RTL
            row.note.text = e.note
            row.note.setTextColor(if (e.ok) row.note.currentTextColor else errColor)
            row.time.text = Template.dateTime(e.time)
            b.list.addView(row.root)
        }
    }

    private companion object {
        const val PAGE = 20
    }
}
