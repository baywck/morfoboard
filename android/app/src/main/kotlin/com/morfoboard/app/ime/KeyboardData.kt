package com.morfoboard.app.ime

/**
 * Data model for keyboard keys and layouts.
 */
data class KeyDef(
    val label: String,
    val code: Int = 0,
    val widthWeight: Float = 1f,
    val isSpecial: Boolean = false,
    val keyType: KeyType = KeyType.CHARACTER
)

enum class KeyType {
    CHARACTER,
    SHIFT,
    BACKSPACE,
    ENTER,
    SPACE,
    SYMBOL_TOGGLE,
    SYMBOL_PAGE_TOGGLE,
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
        KeyDef("q", code = ch('q')),
        KeyDef("w", code = ch('w')),
        KeyDef("e", code = ch('e')),
        KeyDef("r", code = ch('r')),
        KeyDef("t", code = ch('t')),
        KeyDef("y", code = ch('y')),
        KeyDef("u", code = ch('u')),
        KeyDef("i", code = ch('i')),
        KeyDef("o", code = ch('o')),
        KeyDef("p", code = ch('p')),
    )

    val qwertyRow2 = listOf(
        KeyDef("a", code = ch('a')),
        KeyDef("s", code = ch('s')),
        KeyDef("d", code = ch('d')),
        KeyDef("f", code = ch('f')),
        KeyDef("g", code = ch('g')),
        KeyDef("h", code = ch('h')),
        KeyDef("j", code = ch('j')),
        KeyDef("k", code = ch('k')),
        KeyDef("l", code = ch('l')),
    )

    val qwertyRow3 = listOf(
        KeyDef("⇧", code = 0, keyType = KeyType.SHIFT, isSpecial = true, widthWeight = 1.5f),
        KeyDef("z", code = ch('z')),
        KeyDef("x", code = ch('x')),
        KeyDef("c", code = ch('c')),
        KeyDef("v", code = ch('v')),
        KeyDef("b", code = ch('b')),
        KeyDef("n", code = ch('n')),
        KeyDef("m", code = ch('m')),
        KeyDef("⌫", code = 0, keyType = KeyType.BACKSPACE, isSpecial = true, widthWeight = 1.5f),
    )

    val bottomRow = listOf(
        KeyDef("123", code = 0, keyType = KeyType.SYMBOL_TOGGLE, isSpecial = true, widthWeight = 1.25f),
        KeyDef(",", code = ch(','), widthWeight = 1.05f),
        KeyDef(" ", code = ch(' '), keyType = KeyType.SPACE, widthWeight = 4.8f),
        KeyDef(".", code = ch('.'), widthWeight = 0.9f),
        KeyDef("↵", code = 0, keyType = KeyType.ENTER, isSpecial = true, widthWeight = 1.35f),
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
