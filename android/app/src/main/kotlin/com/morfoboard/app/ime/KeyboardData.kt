package com.morfoboard.app.ime

/**
 * Data model for keyboard keys and layouts.
 */
data class KeyDef(
    val label: String,
    val code: Int = 0,
    val widthWeight: Float = 1f,
    val isSpecial: Boolean = false,
    val keyType: KeyType = KeyType.CHARACTER,
    val secondaryLabel: String? = null // Long-press character (shown as hint)
)

enum class KeyType {
    CHARACTER,
    SHIFT,
    BACKSPACE,
    ENTER,
    SPACE,
    SYMBOL_TOGGLE,
    SYMBOL_PAGE_TOGGLE,
    EMOJI_TOGGLE,
    LANGUAGE,
    ACTION_AI
}

enum class ShiftState {
    OFF,
    SINGLE,
    LOCKED
}

object KeyboardLayouts {

    private fun ch(c: Char): Int = c.code

    val qwertyRow1 = listOf(
        KeyDef("q", code = ch('q'), secondaryLabel = "1"),
        KeyDef("w", code = ch('w'), secondaryLabel = "2"),
        KeyDef("e", code = ch('e'), secondaryLabel = "3"),
        KeyDef("r", code = ch('r'), secondaryLabel = "4"),
        KeyDef("t", code = ch('t'), secondaryLabel = "5"),
        KeyDef("y", code = ch('y'), secondaryLabel = "6"),
        KeyDef("u", code = ch('u'), secondaryLabel = "7"),
        KeyDef("i", code = ch('i'), secondaryLabel = "8"),
        KeyDef("o", code = ch('o'), secondaryLabel = "9"),
        KeyDef("p", code = ch('p'), secondaryLabel = "0"),
    )

    val qwertyRow2 = listOf(
        KeyDef("a", code = ch('a'), secondaryLabel = "@"),
        KeyDef("s", code = ch('s'), secondaryLabel = "#"),
        KeyDef("d", code = ch('d'), secondaryLabel = "$"),
        KeyDef("f", code = ch('f'), secondaryLabel = "%"),
        KeyDef("g", code = ch('g'), secondaryLabel = "&"),
        KeyDef("h", code = ch('h'), secondaryLabel = "-"),
        KeyDef("j", code = ch('j'), secondaryLabel = "+"),
        KeyDef("k", code = ch('k'), secondaryLabel = "("),
        KeyDef("l", code = ch('l'), secondaryLabel = ")"),
    )

    val qwertyRow3 = listOf(
        KeyDef("⇧", code = 0, keyType = KeyType.SHIFT, isSpecial = true, widthWeight = 1.5f),
        KeyDef("z", code = ch('z'), secondaryLabel = "*"),
        KeyDef("x", code = ch('x'), secondaryLabel = "\""),
        KeyDef("c", code = ch('c'), secondaryLabel = "'"),
        KeyDef("v", code = ch('v'), secondaryLabel = ":"),
        KeyDef("b", code = ch('b'), secondaryLabel = ";"),
        KeyDef("n", code = ch('n'), secondaryLabel = "!"),
        KeyDef("m", code = ch('m'), secondaryLabel = "?"),
        KeyDef("⌫", code = 0, keyType = KeyType.BACKSPACE, isSpecial = true, widthWeight = 1.5f),
    )

    val bottomRow = listOf(
        KeyDef("123", code = 0, keyType = KeyType.SYMBOL_TOGGLE, isSpecial = true, widthWeight = 1.2f),
        KeyDef(",", code = ch(','), widthWeight = 0.9f),
        KeyDef("☺", code = 0, keyType = KeyType.EMOJI_TOGGLE, widthWeight = 0.9f),
        KeyDef(" ", code = ch(' '), keyType = KeyType.SPACE, widthWeight = 4.1f),
        KeyDef(".", code = ch('.'), widthWeight = 0.9f),
        KeyDef("↵", code = 0, keyType = KeyType.ENTER, isSpecial = true, widthWeight = 1.35f),
    )

