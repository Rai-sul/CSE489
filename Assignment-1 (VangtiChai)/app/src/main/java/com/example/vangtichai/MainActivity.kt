package com.example.vangtichai
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

private const val KEY_AMOUNT = "amount"
private const val MAX_AMOUNT = 99_999_999L   // stops the number from overflowing

class MainActivity : AppCompatActivity() {

    private val noteValues = listOf(500, 100, 50, 20, 10, 5, 2, 1)
    private val noteIds = listOf(
        R.id.note500, R.id.note100, R.id.note50, R.id.note20,
        R.id.note10, R.id.note5, R.id.note2, R.id.note1
    )

    private lateinit var amountText: TextView
    private lateinit var noteViews: List<TextView>
    private var amount = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)   // portrait or landscape file, chosen by Android

        amountText = findViewById(R.id.amountText)
        noteViews = noteIds.map { findViewById<TextView>(it) }

        // After a rotation the activity is recreated; get the amount back
        amount = savedInstanceState?.getLong(KEY_AMOUNT) ?: 0L
        showAmountAndChange()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_AMOUNT, amount)
    }

    // Called by every digit button (android:onClick="onDigitClick")
    fun onDigitClick(view: View) {
        if (amount > MAX_AMOUNT) return
        val digit = (view as Button).text.toString().toLong()
        amount = amount * 10 + digit             // 2 -> 23 -> 234
        showAmountAndChange()
    }

    fun onClearClick(view: View) {
        amount = 0L
        showAmountAndChange()
    }

    private fun showAmountAndChange() {
        val shown = if (amount == 0L) "" else amount.toString()
        amountText.text = getString(R.string.taka_format, shown)

        var remaining = amount
        for (i in noteValues.indices) {
            val count = remaining / noteValues[i]   // how many of this note
            remaining %= noteValues[i]              // what is left
            noteViews[i].text = getString(R.string.note_format, noteValues[i], count)
        }
    }
}