package prog8.code.target.encodings

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.io.CharConversionException

object C64osEncoding {

    // decoding:  from C64 OS Screencodes (0-255) to unicode
    // character table from:
    // https://www.c64os.com/c64os/usersguide/appendices#charactersets

    private val decodingC64os = charArrayOf(
        '@'     ,    //  @    0x00 -> COMMERCIAL AT
        'a'     ,    //  a    0x01 -> LATIN SMALL LETTER A
        'b'     ,    //  b    0x02 -> LATIN SMALL LETTER B
        'c'     ,    //  c    0x03 -> LATIN SMALL LETTER C
        'd'     ,    //  d    0x04 -> LATIN SMALL LETTER D
        'e'     ,    //  e    0x05 -> LATIN SMALL LETTER E
        'f'     ,    //  f    0x06 -> LATIN SMALL LETTER F
        'g'     ,    //  g    0x07 -> LATIN SMALL LETTER G
        'h'     ,    //  h    0x08 -> LATIN SMALL LETTER H
        'i'     ,    //  i    0x09 -> LATIN SMALL LETTER I
        'j'     ,    //  j    0x0A -> LATIN SMALL LETTER J
        'k'     ,    //  k    0x0B -> LATIN SMALL LETTER K
        'l'     ,    //  l    0x0C -> LATIN SMALL LETTER L
        'm'     ,    //  m    0x0D -> LATIN SMALL LETTER M
        'n'     ,    //  n    0x0E -> LATIN SMALL LETTER N
        'o'     ,    //  o    0x0F -> LATIN SMALL LETTER O
        'p'     ,    //  p    0x10 -> LATIN SMALL LETTER P
        'q'     ,    //  q    0x11 -> LATIN SMALL LETTER Q
        'r'     ,    //  r    0x12 -> LATIN SMALL LETTER R
        's'     ,    //  s    0x13 -> LATIN SMALL LETTER S
        't'     ,    //  t    0x14 -> LATIN SMALL LETTER T
        'u'     ,    //  u    0x15 -> LATIN SMALL LETTER U
        'v'     ,    //  v    0x16 -> LATIN SMALL LETTER V
        'w'     ,    //  w    0x17 -> LATIN SMALL LETTER W
        'x'     ,    //  x    0x18 -> LATIN SMALL LETTER X
        'y'     ,    //  y    0x19 -> LATIN SMALL LETTER Y
        'z'     ,    //  z    0x1A -> LATIN SMALL LETTER Z
        '['     ,    //  [    0x1B -> LEFT SQUARE BRACKET
        '\\'    ,    //  \    0x1C -> REVERSE SOLIDUS
        ']'     ,    //  ]    0x1D -> RIGHT SQUARE BRACKET
        '^'     ,    //  ^    0x1E -> CIRCUMFLEX
        '_'     ,    //  _    0x1F -> UNDERSCORE
        ' '     ,    //       0x20 -> SPACE
        '!'     ,    //  !    0x21 -> EXCLAMATION MARK
        '"'     ,    //  "    0x22 -> QUOTATION MARK
        '#'     ,    //  #    0x23 -> NUMBER SIGN
        '$'     ,    //  $    0x24 -> DOLLAR SIGN
        '%'     ,    //  %    0x25 -> PERCENT SIGN
        '&'     ,    //  &    0x26 -> AMPERSAND
        '\''    ,    //  '    0x27 -> APOSTROPHE
        '('     ,    //  (    0x28 -> LEFT PARENTHESIS
        ')'     ,    //  )    0x29 -> RIGHT PARENTHESIS
        '*'     ,    //  *    0x2A -> ASTERISK
        '+'     ,    //  +    0x2B -> PLUS SIGN
        ','     ,    //  ,    0x2C -> COMMA
        '-'     ,    //  -    0x2D -> HYPHEN-MINUS
        '.'     ,    //  .    0x2E -> FULL STOP
        '/'     ,    //  /    0x2F -> SOLIDUS
        '0'     ,    //  0    0x30 -> DIGIT ZERO
        '1'     ,    //  1    0x31 -> DIGIT ONE
        '2'     ,    //  2    0x32 -> DIGIT TWO
        '3'     ,    //  3    0x33 -> DIGIT THREE
        '4'     ,    //  4    0x34 -> DIGIT FOUR
        '5'     ,    //  5    0x35 -> DIGIT FIVE
        '6'     ,    //  6    0x36 -> DIGIT SIX
        '7'     ,    //  7    0x37 -> DIGIT SEVEN
        '8'     ,    //  8    0x38 -> DIGIT EIGHT
        '9'     ,    //  9    0x39 -> DIGIT NINE
        ':'     ,    //  :    0x3A -> COLON
        ';'     ,    //  ;    0x3B -> SEMICOLON
        '<'     ,    //  <    0x3C -> LESS-THAN SIGN
        '='     ,    //  =    0x3D -> EQUALS SIGN
        '>'     ,    //  >    0x3E -> GREATER-THAN SIGN
        '?'     ,    //  ?    0x3F -> QUESTION MARK
        '`'     ,    //  `    0x40 -> GRAVE ACCENT
        'A'     ,    //  A    0x41 -> LATIN CAPITAL LETTER A
        'B'     ,    //  B    0x42 -> LATIN CAPITAL LETTER B
        'C'     ,    //  C    0x43 -> LATIN CAPITAL LETTER C
        'D'     ,    //  D    0x44 -> LATIN CAPITAL LETTER D
        'E'     ,    //  E    0x45 -> LATIN CAPITAL LETTER E
        'F'     ,    //  F    0x46 -> LATIN CAPITAL LETTER F
        'G'     ,    //  G    0x47 -> LATIN CAPITAL LETTER G
        'H'     ,    //  H    0x48 -> LATIN CAPITAL LETTER H
        'I'     ,    //  I    0x49 -> LATIN CAPITAL LETTER I
        'J'     ,    //  J    0x4A -> LATIN CAPITAL LETTER J
        'K'     ,    //  K    0x4B -> LATIN CAPITAL LETTER K
        'L'     ,    //  L    0x4C -> LATIN CAPITAL LETTER L
        'M'     ,    //  M    0x4D -> LATIN CAPITAL LETTER M
        'N'     ,    //  N    0x4E -> LATIN CAPITAL LETTER N
        'O'     ,    //  O    0x4F -> LATIN CAPITAL LETTER O
        'P'     ,    //  P    0x50 -> LATIN CAPITAL LETTER P
        'Q'     ,    //  Q    0x51 -> LATIN CAPITAL LETTER Q
        'R'     ,    //  R    0x52 -> LATIN CAPITAL LETTER R
        'S'     ,    //  S    0x53 -> LATIN CAPITAL LETTER S
        'T'     ,    //  T    0x54 -> LATIN CAPITAL LETTER T
        'U'     ,    //  U    0x55 -> LATIN CAPITAL LETTER U
        'V'     ,    //  V    0x56 -> LATIN CAPITAL LETTER V
        'W'     ,    //  W    0x57 -> LATIN CAPITAL LETTER W
        'X'     ,    //  X    0x58 -> LATIN CAPITAL LETTER X
        'Y'     ,    //  Y    0x59 -> LATIN CAPITAL LETTER Y
        'Z'     ,    //  Z    0x5A -> LATIN CAPITAL LETTER Z
        '{'     ,    //  {    0x5B -> LEFT BRACE
        '|'     ,    //  |    0x5C -> VERTICAL BAR
        '}'     ,    //  }    0x5D -> RIGHT BRACE
        '~'     ,    //  ~    0x5E -> TILDE
        '\ufffe',    //       0x5F -> RESERVED
        '\u00a0',    //       0x60 -> NO-BREAK SPACE (TRANSPARENT)
        '\ufffe',    //       0x61 -> COMMODORE SYMBOL
        '\u2191',    //  ↑    0x62 -> UP ARROW
        '\u2193',    //  ↓    0x63 -> DOWN ARROW
        '\u2190',    //  ←    0x64 -> LEFT ARROW
        '\u2192',    //  →    0x65 -> RIGHT ARROW
        '\u231A',    //  ⌚   0x66 -> WATCH (ANALOG CLOCKFACE)
        '\u21BB',    //  ↻    0x67 -> CYCLE ARROWS
        '\u2026',    //  …    0x68 -> ELLIPSIS
        '\u25a7',    //  ▧    0x69 -> DIAGNONAL STRIPES
        '\u2610',    //  ☐    0x6A -> CHECKBOX UNCHECKED
        '\u2611',    //  ☑    0x6B -> CHECKBOX CHECKED
        '\ufffe',    //       0x6C -> RADIO BUTTON UNSELECTED
        '\ufffe',    //       0x6D -> RADIO BUTTON SELECTED
        '\ufffe',    //       0x6E -> UTILITY CLOSE BUTTON
        '\ufffe',    //       0x6F -> UTILITY TITLE BAR
        '\u00a9',    //  ©    0x70 -> COPYRIGHT
        '\u2713',    //  ✓    0x71 -> CHECKMARK
        '\u2261',    //  ≡    0x72 -> THREE HORIZONTAL STRIPES
        '\ufffe',    //       0x73 -> TICK TRACK
        '\ufffe',    //       0x74 -> TICK TRACK NUB
        '\ufffe',    //       0x75 -> TAB CORNER
        '\u2980',    //  ⦀    0x76 -> THREE VERTICAL STRIPES
        '\ufffe',    //       0x77 -> CUSTOM 1
        '\ufffe',    //       0x78 -> CUSTOM 2
        '\ufffe',    //       0x79 -> CUSTOM 3
        '\ufffe',    //       0x7A -> CUSTOM 4
        '\ufffe',    //       0x7B -> CUSTOM 5
        '\ufffe',    //       0x7C -> CUSTOM 6
        '\ufffe',    //       0x7D -> CUSTOM 7
        '\ufffe',    //       0x7E -> CUSTOM 8
        '\ufffe',    //       0x7F -> CUSTOM 9
        '\ufffe',    //       0x80 -> REVERSED COMMERCIAL AT
        '\ufffe',    //       0x81 -> REVERSED LATIN SMALL LETTER A
        '\ufffe',    //       0x82 -> REVERSED LATIN SMALL LETTER B
        '\ufffe',    //       0x83 -> REVERSED LATIN SMALL LETTER C
        '\ufffe',    //       0x84 -> REVERSED LATIN SMALL LETTER D
        '\ufffe',    //       0x85 -> REVERSED LATIN SMALL LETTER E
        '\ufffe',    //       0x86 -> REVERSED LATIN SMALL LETTER F
        '\ufffe',    //       0x87 -> REVERSED LATIN SMALL LETTER G
        '\ufffe',    //       0x88 -> REVERSED LATIN SMALL LETTER H
        '\ufffe',    //       0x89 -> REVERSED LATIN SMALL LETTER I
        '\ufffe',    //       0x8A -> REVERSED LATIN SMALL LETTER J
        '\ufffe',    //       0x8B -> REVERSED LATIN SMALL LETTER K
        '\ufffe',    //       0x8C -> REVERSED LATIN SMALL LETTER L
        '\ufffe',    //       0x8D -> REVERSED LATIN SMALL LETTER M
        '\ufffe',    //       0x8E -> REVERSED LATIN SMALL LETTER N
        '\ufffe',    //       0x8F -> REVERSED LATIN SMALL LETTER O
        '\ufffe',    //       0x90 -> REVERSED LATIN SMALL LETTER P
        '\ufffe',    //       0x91 -> REVERSED LATIN SMALL LETTER Q
        '\ufffe',    //       0x92 -> REVERSED LATIN SMALL LETTER R
        '\ufffe',    //       0x93 -> REVERSED LATIN SMALL LETTER S
        '\ufffe',    //       0x94 -> REVERSED LATIN SMALL LETTER T
        '\ufffe',    //       0x95 -> REVERSED LATIN SMALL LETTER U
        '\ufffe',    //       0x96 -> REVERSED LATIN SMALL LETTER V
        '\ufffe',    //       0x97 -> REVERSED LATIN SMALL LETTER W
        '\ufffe',    //       0x98 -> REVERSED LATIN SMALL LETTER X
        '\ufffe',    //       0x99 -> REVERSED LATIN SMALL LETTER Y
        '\ufffe',    //       0x9A -> REVERSED LATIN SMALL LETTER Z
        '\ufffe',    //       0x9B -> REVERSED LEFT SQUARE BRACKET
        '\ufffe',    //       0x9C -> REVERSED REVERSE SOLIDUS
        '\ufffe',    //       0x9D -> REVERSED RIGHT SQUARE BRACKET
        '\ufffe',    //       0x9E -> REVERSED CIRCUMFLEX
        '\ufffe',    //       0x9F -> REVERSED UNDERSCORE
        '\ufffe',    //       0xA0 -> REVERSED SPACE
        '\ufffe',    //       0xA1 -> REVERSED EXCLAMATION MARK
        '\ufffe',    //       0xA2 -> REVERSED QUOTATION MARK
        '\ufffe',    //       0xA3 -> REVERSED NUMBER SIGN
        '\ufffe',    //       0xA4 -> REVERSED DOLLAR SIGN
        '\ufffe',    //       0xA5 -> REVERSED PERCENT SIGN
        '\ufffe',    //       0xA6 -> REVERSED AMPERSAND
        '\ufffe',    //       0xA7 -> REVERSED APOSTROPHE
        '\ufffe',    //       0xA8 -> REVERSED LEFT PARENTHESIS
        '\ufffe',    //       0xA9 -> REVERSED RIGHT PARENTHESIS
        '\ufffe',    //       0xAA -> REVERSED ASTERISK
        '\ufffe',    //       0xAB -> REVERSED PLUS SIGN
        '\ufffe',    //       0xAC -> REVERSED COMMA
        '\ufffe',    //       0xAD -> REVERSED HYPHEN-MINUS
        '\ufffe',    //       0xAE -> REVERSED FULL STOP
        '\ufffe',    //       0xAF -> REVERSED SOLIDUS
        '\ufffe',    //       0xB0 -> REVERSED DIGIT ZERO
        '\ufffe',    //       0xB1 -> REVERSED DIGIT ONE
        '\ufffe',    //       0xB2 -> REVERSED DIGIT TWO
        '\ufffe',    //       0xB3 -> REVERSED DIGIT THREE
        '\ufffe',    //       0xB4 -> REVERSED DIGIT FOUR
        '\ufffe',    //       0xB5 -> REVERSED DIGIT FIVE
        '\ufffe',    //       0xB6 -> REVERSED DIGIT SIX
        '\ufffe',    //       0xB7 -> REVERSED DIGIT SEVEN
        '\ufffe',    //       0xB8 -> REVERSED DIGIT EIGHT
        '\ufffe',    //       0xB9 -> REVERSED DIGIT NINE
        '\ufffe',    //       0xBA -> REVERSED COLON
        '\ufffe',    //       0xBB -> REVERSED SEMICOLON
        '\ufffe',    //       0xBC -> REVERSED LESS-THAN SIGN
        '\ufffe',    //       0xBD -> REVERSED EQUALS SIGN
        '\ufffe',    //       0xBE -> REVERSED GREATER-THAN SIGN
        '\ufffe',    //       0xBF -> REVERSED QUESTION MARK
        '\ufffe',    //       0xC0 -> REVERSED GRAVE ACCENT
        '\ufffe',    //       0xC1 -> REVERSED LATIN CAPITAL LETTER A
        '\ufffe',    //       0xC2 -> REVERSED LATIN CAPITAL LETTER B
        '\ufffe',    //       0xC3 -> REVERSED LATIN CAPITAL LETTER C
        '\ufffe',    //       0xC4 -> REVERSED LATIN CAPITAL LETTER D
        '\ufffe',    //       0xC5 -> REVERSED LATIN CAPITAL LETTER E
        '\ufffe',    //       0xC6 -> REVERSED LATIN CAPITAL LETTER F
        '\ufffe',    //       0xC7 -> REVERSED LATIN CAPITAL LETTER G
        '\ufffe',    //       0xC8 -> REVERSED LATIN CAPITAL LETTER H
        '\ufffe',    //       0xC9 -> REVERSED LATIN CAPITAL LETTER I
        '\ufffe',    //       0xCA -> REVERSED LATIN CAPITAL LETTER J
        '\ufffe',    //       0xCB -> REVERSED LATIN CAPITAL LETTER K
        '\ufffe',    //       0xCC -> REVERSED LATIN CAPITAL LETTER L
        '\ufffe',    //       0xCD -> REVERSED LATIN CAPITAL LETTER M
        '\ufffe',    //       0xCE -> REVERSED LATIN CAPITAL LETTER N
        '\ufffe',    //       0xCF -> REVERSED LATIN CAPITAL LETTER O
        '\ufffe',    //       0xD0 -> REVERSED LATIN CAPITAL LETTER P
        '\ufffe',    //       0xD1 -> REVERSED LATIN CAPITAL LETTER Q
        '\ufffe',    //       0xD2 -> REVERSED LATIN CAPITAL LETTER R
        '\ufffe',    //       0xD3 -> REVERSED LATIN CAPITAL LETTER S
        '\ufffe',    //       0xD4 -> REVERSED LATIN CAPITAL LETTER T
        '\ufffe',    //       0xD5 -> REVERSED LATIN CAPITAL LETTER U
        '\ufffe',    //       0xD6 -> REVERSED LATIN CAPITAL LETTER V
        '\ufffe',    //       0xD7 -> REVERSED LATIN CAPITAL LETTER W
        '\ufffe',    //       0xD8 -> REVERSED LATIN CAPITAL LETTER X
        '\ufffe',    //       0xD9 -> REVERSED LATIN CAPITAL LETTER Y
        '\ufffe',    //       0xDA -> REVERSED LATIN CAPITAL LETTER Z
        '\ufffe',    //       0xDB -> REVERSED LEFT BRACE
        '\ufffe',    //       0xDC -> REVERSED VERTICAL BAR
        '\ufffe',    //       0xDD -> REVERSED RIGHT BRACE
        '\ufffe',    //       0xDE -> REVERSED TILDE
        '\ufffe',    //       0xDF -> RESERVED
        '\ufffe',    //       0xE0 -> RESERVED
        '\ufffe',    //       0xE1 -> REVERSED COMMODORE SYMBOL
        '\ufffe',    //       0xE2 -> REVERSED UP ARROW
        '\ufffe',    //       0xE3 -> REVERSED DOWN ARROW
        '\ufffe',    //       0xE4 -> REVERSED LEFT ARROW
        '\ufffe',    //       0xE5 -> REVERSED RIGHT ARROW
        '\ufffe',    //       0xE6 -> REVERSED ANALOG CLOCKFACE
        '\ufffe',    //       0xE7 -> REVERSED CYCLE ARROWS
        '\ufffe',    //       0xE8 -> REVERSED ELLIPSIS
        '\ufffe',    //       0xE9 -> REVERSED DIAGONAL STRIPES
        '\ufffe',    //       0xEA -> REVERSED CHECKBOX UNCHECKED
        '\ufffe',    //       0xEB -> REVERSED CHECKBOX CHECKED
        '\ufffe',    //       0xEC -> REVERSED RADIO BUTTON UNSELECTED
        '\ufffe',    //       0xED -> REVERSED RADIO BUTTON SELECTED
        '\ufffe',    //       0xEE -> MEMORY CHIP ICON
        '\u21e7',    //  ⇧    0xEF -> SHIFT SYMBOL
        '\ufffe',    //       0xF0 -> REVERSED COPYRIGHT SYMBOL
        '\ufffe',    //       0xF1 -> REVERSED CHECKMARK
        '\ufffe',    //       0xF2 -> REVERSED THREE HORIZONTAL STRIPES
        '\ufffe',    //       0xF3 -> REVERSED TICK TRACK
        '\ufffe',    //       0xF4 -> REVERSED TICK TRACK NUB
        '\ufffe',    //       0xF5 -> REVERSED TAB CORNER
        '\ufffe',    //       0xF6 -> REVERSED THREE VERTICAL STRIPES
        '\ufffe',    //       0xF7 -> CUSTOM 10
        '\ufffe',    //       0xF8 -> CUSTOM 11
        '\ufffe',    //       0xF9 -> CUSTOM 12
        '\ufffe',    //       0xFA -> CUSTOM 13
        '\ufffe',    //       0xFB -> CUSTOM 14
        '\ufffe',    //       0xFC -> CUSTOM 15
        '\ufffe',    //       0xFD -> CUSTOM 16
        '\ufffe',    //       0xFE -> CUSTOM 17
        '\ufffe'     //       0xFF -> CUSTOM 18
    )

