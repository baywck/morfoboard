package com.morfoboard.app.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.morfoboard.app.R

/**
 * Shows AI results in a bottom sheet overlay above the keyboard.
 */
class BottomSheetPresenter(
    private val context: Context,
    private val parentView: android.view.ViewGroup
) {

    private var bottomSheet: View? = null
    private var onReplace: ((String) -> Unit)? = null

    fun show(
        title: String,
        originalText: String,
        resultText: String,
        metadata: String = "",
        onReplace: (String) -> Unit,
        onDismiss: () -> Unit
    ) {
        this.onReplace = onReplace
        dismiss()

        val sheet = createBottomSheet(title, originalText, resultText, metadata, onDismiss)
        parentView.addView(sheet)
        bottomSheet = sheet

        // Animate in
        sheet.alpha = 0f
        sheet.translationY = 100f
        sheet.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .start()
    }

    fun dismiss() {
        bottomSheet?.let { sheet ->
            sheet.animate()
                .alpha(0f)
                .translationY(100f)
                .setDuration(150)
                .withEndAction {
                    parentView.removeView(sheet)
                }
                .start()
        }
        bottomSheet = null
    }

    private fun createBottomSheet(
        title: String,
        originalText: String,
        resultText: String,
        metadata: String,
        onDismiss: () -> Unit
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bottom_sheet_bg)
            elevation = 16f
            setPadding(dp(20), dp(16), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            // Header
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleView = TextView(context).apply {
                text = title
                setTextColor(context.getColor(R.color.text_primary))
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val closeBtn = TextView(context).apply {
                text = "✕"
                setTextColor(context.getColor(R.color.text_secondary))
                textSize = 18f
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener {
                    onDismiss()
                    dismiss()
                }
            }

            header.addView(titleView)
            header.addView(closeBtn)
            addView(header)

            // Metadata
            if (metadata.isNotEmpty()) {
                val metaView = TextView(context).apply {
                    text = metadata
                    setTextColor(context.getColor(R.color.text_secondary))
                    textSize = 12f
                    setPadding(0, dp(4), 0, dp(8))
                }
                addView(metaView)
            }

            // Divider
            addView(View(context).apply {
                setBackgroundColor(context.getColor(R.color.bottom_sheet_divider))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                )
                setPadding(0, dp(8), 0, dp(8))
            })

            // Result text
            val resultView = TextView(context).apply {
                text = resultText
                setTextColor(context.getColor(R.color.bottom_sheet_text))
                textSize = 16f
                setPadding(0, dp(12), 0, dp(16))
                // Allow scrolling for long text
                maxLines = 6
                isVerticalScrollBarEnabled = true
                movementMethod = android.text.method.ScrollingMovementMethod()
            }
            addView(resultView)

            // Button row
            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }

            val dismissBtn = Button(context).apply {
                text = "Dismiss"
                setTextColor(context.getColor(R.color.text_secondary))
                setBackgroundResource(R.drawable.btn_outline)
                textSize = 13f
                setPadding(dp(16), dp(8), dp(16), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener {
                    onDismiss()
                    dismiss()
                }
            }

            val copyBtn = Button(context).apply {
                text = "Copy"
                setTextColor(context.getColor(R.color.text_primary))
                setBackgroundResource(R.drawable.btn_outline)
                textSize = 13f
                setPadding(dp(16), dp(8), dp(16), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("morfoboard", resultText))
                    Toast.makeText(context, context.getString(com.morfoboard.app.R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                }
            }

            val replaceBtn = Button(context).apply {
                text = "Replace"
                setTextColor(context.getColor(R.color.key_text))
                setBackgroundResource(R.drawable.btn_primary)
                textSize = 13f
                setPadding(dp(16), dp(8), dp(16), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    this@BottomSheetPresenter.onReplace?.invoke(resultText)
                    dismiss()
                }
            }

            btnRow.addView(dismissBtn)
            btnRow.addView(copyBtn)
            btnRow.addView(replaceBtn)
            addView(btnRow)
        }
    }

    private fun dp(value: Int): Int {
        val density = context.resources.displayMetrics.density
        return (value * density).toInt()
    }
}
