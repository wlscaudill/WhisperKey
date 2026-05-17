package com.whisperkey.ui

data class KeyboardProfile(
    val id: String,
    val displayName: String,
    val letterRows: List<List<String>>,
    val symbolPage1Rows: List<List<String>>,
    val symbolPage2Rows: List<List<String>>
)

object KeyboardProfiles {

    private val sharedLetterRows = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m")
    )

    private val defaultSymbolPage1 = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/"),
        listOf("*", "\"", "'", ":", ";", "!", "?")
    )

    private val defaultSymbolPage2 = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆"),
        listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\"),
        listOf("%", "©", "®", "™", "✓", "[", "]")
    )

    val DEFAULT = KeyboardProfile(
        id = "default",
        displayName = "Default",
        letterRows = sharedLetterRows,
        symbolPage1Rows = defaultSymbolPage1,
        symbolPage2Rows = defaultSymbolPage2
    )

    val DEVELOPER = KeyboardProfile(
        id = "developer",
        displayName = "Developer",
        letterRows = sharedLetterRows,
        symbolPage1Rows = defaultSymbolPage1,
        symbolPage2Rows = listOf(
            listOf("[", "]", "{", "}", "<", ">", "\\", "|", "/", "*"),
            listOf("_", "`", "~", "^", "=", "+", "-", "→", "←"),
            listOf("#", "%", "°", "±", "×", "÷", "!")
        )
    )

    val WRITER = KeyboardProfile(
        id = "writer",
        displayName = "Writer",
        letterRows = sharedLetterRows,
        symbolPage1Rows = listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/"),
            listOf("—", "“", "”", "‘", "’", "!", "?")
        ),
        symbolPage2Rows = listOf(
            listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆"),
            listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\"),
            listOf("\"", "'", "©", "®", "™", "[", "]")
        )
    )

    val ALL = listOf(DEFAULT, DEVELOPER, WRITER)

    fun byId(id: String?): KeyboardProfile = ALL.find { it.id == id } ?: DEFAULT
}