    // encoding:  from unicode to C64 OS Screencodes (0-255)
    private val encodingC64os = decodingC64os.withIndex().associate{it.value to it.index}

    private fun replaceSpecial(chr: Char): Char =
        when(chr) {
            '\r' -> '\n'        // to make \r (carriage returrn) equivalent to \n (line feed): RETURN ($0d)
            else -> chr
        }
    
    fun encode(text: String, lowercase: Boolean = false): Result<List<UByte>, CharConversionException> {
        fun encodeChar(chr3: Char, lowercase: Boolean): UByte {
            val chr = replaceSpecial(chr3)
            val screencode = encodingC64os[chr]
            return screencode?.toUByte() ?: when (chr) {
                '\u0000' -> 0u
                in '\u8000'..'\u80ff' -> {
                    // special case: take the lower 8 bit hex value directly
                    (chr.code - 0x8000).toUByte()
                }
                else -> {
                    if(chr.isISOControl())
                        throw CharConversionException("no c64os character for char #${chr.code}")
                    else
                        throw CharConversionException("no c64os character for char #${chr.code} '${chr}'")
                }
            }
        }

        return try {
            Ok(text.map {
                try {
                    encodeChar(it, lowercase)
                } catch (x: CharConversionException) {
                    encodeChar(it, !lowercase)
                }
            })
        } catch(cx: CharConversionException) {
            Err(cx)
        }
    }

    fun decode(screencode: Iterable<UByte>, lowercase: Boolean = false): Result<String, CharConversionException> {
        return try {
            Ok(screencode.map {
                val code = it.toInt()
                if(code<0 || code>= decodingC64os.size)
                    throw CharConversionException("c64os $code out of range 0..${decodingC64os.size-1}")
                decodingC64os[code]
            }.joinToString(""))
        } catch(ce: CharConversionException) {
            Err(ce)
        }
    }
}