    // Emoji pages
    val emojiPages: List<List<String>> = listOf(
        // Page 1: Smileys & People
        listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗",
            "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭",
            "🤫", "🤔", "🤐", "🤨", "😐", "😑", "😶", "😏",
            "😒", "🙄", "😬", "🤥", "😌", "😔", "😪", "🤤"
        ),
        // Page 2: Gestures & Hearts
        listOf(
            "👍", "👎", "👊", "✊", "🤛", "🤜", "👏", "🙌",
            "👐", "🤲", "🤝", "🙏", "✌️", "🤞", "🤟", "🤘",
            "👌", "🤌", "👈", "👉", "👆", "👇", "☝️", "✋",
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
            "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘"
        ),
        // Page 3: Objects & Symbols
        listOf(
            "🔥", "⭐", "🌟", "✨", "💫", "🎉", "🎊", "🎈",
            "💯", "💢", "💥", "💦", "💨", "🕳️", "💣", "💬",
            "👁️", "🗨️", "🗯️", "💭", "💤", "👋", "🤚", "🖐️",
            "✅", "❌", "⭕", "❗", "❓", "‼️", "⁉️", "💡",
            "🎵", "🎶", "🔔", "📌", "📍", "🏷️", "💰", "🎁"
        )
    )

    val symbolRow1 = listOf(
        KeyDef("1", code = ch('1')),
        KeyDef("2", code = ch('2')),
        KeyDef("3", code = ch('3')),
        KeyDef("4", code = ch('4')),
        KeyDef("5", code = ch('5')),
        KeyDef("6", code = ch('6')),
        KeyDef("7", code = ch('7')),
        KeyDef("8", code = ch('8')),
        KeyDef("9", code = ch('9')),
        KeyDef("0", code = ch('0')),
    )

    val symbolRow2 = listOf(
        KeyDef("@", code = ch('@')),
        KeyDef("#", code = ch('#')),
        KeyDef("$", code = ch('$')),
        KeyDef("%", code = ch('%')),
        KeyDef("&", code = ch('&')),
        KeyDef("-", code = ch('-')),
        KeyDef("+", code = ch('+')),
        KeyDef("(", code = ch('(')),
        KeyDef(")", code = ch(')')),
    )

    val symbolRow3 = listOf(
        KeyDef("=\\<", code = 0, keyType = KeyType.SYMBOL_PAGE_TOGGLE, isSpecial = true, widthWeight = 1.5f),
        KeyDef("*", code = ch('*')),
        KeyDef("\"", code = ch('"')),
        KeyDef("'", code = ch('\'')),
        KeyDef(":", code = ch(':')),
        KeyDef(";", code = ch(';')),
        KeyDef("!", code = ch('!')),
        KeyDef("?", code = ch('?')),
        KeyDef("⌫", code = 0, keyType = KeyType.BACKSPACE, isSpecial = true, widthWeight = 1.5f),
    )
    
    val symbol2Row1 = listOf(
        KeyDef("~", code = ch('~')),
        KeyDef("`", code = ch('`')),
        KeyDef("|", code = ch('|')),
        KeyDef("•", code = ch('•')),
        KeyDef("√", code = ch('√')),
        KeyDef("π", code = ch('π')),
        KeyDef("÷", code = ch('÷')),
        KeyDef("×", code = ch('×')),
        KeyDef("¶", code = ch('π')), // Just filling up
        KeyDef("∆", code = ch('∆')),
    )

    val symbol2Row2 = listOf(
        KeyDef("€", code = ch('€')),
        KeyDef("£", code = ch('£')),
        KeyDef("¥", code = ch('¥')),
        KeyDef("¢", code = ch('¢')),
        KeyDef("°", code = ch('°')),
        KeyDef("©", code = ch('©')),
        KeyDef("®", code = ch('®')),
        KeyDef("™", code = ch('™')),
        KeyDef("✓", code = ch('✓')),
    )

    val symbol2Row3 = listOf(
        KeyDef("?123", code = 0, keyType = KeyType.SYMBOL_PAGE_TOGGLE, isSpecial = true, widthWeight = 1.5f),
        KeyDef("[", code = ch('[')),
        KeyDef("]", code = ch(']')),
        KeyDef("{", code = ch('{')),
        KeyDef("}", code = ch('}')),
        KeyDef("<", code = ch('<')),
        KeyDef(">", code = ch('>')),
        KeyDef("^", code = ch('^')),
        KeyDef("⌫", code = 0, keyType = KeyType.BACKSPACE, isSpecial = true, widthWeight = 1.5f),
    )

    fun getQwertyRows(shiftState: ShiftState): List<List<KeyDef>> {
        return listOf(
            applyShift(qwertyRow1, shiftState),
            applyShift(qwertyRow2, shiftState),
            qwertyRow3,
            bottomRow
        )
    }

    fun getSymbolRows(): List<List<KeyDef>> {
        return listOf(symbolRow1, symbolRow2, symbolRow3, bottomRow)
    }
    
    fun getSymbol2Rows(): List<List<KeyDef>> {
        return listOf(symbol2Row1, symbol2Row2, symbol2Row3, bottomRow)
    }

    private fun applyShift(row: List<KeyDef>, shiftState: ShiftState): List<KeyDef> {
        if (shiftState == ShiftState.OFF) return row
        return row.map { key ->
            if (key.keyType == KeyType.CHARACTER && key.code != 0) {
                key.copy(label = key.label.uppercase())
            } else {
                key
            }
        }
    }
}
