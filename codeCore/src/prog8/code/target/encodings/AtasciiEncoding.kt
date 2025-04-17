package prog8.code.target.encodings

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.io.CharConversionException

object AtasciiEncoding {

    private val decodeTable: CharArray = charArrayOf(
        // $00
        '♥',
        '├',
        '\uf130',    //  🮇    0x02 -> RIGHT ONE QUARTER BLOCK (CUS)
        '┘',
        '┤',
        '┐',
        '╱',
        '╲',
        '◢',
        '▗',
        '◣',
        '▝',
        '▘',
        '\uf132',    //  🮂    0x1d -> UPPER ONE QUARTER BLOCK (CUS)
        '▂',
        '▖',

        // $10
        '♣',
        '┌',
        '─',
        '┼',
        '•',
        '▄',
        '▎',
        '┬',
        '┴',
        '▌',
        '└',
        '\u001b',           // $1b = escape
        '\ufffe',           // UNDEFINED CHAR.   $1c = cursor up
        '\ufffe',           // UNDEFINED CHAR.   $1d = cursor down
        '\ufffe',           // UNDEFINED CHAR.   $1e = cursor left
        '\ufffe',           // UNDEFINED CHAR.   $1f = cursor right

        // $20
        ' ',
        '!',
        '"',
        '#',
        '$',
        '%',
        '&',
        '\'',
        '(',
        ')',
        '*',
        '+',
        ',',
        '-',
        '.',
        '/',

        // $30
        '0',
        '1',
        '2',
        '3',
        '4',
        '5',
        '6',
        '7',
        '8',
        '9',
        ':',
        ';',
        '<',
        '=',
        '>',
        '?',

        // $40
        '@',
        'A',
        'B',
        'C',
        'D',
        'E',
        'F',
        'G',
        'H',
        'I',
        'J',
        'K',
        'L',
        'M',
        'N',
        'O',

        // $50
        'P',
        'Q',
        'R',
        'S',
        'T',
        'U',
        'V',
        'W',
        'X',
        'Y',
        'Z',
        '[',
        '\\',
        ']',
        '^',
        '_',

        // $60
        '♦',
        'a',
        'b',
        'c',
        'd',
        'e',
        'f',
        'g',
        'h',
        'i',
        'j',
        'k',
        'l',
        'm',
        'n',
        'o',

        // $70
        'p',
        'q',
        'r',
        's',
        't',
        'u',
        'v',
        'w',
        'x',
        'y',
        'z',
        '♠',
        '|',
        '\u000c',    //       $7d -> FORM FEED (CLEAR SCREEN)
        '\u0008',    //       $7e -> BACKSPACE
        '\u0009',    //       $7f -> TAB

        // $80-$ff are reversed video characters + various special characters.
        '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe',
        '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe',
        // $90
        '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe',
        '\ufffe',
        '\ufffe',
        '\ufffe',
        '\n',               // $9b -> EOL/RETURN
        '\ufffe',           // UNDEFINED   $9c = DELETE LINE
        '\ufffe',           // UNDEFINED   $9d = INSERT LINE
        '\ufffe',           // UNDEFINED   $9e = CLEAR TAB STOP
        '\ufffe',           // UNDEFINED   $9f = SET TAB STOP
        // $a0
        '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe',
        '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe',
        // $b0
        '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe',
        '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe',
        // $c0
        '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe',
        '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe',
        // $d0
        '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe',
        '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe',
        // $e0
        '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe',
        '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe',
        // $f0
        '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe', '\ufffe',
        '\ufffe',
        '\ufffe',
        '\ufffe',
        '\ufffe',
        '\ufffe',
        '\u0007',           // $fd = bell/beep
        '\u007f',           // $fe = DELETE
        '\ufffe'            // UNDEFINED     $ff = INSERT
    )

    private val encodeTable = decodeTable.withIndex().associate{it.value to it.index}


    fun encode(str: String): Result<List<UByte>, CharConversionException> {
        val mapped = str.map { chr ->
            when (chr) {
                '\u0000' -> 0u
                in '\u8000'..'\u80ff' -> {
                    // special case: take the lower 8 bit hex value directly
                    (chr.code - 0x8000).toUByte()
                }
                else -> encodeTable.getValue(chr).toUByte()
            }
        }
        return Ok(mapped)
    }

    fun decode(bytes: Iterable<UByte>): Result<String, CharConversionException> {
        return Ok(bytes.map { decodeTable[it.toInt()] }.joinToString(""))
    }
}
