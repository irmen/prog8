package prog8.code.target.encodings

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.io.CharConversionException

object PetsciiEncoding {

    // decoding:  from Petscii/Screencodes (0-255) to Unicode
    // character tables used from https://github.com/irmen/cbmcodecs2

    private val decodingPetsciiLowercase = charArrayOf(
        '\u0000',    //       0x00 -> \u0000
        '\ufffe',    //       0x01 -> UNDEFINED
        '\ufffe',    //       0x02 -> UNDEFINED
        '\ufffe',    //       0x03 -> UNDEFINED
        '\ufffe',    //       0x04 -> UNDEFINED
        '\uf100',    //       0x05 -> WHITE COLOR SWITCH (CUS)
        '\ufffe',    //       0x06 -> UNDEFINED
        '\ufffe',    //       0x07 -> UNDEFINED
        '\uf118',    //       0x08 -> DISABLE CHARACTER SET SWITCHING (CUS)
        '\uf119',    //       0x09 -> ENABLE CHARACTER SET SWITCHING (CUS)
        '\ufffe',    //       0x0A -> UNDEFINED
        '\ufffe',    //       0x0B -> UNDEFINED
        '\ufffe',    //       0x0C -> UNDEFINED
        '\n'    ,    //       0x0D -> LINE FEED (RETURN)
        '\u000e',    //       0x0E -> SHIFT OUT
        '\ufffe',    //       0x0F -> UNDEFINED
        '\ufffe',    //       0x10 -> UNDEFINED
        '\uf11c',    //       0x11 -> CURSOR DOWN (CUS)
        '\uf11a',    //       0x12 -> REVERSE VIDEO ON (CUS)
        '\uf120',    //       0x13 -> HOME (CUS)
        '\u007f',    //       0x14 -> DELETE
        '\ufffe',    //       0x15 -> UNDEFINED
        '\ufffe',    //       0x16 -> UNDEFINED
        '\ufffe',    //       0x17 -> UNDEFINED
        '\ufffe',    //       0x18 -> UNDEFINED
        '\ufffe',    //       0x19 -> UNDEFINED
        '\ufffe',    //       0x1A -> UNDEFINED
        '\ufffe',    //       0x1B -> UNDEFINED
        '\uf101',    //       0x1C -> RED COLOR SWITCH (CUS)
        '\uf11d',    //       0x1D -> CURSOR RIGHT (CUS)
        '\uf102',    //       0x1E -> GREEN COLOR SWITCH (CUS)
        '\uf103',    //       0x1F -> BLUE COLOR SWITCH (CUS)
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
        '@'     ,    //  @    0x40 -> COMMERCIAL AT
        'a'     ,    //  a    0x41 -> LATIN SMALL LETTER A
        'b'     ,    //  b    0x42 -> LATIN SMALL LETTER B
        'c'     ,    //  c    0x43 -> LATIN SMALL LETTER C
        'd'     ,    //  d    0x44 -> LATIN SMALL LETTER D
        'e'     ,    //  e    0x45 -> LATIN SMALL LETTER E
        'f'     ,    //  f    0x46 -> LATIN SMALL LETTER F
        'g'     ,    //  g    0x47 -> LATIN SMALL LETTER G
        'h'     ,    //  h    0x48 -> LATIN SMALL LETTER H
        'i'     ,    //  i    0x49 -> LATIN SMALL LETTER I
        'j'     ,    //  j    0x4A -> LATIN SMALL LETTER J
        'k'     ,    //  k    0x4B -> LATIN SMALL LETTER K
        'l'     ,    //  l    0x4C -> LATIN SMALL LETTER L
        'm'     ,    //  m    0x4D -> LATIN SMALL LETTER M
        'n'     ,    //  n    0x4E -> LATIN SMALL LETTER N
        'o'     ,    //  o    0x4F -> LATIN SMALL LETTER O
        'p'     ,    //  p    0x50 -> LATIN SMALL LETTER P
        'q'     ,    //  q    0x51 -> LATIN SMALL LETTER Q
        'r'     ,    //  r    0x52 -> LATIN SMALL LETTER R
        's'     ,    //  s    0x53 -> LATIN SMALL LETTER S
        't'     ,    //  t    0x54 -> LATIN SMALL LETTER T
        'u'     ,    //  u    0x55 -> LATIN SMALL LETTER U
        'v'     ,    //  v    0x56 -> LATIN SMALL LETTER V
        'w'     ,    //  w    0x57 -> LATIN SMALL LETTER W
        'x'     ,    //  x    0x58 -> LATIN SMALL LETTER X
        'y'     ,    //  y    0x59 -> LATIN SMALL LETTER Y
        'z'     ,    //  z    0x5A -> LATIN SMALL LETTER Z
        '['     ,    //  [    0x5B -> LEFT SQUARE BRACKET
        '\u00a3',    //  £    0x5C -> POUND SIGN
        ']'     ,    //  ]    0x5D -> RIGHT SQUARE BRACKET
        '\u2191',    //  ↑    0x5E -> UPWARDS ARROW
        '\u2190',    //  ←    0x5F -> LEFTWARDS ARROW
        '\u2500',    //  ─    0x60 -> BOX DRAWINGS LIGHT HORIZONTAL
        'A'     ,    //  A    0x61 -> LATIN CAPITAL LETTER A
        'B'     ,    //  B    0x62 -> LATIN CAPITAL LETTER B
        'C'     ,    //  C    0x63 -> LATIN CAPITAL LETTER C
        'D'     ,    //  D    0x64 -> LATIN CAPITAL LETTER D
        'E'     ,    //  E    0x65 -> LATIN CAPITAL LETTER E
        'F'     ,    //  F    0x66 -> LATIN CAPITAL LETTER F
        'G'     ,    //  G    0x67 -> LATIN CAPITAL LETTER G
        'H'     ,    //  H    0x68 -> LATIN CAPITAL LETTER H
        'I'     ,    //  I    0x69 -> LATIN CAPITAL LETTER I
        'J'     ,    //  J    0x6A -> LATIN CAPITAL LETTER J
        'K'     ,    //  K    0x6B -> LATIN CAPITAL LETTER K
        'L'     ,    //  L    0x6C -> LATIN CAPITAL LETTER L
        'M'     ,    //  M    0x6D -> LATIN CAPITAL LETTER M
        'N'     ,    //  N    0x6E -> LATIN CAPITAL LETTER N
        'O'     ,    //  O    0x6F -> LATIN CAPITAL LETTER O
        'P'     ,    //  P    0x70 -> LATIN CAPITAL LETTER P
        'Q'     ,    //  Q    0x71 -> LATIN CAPITAL LETTER Q
        'R'     ,    //  R    0x72 -> LATIN CAPITAL LETTER R
        'S'     ,    //  S    0x73 -> LATIN CAPITAL LETTER S
        'T'     ,    //  T    0x74 -> LATIN CAPITAL LETTER T
        'U'     ,    //  U    0x75 -> LATIN CAPITAL LETTER U
        'V'     ,    //  V    0x76 -> LATIN CAPITAL LETTER V
        'W'     ,    //  W    0x77 -> LATIN CAPITAL LETTER W
        'X'     ,    //  X    0x78 -> LATIN CAPITAL LETTER X
        'Y'     ,    //  Y    0x79 -> LATIN CAPITAL LETTER Y
        'Z'     ,    //  Z    0x7A -> LATIN CAPITAL LETTER Z
        '\u253c',    //  ┼    0x7B -> BOX DRAWINGS LIGHT VERTICAL AND HORIZONTAL
        '\uf12e',    //      0x7C -> LEFT HALF BLOCK MEDIUM SHADE (CUS)
        '\u2502',    //  │    0x7D -> BOX DRAWINGS LIGHT VERTICAL
        '\u2592',    //  ▒    0x7E -> MEDIUM SHADE
        '\uf139',    //      0x7F -> MEDIUM SHADE SLASHED LEFT (CUS)
        '\ufffe',    //       0x80 -> UNDEFINED
        '\uf104',    //       0x81 -> ORANGE COLOR SWITCH (CUS)
        '\ufffe',    //       0x82 -> UNDEFINED
        '\ufffe',    //       0x83 -> UNDEFINED
        '\ufffe',    //       0x84 -> UNDEFINED
        '\uf110',    //      0x85 -> FUNCTION KEY 1 (CUS)
        '\uf112',    //      0x86 -> FUNCTION KEY 3 (CUS)
        '\uf114',    //      0x87 -> FUNCTION KEY 5 (CUS)
        '\uf116',    //      0x88 -> FUNCTION KEY 7 (CUS)
        '\uf111',    //      0x89 -> FUNCTION KEY 2 (CUS)
        '\uf113',    //      0x8A -> FUNCTION KEY 4 (CUS)
        '\uf115',    //      0x8B -> FUNCTION KEY 6 (CUS)
        '\uf117',    //      0x8C -> FUNCTION KEY 8 (CUS)
        '\r'    ,    //       0x8D -> CARRIAGE RETURN (SHIFT-RETURN)
        '\u000f',    //      0x8E -> SHIFT IN
        '\ufffe',    //       0x8F -> UNDEFINED
        '\uf105',    //       0x90 -> BLACK COLOR SWITCH (CUS)
        '\uf11e',    //      0x91 -> CURSOR UP (CUS)
        '\uf11b',    //      0x92 -> REVERSE VIDEO OFF (CUS)
        '\u000c',    //       0x93 -> FORM FEED (CLEAR SCREEN)
        '\uf121',    //      0x94 -> INSERT (CUS)
        '\uf106',    //       0x95 -> BROWN COLOR SWITCH (CUS)
        '\uf107',    //       0x96 -> LIGHT RED COLOR SWITCH (CUS)
        '\uf108',    //       0x97 -> GRAY 1 COLOR SWITCH (CUS)
        '\uf109',    //      0x98 -> GRAY 2 COLOR SWITCH (CUS)
        '\uf10a',    //      0x99 -> LIGHT GREEN COLOR SWITCH (CUS)
        '\uf10b',    //      0x9A -> LIGHT BLUE COLOR SWITCH (CUS)
        '\uf10c',    //      0x9B -> GRAY 3 COLOR SWITCH (CUS)
        '\uf10d',    //      0x9C -> PURPLE COLOR SWITCH (CUS)
        '\uf11d',    //      0x9D -> CURSOR LEFT (CUS)
        '\uf10e',    //      0x9E -> YELLOW COLOR SWITCH (CUS)
        '\uf10f',    //      0x9F -> CYAN COLOR SWITCH (CUS)
        '\u00a0',    //       0xA0 -> NO-BREAK SPACE
        '\u258c',    //  ▌    0xA1 -> LEFT HALF BLOCK
        '\u2584',    //  ▄    0xA2 -> LOWER HALF BLOCK
        '\u2594',    //  ▔    0xA3 -> UPPER ONE EIGHTH BLOCK
        '\u2581',    //  ▁    0xA4 -> LOWER ONE EIGHTH BLOCK
        '\u258f',    //  ▏    0xA5 -> LEFT ONE EIGHTH BLOCK
        '\u2592',    //  ▒    0xA6 -> MEDIUM SHADE
        '\u2595',    //  ▕    0xA7 -> RIGHT ONE EIGHTH BLOCK
        '\uf12f',    //      0xA8 -> LOWER HALF BLOCK MEDIUM SHADE (CUS)
        '\uf13a',    //      0xA9 -> MEDIUM SHADE SLASHED RIGHT (CUS)
        '\uf130',    //      0xAA -> RIGHT ONE QUARTER BLOCK (CUS)
        '\u251c',    //  ├    0xAB -> BOX DRAWINGS LIGHT VERTICAL AND RIGHT
        '\u2597',    //  ▗    0xAC -> QUADRANT LOWER RIGHT
        '\u2514',    //  └    0xAD -> BOX DRAWINGS LIGHT UP AND RIGHT
        '\u2510',    //  ┐    0xAE -> BOX DRAWINGS LIGHT DOWN AND LEFT
        '\u2582',    //  ▂    0xAF -> LOWER ONE QUARTER BLOCK
        '\u250c',    //  ┌    0xB0 -> BOX DRAWINGS LIGHT DOWN AND RIGHT
        '\u2534',    //  ┴    0xB1 -> BOX DRAWINGS LIGHT UP AND HORIZONTAL
        '\u252c',    //  ┬    0xB2 -> BOX DRAWINGS LIGHT DOWN AND HORIZONTAL
        '\u2524',    //  ┤    0xB3 -> BOX DRAWINGS LIGHT VERTICAL AND LEFT
        '\u258e',    //  ▎    0xB4 -> LEFT ONE QUARTER BLOCK
        '\u258d',    //  ▍    0xB5 -> LEFT THREE EIGTHS BLOCK
        '\uf131',    //      0xB6 -> RIGHT THREE EIGHTHS BLOCK (CUS)
        '\uf132',    //  🮂    0xB7 -> UPPER ONE QUARTER BLOCK (CUS)
        '\uf133',    //      0xB8 -> UPPER THREE EIGHTS BLOCK (CUS)
        '\u2583',    //  ▃    0xB9 -> LOWER THREE EIGHTHS BLOCK
        '\u2713',    //  ✓    0xBA -> CHECK MARK
        '\u2596',    //  ▖    0xBB -> QUADRANT LOWER LEFT
        '\u259d',    //  ▝    0xBC -> QUADRANT UPPER RIGHT
        '\u2518',    //  ┘    0xBD -> BOX DRAWINGS LIGHT UP AND LEFT
        '\u2598',    //  ▘    0xBE -> QUADRANT UPPER LEFT
        '\u259a',    //  ▚    0xBF -> QUADRANT UPPER LEFT AND LOWER RIGHT
        '\u2500',    //  ─    0xC0 -> BOX DRAWINGS LIGHT HORIZONTAL
        'A'     ,    //  A    0xC1 -> LATIN CAPITAL LETTER A
        'B'     ,    //  B    0xC2 -> LATIN CAPITAL LETTER B
        'C'     ,    //  C    0xC3 -> LATIN CAPITAL LETTER C
        'D'     ,    //  D    0xC4 -> LATIN CAPITAL LETTER D
        'E'     ,    //  E    0xC5 -> LATIN CAPITAL LETTER E
        'F'     ,    //  F    0xC6 -> LATIN CAPITAL LETTER F
        'G'     ,    //  G    0xC7 -> LATIN CAPITAL LETTER G
        'H'     ,    //  H    0xC8 -> LATIN CAPITAL LETTER H
        'I'     ,    //  I    0xC9 -> LATIN CAPITAL LETTER I
        'J'     ,    //  J    0xCA -> LATIN CAPITAL LETTER J
        'K'     ,    //  K    0xCB -> LATIN CAPITAL LETTER K
        'L'     ,    //  L    0xCC -> LATIN CAPITAL LETTER L
        'M'     ,    //  M    0xCD -> LATIN CAPITAL LETTER M
        'N'     ,    //  N    0xCE -> LATIN CAPITAL LETTER N
        'O'     ,    //  O    0xCF -> LATIN CAPITAL LETTER O
        'P'     ,    //  P    0xD0 -> LATIN CAPITAL LETTER P
        'Q'     ,    //  Q    0xD1 -> LATIN CAPITAL LETTER Q
        'R'     ,    //  R    0xD2 -> LATIN CAPITAL LETTER R
        'S'     ,    //  S    0xD3 -> LATIN CAPITAL LETTER S
        'T'     ,    //  T    0xD4 -> LATIN CAPITAL LETTER T
        'U'     ,    //  U    0xD5 -> LATIN CAPITAL LETTER U
        'V'     ,    //  V    0xD6 -> LATIN CAPITAL LETTER V
        'W'     ,    //  W    0xD7 -> LATIN CAPITAL LETTER W
        'X'     ,    //  X    0xD8 -> LATIN CAPITAL LETTER X
        'Y'     ,    //  Y    0xD9 -> LATIN CAPITAL LETTER Y
        'Z'     ,    //  Z    0xDA -> LATIN CAPITAL LETTER Z
        '\u253c',    //  ┼    0xDB -> BOX DRAWINGS LIGHT VERTICAL AND HORIZONTAL
        '\uf12e',    //      0xDC -> LEFT HALF BLOCK MEDIUM SHADE (CUS)
        '\u2502',    //  │    0xDD -> BOX DRAWINGS LIGHT VERTICAL
        '\u2592',    //  ▒    0xDE -> MEDIUM SHADE
        '\uf139',    //      0xDF -> MEDIUM SHADE SLASHED LEFT (CUS)
        '\u00a0',    //       0xE0 -> NO-BREAK SPACE
        '\u258c',    //  ▌    0xE1 -> LEFT HALF BLOCK
        '\u2584',    //  ▄    0xE2 -> LOWER HALF BLOCK
        '\u2594',    //  ▔    0xE3 -> UPPER ONE EIGHTH BLOCK
        '\u2581',    //  ▁    0xE4 -> LOWER ONE EIGHTH BLOCK
        '\u258f',    //  ▏    0xE5 -> LEFT ONE EIGHTH BLOCK
        '\u2592',    //  ▒    0xE6 -> MEDIUM SHADE
        '\u2595',    //  ▕    0xE7 -> RIGHT ONE EIGHTH BLOCK
        '\uf12f',    //      0xE8 -> LOWER HALF BLOCK MEDIUM SHADE (CUS)
        '\uf13a',    //      0xE9 -> MEDIUM SHADE SLASHED RIGHT (CUS)
        '\uf130',    //  🮇    0xEA -> RIGHT ONE QUARTER BLOCK (CUS)
        '\u251c',    //  ├    0xEB -> BOX DRAWINGS LIGHT VERTICAL AND RIGHT
        '\u2597',    //  ▗    0xEC -> QUADRANT LOWER RIGHT
        '\u2514',    //  └    0xED -> BOX DRAWINGS LIGHT UP AND RIGHT
        '\u2510',    //  ┐    0xEE -> BOX DRAWINGS LIGHT DOWN AND LEFT
        '\u2582',    //  ▂    0xEF -> LOWER ONE QUARTER BLOCK
        '\u250c',    //  ┌    0xF0 -> BOX DRAWINGS LIGHT DOWN AND RIGHT
        '\u2534',    //  ┴    0xF1 -> BOX DRAWINGS LIGHT UP AND HORIZONTAL
        '\u252c',    //  ┬    0xF2 -> BOX DRAWINGS LIGHT DOWN AND HORIZONTAL
        '\u2524',    //  ┤    0xF3 -> BOX DRAWINGS LIGHT VERTICAL AND LEFT
        '\u258e',    //  ▎    0xF4 -> LEFT ONE QUARTER BLOCK
        '\u258d',    //  ▍    0xF5 -> LEFT THREE EIGTHS BLOCK
        '\uf131',    //      0xF6 -> RIGHT THREE EIGHTHS BLOCK (CUS)
        '\uf132',    //      0xF7 -> UPPER ONE QUARTER BLOCK (CUS)
        '\uf133',    //      0xF8 -> UPPER THREE EIGHTS BLOCK (CUS)
        '\u2583',    //  ▃    0xF9 -> LOWER THREE EIGHTHS BLOCK
        '\u2713',    //  ✓    0xFA -> CHECK MARK
        '\u2596',    //  ▖    0xFB -> QUADRANT LOWER LEFT
        '\u259d',    //  ▝    0xFC -> QUADRANT UPPER RIGHT
        '\u2518',    //  ┘    0xFD -> BOX DRAWINGS LIGHT UP AND LEFT
        '\u2598',    //  ▘    0xFE -> QUADRANT UPPER LEFT
        '\u2592'     //  ▒    0xFF -> MEDIUM SHADE
    )

    private val decodingPetsciiUppercase = charArrayOf(
        '\u0000',    //       0x00 -> \u0000
        '\ufffe',    //       0x01 -> UNDEFINED
        '\ufffe',    //       0x02 -> UNDEFINED
        '\ufffe',    //       0x03 -> UNDEFINED
        '\ufffe',    //       0x04 -> UNDEFINED
        '\uf100',    //       0x05 -> WHITE COLOR SWITCH (CUS)
        '\ufffe',    //       0x06 -> UNDEFINED
        '\ufffe',    //       0x07 -> UNDEFINED
        '\uf118',    //       0x08 -> DISABLE CHARACTER SET SWITCHING (CUS)
        '\uf119',    //       0x09 -> ENABLE CHARACTER SET SWITCHING (CUS)
        '\ufffe',    //       0x0A -> UNDEFINED
        '\ufffe',    //       0x0B -> UNDEFINED
        '\ufffe',    //       0x0C -> UNDEFINED
        '\n'    ,    //       0x0D -> LINE FEED (RETURN)
        '\u000e',    //       0x0E -> SHIFT OUT
        '\ufffe',    //       0x0F -> UNDEFINED
        '\ufffe',    //       0x10 -> UNDEFINED
        '\uf11c',    //       0x11 -> CURSOR DOWN (CUS)
        '\uf11a',    //       0x12 -> REVERSE VIDEO ON (CUS)
        '\uf120',    //       0x13 -> HOME (CUS)
        '\u007f',    //       0x14 -> DELETE
        '\ufffe',    //       0x15 -> UNDEFINED
        '\ufffe',    //       0x16 -> UNDEFINED
        '\ufffe',    //       0x17 -> UNDEFINED
        '\ufffe',    //       0x18 -> UNDEFINED
        '\ufffe',    //       0x19 -> UNDEFINED
        '\ufffe',    //       0x1A -> UNDEFINED
        '\ufffe',    //       0x1B -> UNDEFINED
        '\uf101',    //       0x1C -> RED COLOR SWITCH (CUS)
        '\uf11d',    //       0x1D -> CURSOR RIGHT (CUS)
        '\uf102',    //       0x1E -> GREEN COLOR SWITCH (CUS)
        '\uf103',    //       0x1F -> BLUE COLOR SWITCH (CUS)
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
        '@'     ,    //  @    0x40 -> COMMERCIAL AT
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
        '['     ,    //  [    0x5B -> LEFT SQUARE BRACKET
        '\u00a3',    //  £    0x5C -> POUND SIGN
        ']'     ,    //  ]    0x5D -> RIGHT SQUARE BRACKET
        '\u2191',    //  ↑    0x5E -> UPWARDS ARROW
        '\u2190',    //  ←    0x5F -> LEFTWARDS ARROW
        '\u2500',    //  ─    0x60 -> BOX DRAWINGS LIGHT HORIZONTAL
        '\u2660',    //  ♠    0x61 -> BLACK SPADE SUIT
        '\uf13c',    //  │    0x62 -> BOX DRAWINGS LIGHT VERTICAL ONE EIGHTH LEFT (CUS)
        '\uf13b',    //  ─    0x63 -> BOX DRAWINGS LIGHT HORIZONTAL ONE EIGHTH UP (CUS)
        '\uf122',    //      0x64 -> BOX DRAWINGS LIGHT HORIZONTAL TWO EIGHTHS UP (CUS)
        '\uf123',    //      0x65 -> BOX DRAWINGS LIGHT HORIZONTAL THREE EIGHTHS UP (CUS)
        '\uf124',    //      0x66 -> BOX DRAWINGS LIGHT HORIZONTAL ONE EIGHTH DOWN (CUS)
        '\uf126',    //      0x67 -> BOX DRAWINGS LIGHT VERTICAL TWO EIGHTHS LEFT (CUS)
        '\uf128',    //      0x68 -> BOX DRAWINGS LIGHT VERTICAL ONE EIGHTH RIGHT (CUS)
        '\u256e',    //  ╮    0x69 -> BOX DRAWINGS LIGHT ARC DOWN AND LEFT
        '\u2570',    //  ╰    0x6A -> BOX DRAWINGS LIGHT ARC UP AND RIGHT
        '\u256f',    //  ╯    0x6B -> BOX DRAWINGS LIGHT ARC UP AND LEFT
        '\uf12a',    //      0x6C -> ONE EIGHTH BLOCK UP AND RIGHT (CUS)
        '\u2572',    //  ╲    0x6D -> BOX DRAWINGS LIGHT DIAGONAL UPPER LEFT TO LOWER RIGHT
        '\u2571',    //  ╱    0x6E -> BOX DRAWINGS LIGHT DIAGONAL UPPER RIGHT TO LOWER LEFT
        '\uf12b',    //      0x6F -> ONE EIGHTH BLOCK DOWN AND RIGHT (CUS)
        '\uf12c',    //      0x70 -> ONE EIGHTH BLOCK DOWN AND LEFT (CUS)
        '\u25cf',    //  ●    0x71 -> BLACK CIRCLE
        '\uf125',    //      0x72 -> BOX DRAWINGS LIGHT HORIZONTAL TWO EIGHTHS DOWN (CUS)
        '\u2665',    //  ♥    0x73 -> BLACK HEART SUIT
        '\uf127',    //      0x74 -> BOX DRAWINGS LIGHT VERTICAL THREE EIGHTHS LEFT (CUS)
        '\u256d',    //  ╭    0x75 -> BOX DRAWINGS LIGHT ARC DOWN AND RIGHT
        '\u2573',    //  ╳    0x76 -> BOX DRAWINGS LIGHT DIAGONAL CROSS
        '\u25cb',    //  ○    0x77 -> WHITE CIRCLE
        '\u2663',    //  ♣    0x78 -> BLACK CLUB SUIT
        '\uf129',    //      0x79 -> BOX DRAWINGS LIGHT VERTICAL TWO EIGHTS RIGHT (CUS)
        '\u2666',    //  ♦    0x7A -> BLACK DIAMOND SUIT
        '\u253c',    //  ┼    0x7B -> BOX DRAWINGS LIGHT VERTICAL AND HORIZONTAL
        '\uf12e',    //      0x7C -> LEFT HALF BLOCK MEDIUM SHADE (CUS)
        '\u2502',    //  │    0x7D -> BOX DRAWINGS LIGHT VERTICAL
        '\u03c0',    //  π    0x7E -> GREEK SMALL LETTER PI
        '\u25e5',    //  ◥    0x7F -> BLACK UPPER RIGHT TRIANGLE
        '\ufffe',    //       0x80 -> UNDEFINED
        '\uf104',    //      0x81 -> ORANGE COLOR SWITCH (CUS)
        '\ufffe',    //       0x82 -> UNDEFINED
        '\ufffe',    //       0x83 -> UNDEFINED
        '\ufffe',    //       0x84 -> UNDEFINED
        '\uf110',    //       0x85 -> FUNCTION KEY 1 (CUS)
        '\uf112',    //       0x86 -> FUNCTION KEY 3 (CUS)
        '\uf114',    //       0x87 -> FUNCTION KEY 5 (CUS)
        '\uf116',    //       0x88 -> FUNCTION KEY 7 (CUS)
        '\uf111',    //       0x89 -> FUNCTION KEY 2 (CUS)
        '\uf113',    //       0x8A -> FUNCTION KEY 4 (CUS)
        '\uf115',    //       0x8B -> FUNCTION KEY 6 (CUS)
        '\uf117',    //       0x8C -> FUNCTION KEY 8 (CUS)
        '\r'    ,    //       0x8D -> CARRIAGE RETURN (SHIFT-RETURN)
        '\u000f',    //       0x8E -> SHIFT IN
        '\ufffe',    //       0x8F -> UNDEFINED
        '\uf105',    //       0x90 -> BLACK COLOR SWITCH (CUS)
        '\uf11e',    //       0x91 -> CURSOR UP (CUS)
        '\uf11b',    //       0x92 -> REVERSE VIDEO OFF (CUS)
        '\u000c',    //       0x93 -> FORM FEED (CLEAR SCREEN)
        '\uf121',    //       0x94 -> INSERT (CUS)
        '\uf106',    //       0x95 -> BROWN COLOR SWITCH (CUS)
        '\uf107',    //       0x96 -> LIGHT RED COLOR SWITCH (CUS)
        '\uf108',    //       0x97 -> GRAY 1 COLOR SWITCH (CUS)
        '\uf109',    //       0x98 -> GRAY 2 COLOR SWITCH (CUS)
        '\uf10a',    //       0x99 -> LIGHT GREEN COLOR SWITCH (CUS)
        '\uf10b',    //       0x9A -> LIGHT BLUE COLOR SWITCH (CUS)
        '\uf10c',    //       0x9B -> GRAY 3 COLOR SWITCH (CUS)
        '\uf10d',    //       0x9C -> PURPLE COLOR SWITCH (CUS)
        '\uf11d',    //       0x9D -> CURSOR LEFT (CUS)
        '\uf10e',    //       0x9E -> YELLOW COLOR SWITCH (CUS)
        '\uf10f',    //       0x9F -> CYAN COLOR SWITCH (CUS)
        '\u00a0',    //       0xA0 -> NO-BREAK SPACE
        '\u258c',    //  ▌    0xA1 -> LEFT HALF BLOCK
        '\u2584',    //  ▄    0xA2 -> LOWER HALF BLOCK
        '\u2594',    //  ▔    0xA3 -> UPPER ONE EIGHTH BLOCK
        '\u2581',    //  ▁    0xA4 -> LOWER ONE EIGHTH BLOCK
        '\u258f',    //  ▏    0xA5 -> LEFT ONE EIGHTH BLOCK
        '\u2592',    //  ▒    0xA6 -> MEDIUM SHADE
        '\u2595',    //  ▕    0xA7 -> RIGHT ONE EIGHTH BLOCK
        '\uf12f',    //      0xA8 -> LOWER HALF BLOCK MEDIUM SHADE (CUS)
        '\u25e4',    //  ◤    0xA9 -> BLACK UPPER LEFT TRIANGLE
        '\uf130',    //      0xAA -> RIGHT ONE QUARTER BLOCK (CUS)
        '\u251c',    //  ├    0xAB -> BOX DRAWINGS LIGHT VERTICAL AND RIGHT
        '\u2597',    //  ▗    0xAC -> QUADRANT LOWER RIGHT
        '\u2514',    //  └    0xAD -> BOX DRAWINGS LIGHT UP AND RIGHT
        '\u2510',    //  ┐    0xAE -> BOX DRAWINGS LIGHT DOWN AND LEFT
        '\u2582',    //  ▂    0xAF -> LOWER ONE QUARTER BLOCK
        '\u250c',    //  ┌    0xB0 -> BOX DRAWINGS LIGHT DOWN AND RIGHT
        '\u2534',    //  ┴    0xB1 -> BOX DRAWINGS LIGHT UP AND HORIZONTAL
        '\u252c',    //  ┬    0xB2 -> BOX DRAWINGS LIGHT DOWN AND HORIZONTAL
        '\u2524',    //  ┤    0xB3 -> BOX DRAWINGS LIGHT VERTICAL AND LEFT
        '\u258e',    //  ▎    0xB4 -> LEFT ONE QUARTER BLOCK
        '\u258d',    //  ▍    0xB5 -> LEFT THREE EIGTHS BLOCK
        '\uf131',    //      0xB6 -> RIGHT THREE EIGHTHS BLOCK (CUS)
        '\uf132',    //      0xB7 -> UPPER ONE QUARTER BLOCK (CUS)
        '\uf133',    //      0xB8 -> UPPER THREE EIGHTS BLOCK (CUS)
        '\u2583',    //  ▃    0xB9 -> LOWER THREE EIGHTHS BLOCK
        '\uf12d',    //      0xBA -> ONE EIGHTH BLOCK UP AND LEFT (CUS)
        '\u2596',    //  ▖    0xBB -> QUADRANT LOWER LEFT
        '\u259d',    //  ▝    0xBC -> QUADRANT UPPER RIGHT
        '\u2518',    //  ┘    0xBD -> BOX DRAWINGS LIGHT UP AND LEFT
        '\u2598',    //  ▘    0xBE -> QUADRANT UPPER LEFT
        '\u259a',    //  ▚    0xBF -> QUADRANT UPPER LEFT AND LOWER RIGHT
        '\u2500',    //  ─    0xC0 -> BOX DRAWINGS LIGHT HORIZONTAL
        '\u2660',    //  ♠    0xC1 -> BLACK SPADE SUIT
        '\uf13c',    //  │    0xC2 -> BOX DRAWINGS LIGHT VERTICAL ONE EIGHTH LEFT (CUS)
        '\uf13b',    //  ─    0xC3 -> BOX DRAWINGS LIGHT HORIZONTAL ONE EIGHTH UP (CUS)
        '\uf122',    //      0xC4 -> BOX DRAWINGS LIGHT HORIZONTAL TWO EIGHTHS UP (CUS)
        '\uf123',    //      0xC5 -> BOX DRAWINGS LIGHT HORIZONTAL THREE EIGHTHS UP (CUS)
        '\uf124',    //      0xC6 -> BOX DRAWINGS LIGHT HORIZONTAL ONE EIGHTH DOWN (CUS)
        '\uf126',    //      0xC7 -> BOX DRAWINGS LIGHT VERTICAL TWO EIGHTHS LEFT (CUS)
        '\uf128',    //      0xC8 -> BOX DRAWINGS LIGHT VERTICAL ONE EIGHTH RIGHT (CUS)
        '\u256e',    //  ╮    0xC9 -> BOX DRAWINGS LIGHT ARC DOWN AND LEFT
        '\u2570',    //  ╰    0xCA -> BOX DRAWINGS LIGHT ARC UP AND RIGHT
        '\u256f',    //  ╯    0xCB -> BOX DRAWINGS LIGHT ARC UP AND LEFT
        '\uf12a',    //      0xCC -> ONE EIGHTH BLOCK UP AND RIGHT (CUS)
        '\u2572',    //  ╲    0xCD -> BOX DRAWINGS LIGHT DIAGONAL UPPER LEFT TO LOWER RIGHT
        '\u2571',    //  ╱    0xCE -> BOX DRAWINGS LIGHT DIAGONAL UPPER RIGHT TO LOWER LEFT
        '\uf12b',    //      0xCF -> ONE EIGHTH BLOCK DOWN AND RIGHT (CUS)
        '\uf12c',    //      0xD0 -> ONE EIGHTH BLOCK DOWN AND LEFT (CUS)
        '\u25cf',    //  ●    0xD1 -> BLACK CIRCLE
        '\uf125',    //      0xD2 -> BOX DRAWINGS LIGHT HORIZONTAL TWO EIGHTS DOWN (CUS)
        '\u2665',    //  ♥    0xD3 -> BLACK HEART SUIT
        '\uf127',    //      0xD4 -> BOX DRAWINGS LIGHT VERTICAL THREE EIGHTS LEFT (CUS)
        '\u256d',    //  ╭    0xD5 -> BOX DRAWINGS LIGHT ARC DOWN AND LEFT
        '\u2573',    //  ╳    0xD6 -> BOX DRAWINGS LIGHT DIAGONAL CROSS
        '\u25cb',    //  ○    0xD7 -> WHITE CIRCLE
        '\u2663',    //  ♣    0xD8 -> BLACK CLUB SUIT
        '\uf129',    //      0xD9 -> BOX DRAWINGS LIGHT VERTICAL TWO EIGHTS RIGHT (CUS)
        '\u2666',    //  ♦    0xDA -> BLACK DIAMOND SUIT
        '\u253c',    //  ┼    0xDB -> BOX DRAWINGS LIGHT VERTICAL AND HORIZONTAL
        '\uf12e',    //      0xDC -> LEFT HALF BLOCK MEDIUM SHADE (CUS)
        '\u2502',    //  │    0xDD -> BOX DRAWINGS LIGHT VERTICAL
        '\u03c0',    //  π    0xDE -> GREEK SMALL LETTER PI
        '\u25e5',    //  ◥    0xDF -> BLACK UPPER RIGHT TRIANGLE
        '\u00a0',    //       0xE0 -> NO-BREAK SPACE
        '\u258c',    //  ▌    0xE1 -> LEFT HALF BLOCK
        '\u2584',    //  ▄    0xE2 -> LOWER HALF BLOCK
        '\u2594',    //  ▔    0xE3 -> UPPER ONE EIGHTH BLOCK
        '\u2581',    //  ▁    0xE4 -> LOWER ONE EIGHTH BLOCK
        '\u258f',    //  ▏    0xE5 -> LEFT ONE EIGHTH BLOCK
        '\u2592',    //  ▒    0xE6 -> MEDIUM SHADE
        '\u2595',    //  ▕    0xE7 -> RIGHT ONE EIGHTH BLOCK
        '\uf12f',    //      0xE8 -> LOWER HALF BLOCK MEDIUM SHADE (CUS)
        '\u25e4',    //  ◤    0xE9 -> BLACK UPPER LEFT TRIANGLE
        '\uf130',    //      0xEA -> RIGHT ONE QUARTER BLOCK (CUS)
        '\u251c',    //  ├    0xEB -> BOX DRAWINGS LIGHT VERTICAL AND RIGHT
        '\u2597',    //  ▗    0xEC -> QUADRANT LOWER RIGHT
        '\u2514',    //  └    0xED -> BOX DRAWINGS LIGHT UP AND RIGHT
        '\u2510',    //  ┐    0xEE -> BOX DRAWINGS LIGHT DOWN AND LEFT
        '\u2582',    //  ▂    0xEF -> LOWER ONE QUARTER BLOCK
        '\u250c',    //  ┌    0xF0 -> BOX DRAWINGS LIGHT DOWN AND RIGHT
        '\u2534',    //  ┴    0xF1 -> BOX DRAWINGS LIGHT UP AND HORIZONTAL
        '\u252c',    //  ┬    0xF2 -> BOX DRAWINGS LIGHT DOWN AND HORIZONTAL
        '\u2524',    //  ┤    0xF3 -> BOX DRAWINGS LIGHT VERTICAL AND LEFT
        '\u258e',    //  ▎    0xF4 -> LEFT ONE QUARTER BLOCK
        '\u258d',    //  ▍    0xF5 -> LEFT THREE EIGTHS BLOCK
        '\uf131',    //      0xF6 -> RIGHT THREE EIGHTHS BLOCK (CUS)
        '\uf132',    //      0xF7 -> UPPER ONE QUARTER BLOCK (CUS)
        '\uf133',    //      0xF8 -> UPPER THREE EIGHTS BLOCK (CUS)
        '\u2583',    //  ▃    0xF9 -> LOWER THREE EIGHTHS BLOCK
        '\uf12d',    //      0xFA -> ONE EIGHTH BLOCK UP AND LEFT (CUS)
        '\u2596',    //  ▖    0xFB -> QUADRANT LOWER LEFT
        '\u259d',    //  ▝    0xFC -> QUADRANT UPPER RIGHT
        '\u2518',    //  ┘    0xFD -> BOX DRAWINGS LIGHT UP AND LEFT
        '\u2598',    //  ▘    0xFE -> QUADRANT UPPER LEFT
        '\u03c0'     //  π    0xFF -> GREEK SMALL LETTER PI
    )

    private val decodingScreencodeLowercase = charArrayOf(
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
        '\u00a3',    //  £    0x1C -> POUND SIGN
        ']'     ,    //  ]    0x1D -> RIGHT SQUARE BRACKET
        '\u2191',    //  ↑    0x1E -> UPWARDS ARROW
        '\u2190',    //  ←    0x1F -> LEFTWARDS ARROW
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
        '\u2500',    //  ─    0x40 -> BOX DRAWINGS LIGHT HORIZONTAL
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
        '\u253c',    //  ┼    0x5B -> BOX DRAWINGS LIGHT VERTICAL AND HORIZONTAL
        '\uf12e',    //      0x5C -> LEFT HALF BLOCK MEDIUM SHADE (CUS)
        '\u2502',    //  │    0x5D -> BOX DRAWINGS LIGHT VERTICAL
        '\u2592',    //  ▒    0x5E -> MEDIUM SHADE
        '\uf139',    //      0x5F -> MEDIUM SHADE SLASHED LEFT (CUS)
        '\u00a0',    //       0x60 -> NO-BREAK SPACE
        '\u258c',    //  ▌    0x61 -> LEFT HALF BLOCK
        '\u2584',    //  ▄    0x62 -> LOWER HALF BLOCK
        '\u2594',    //  ▔    0x63 -> UPPER ONE EIGHTH BLOCK
        '\u2581',    //  ▁    0x64 -> LOWER ONE EIGHTH BLOCK
        '\u258f',    //  ▏    0x65 -> LEFT ONE EIGHTH BLOCK
        '\u2592',    //  ▒    0x66 -> MEDIUM SHADE
        '\u2595',    //  ▕    0x67 -> RIGHT ONE EIGHTH BLOCK
        '\uf12f',    //      0x68 -> LOWER HALF BLOCK MEDIUM SHADE (CUS)
        '\uf13a',    //      0x69 -> MEDIUM SHADE SLASHED RIGHT (CUS)
        '\uf130',    //      0x6A -> RIGHT ONE QUARTER BLOCK (CUS)
        '\u251c',    //  ├    0x6B -> BOX DRAWINGS LIGHT VERTICAL AND RIGHT
        '\u2597',    //  ▗    0x6C -> QUADRANT LOWER RIGHT
        '\u2514',    //  └    0x6D -> BOX DRAWINGS LIGHT UP AND RIGHT
        '\u2510',    //  ┐    0x6E -> BOX DRAWINGS LIGHT DOWN AND LEFT
        '\u2582',    //  ▂    0x6F -> LOWER ONE QUARTER BLOCK
        '\u250c',    //  ┌    0x70 -> BOX DRAWINGS LIGHT DOWN AND RIGHT
        '\u2534',    //  ┴    0x71 -> BOX DRAWINGS LIGHT UP AND HORIZONTAL
        '\u252c',    //  ┬    0x72 -> BOX DRAWINGS LIGHT DOWN AND HORIZONTAL
        '\u2524',    //  ┤    0x73 -> BOX DRAWINGS LIGHT VERTICAL AND LEFT
        '\u258e',    //  ▎    0x74 -> LEFT ONE QUARTER BLOCK
        '\u258d',    //  ▍    0x75 -> LEFT THREE EIGTHS BLOCK
        '\uf131',    //      0x76 -> RIGHT THREE EIGHTHS BLOCK (CUS)
        '\uf132',    //      0x77 -> UPPER ONE QUARTER BLOCK (CUS)
        '\uf133',    //      0x78 -> UPPER THREE EIGHTS BLOCK (CUS)
        '\u2583',    //  ▃    0x79 -> LOWER THREE EIGHTHS BLOCK
        '\u2713',    //  ✓    0x7A -> CHECK MARK
        '\u2596',    //  ▖    0x7B -> QUADRANT LOWER LEFT
        '\u259d',    //  ▝    0x7C -> QUADRANT UPPER RIGHT
        '\u2518',    //  ┘    0x7D -> BOX DRAWINGS LIGHT UP AND LEFT
        '\u2598',    //  ▘    0x7E -> QUADRANT UPPER LEFT
        '\u259a',    //  ▚    0x7F -> QUADRANT UPPER LEFT AND LOWER RIGHT
        '\ufffe',    //       0x80 -> UNDEFINED
        '\ufffe',    //       0x81 -> UNDEFINED
        '\ufffe',    //       0x82 -> UNDEFINED
        '\ufffe',    //       0x83 -> UNDEFINED
        '\ufffe',    //       0x84 -> UNDEFINED
        '\ufffe',    //       0x85 -> UNDEFINED
        '\ufffe',    //       0x86 -> UNDEFINED
        '\ufffe',    //       0x87 -> UNDEFINED
        '\ufffe',    //       0x88 -> UNDEFINED
        '\ufffe',    //       0x89 -> UNDEFINED
        '\ufffe',    //       0x8A -> UNDEFINED
        '\ufffe',    //       0x8B -> UNDEFINED
        '\ufffe',    //       0x8C -> UNDEFINED
        '\ufffe',    //       0x8D -> UNDEFINED
        '\ufffe',    //       0x8E -> UNDEFINED
        '\ufffe',    //       0x8F -> UNDEFINED
        '\ufffe',    //       0x90 -> UNDEFINED
        '\ufffe',    //       0x91 -> UNDEFINED
        '\ufffe',    //       0x92 -> UNDEFINED
        '\ufffe',    //       0x93 -> UNDEFINED
        '\ufffe',    //       0x94 -> UNDEFINED
        '\ufffe',    //       0x95 -> UNDEFINED
        '\ufffe',    //       0x96 -> UNDEFINED
        '\ufffe',    //       0x97 -> UNDEFINED
        '\ufffe',    //       0x98 -> UNDEFINED
        '\ufffe',    //       0x99 -> UNDEFINED
        '\ufffe',    //       0x9A -> UNDEFINED
        '\ufffe',    //       0x9B -> UNDEFINED
        '\ufffe',    //       0x9C -> UNDEFINED
        '\ufffe',    //       0x9D -> UNDEFINED
        '\ufffe',    //       0x9E -> UNDEFINED
        '\ufffe',    //       0x9F -> UNDEFINED
        '\ufffe',    //       0xA0 -> UNDEFINED
        '\ufffe',    //       0xA1 -> UNDEFINED
        '\ufffe',    //       0xA2 -> UNDEFINED
        '\ufffe',    //       0xA3 -> UNDEFINED
        '\ufffe',    //       0xA4 -> UNDEFINED
        '\ufffe',    //       0xA5 -> UNDEFINED
        '\ufffe',    //       0xA6 -> UNDEFINED
        '\ufffe',    //       0xA7 -> UNDEFINED
        '\ufffe',    //       0xA8 -> UNDEFINED
        '\ufffe',    //       0xA9 -> UNDEFINED
        '\ufffe',    //       0xAA -> UNDEFINED
        '\ufffe',    //       0xAB -> UNDEFINED
        '\ufffe',    //       0xAC -> UNDEFINED
        '\ufffe',    //       0xAD -> UNDEFINED
        '\ufffe',    //       0xAE -> UNDEFINED
        '\ufffe',    //       0xAF -> UNDEFINED
        '\ufffe',    //       0xB0 -> UNDEFINED
        '\ufffe',    //       0xB1 -> UNDEFINED
        '\ufffe',    //       0xB2 -> UNDEFINED
        '\ufffe',    //       0xB3 -> UNDEFINED
        '\ufffe',    //       0xB4 -> UNDEFINED
        '\ufffe',    //       0xB5 -> UNDEFINED
        '\ufffe',    //       0xB6 -> UNDEFINED
        '\ufffe',    //       0xB7 -> UNDEFINED
        '\ufffe',    //       0xB8 -> UNDEFINED
        '\ufffe',    //       0xB9 -> UNDEFINED
        '\ufffe',    //       0xBA -> UNDEFINED
        '\ufffe',    //       0xBB -> UNDEFINED
        '\ufffe',    //       0xBC -> UNDEFINED
        '\ufffe',    //       0xBD -> UNDEFINED
        '\ufffe',    //       0xBE -> UNDEFINED
        '\ufffe',    //       0xBF -> UNDEFINED
        '\ufffe',    //       0xC0 -> UNDEFINED
        '\ufffe',    //       0xC1 -> UNDEFINED
        '\ufffe',    //       0xC2 -> UNDEFINED
        '\ufffe',    //       0xC3 -> UNDEFINED
        '\ufffe',    //       0xC4 -> UNDEFINED
        '\ufffe',    //       0xC5 -> UNDEFINED
        '\ufffe',    //       0xC6 -> UNDEFINED
        '\ufffe',    //       0xC7 -> UNDEFINED
        '\ufffe',    //       0xC8 -> UNDEFINED
        '\ufffe',    //       0xC9 -> UNDEFINED
        '\ufffe',    //       0xCA -> UNDEFINED
        '\ufffe',    //       0xCB -> UNDEFINED
        '\ufffe',    //       0xCC -> UNDEFINED
        '\ufffe',    //       0xCD -> UNDEFINED
        '\ufffe',    //       0xCE -> UNDEFINED
        '\ufffe',    //       0xCF -> UNDEFINED
        '\ufffe',    //       0xD0 -> UNDEFINED
        '\ufffe',    //       0xD1 -> UNDEFINED
        '\ufffe',    //       0xD2 -> UNDEFINED
        '\ufffe',    //       0xD3 -> UNDEFINED
        '\ufffe',    //       0xD4 -> UNDEFINED
        '\ufffe',    //       0xD5 -> UNDEFINED
        '\ufffe',    //       0xD6 -> UNDEFINED
        '\ufffe',    //       0xD7 -> UNDEFINED
        '\ufffe',    //       0xD8 -> UNDEFINED
        '\ufffe',    //       0xD9 -> UNDEFINED
        '\ufffe',    //       0xDA -> UNDEFINED
        '\ufffe',    //       0xDB -> UNDEFINED
        '\ufffe',    //       0xDC -> UNDEFINED
        '\ufffe',    //       0xDD -> UNDEFINED
        '\ufffe',    //       0xDE -> UNDEFINED
        '\ufffe',    //       0xDF -> UNDEFINED
        '\ufffe',    //       0xE0 -> UNDEFINED
        '\ufffe',    //       0xE1 -> UNDEFINED
        '\ufffe',    //       0xE2 -> UNDEFINED
        '\ufffe',    //       0xE3 -> UNDEFINED
        '\ufffe',    //       0xE4 -> UNDEFINED
        '\ufffe',    //       0xE5 -> UNDEFINED
        '\ufffe',    //       0xE6 -> UNDEFINED
        '\ufffe',    //       0xE7 -> UNDEFINED
        '\ufffe',    //       0xE8 -> UNDEFINED
        '\ufffe',    //       0xE9 -> UNDEFINED
        '\ufffe',    //       0xEA -> UNDEFINED
        '\ufffe',    //       0xEB -> UNDEFINED
        '\ufffe',    //       0xEC -> UNDEFINED
        '\ufffe',    //       0xED -> UNDEFINED
        '\ufffe',    //       0xEE -> UNDEFINED
        '\ufffe',    //       0xEF -> UNDEFINED
        '\ufffe',    //       0xF0 -> UNDEFINED
        '\ufffe',    //       0xF1 -> UNDEFINED
        '\ufffe',    //       0xF2 -> UNDEFINED
        '\ufffe',    //       0xF3 -> UNDEFINED
        '\ufffe',    //       0xF4 -> UNDEFINED
        '\ufffe',    //       0xF5 -> UNDEFINED
        '\ufffe',    //       0xF6 -> UNDEFINED
        '\ufffe',    //       0xF7 -> UNDEFINED
        '\ufffe',    //       0xF8 -> UNDEFINED
        '\ufffe',    //       0xF9 -> UNDEFINED
        '\ufffe',    //       0xFA -> UNDEFINED
        '\ufffe',    //       0xFB -> UNDEFINED
        '\ufffe',    //       0xFC -> UNDEFINED
        '\ufffe',    //       0xFD -> UNDEFINED
        '\ufffe',    //       0xFE -> UNDEFINED
        '\ufffe'     //       0xFF -> UNDEFINED
    )

    private val decodingScreencodeUppercase = charArrayOf(
        '@'     ,    //  @    0x00 -> COMMERCIAL AT
        'A'     ,    //  A    0x01 -> LATIN CAPITAL LETTER A
        'B'     ,    //  B    0x02 -> LATIN CAPITAL LETTER B
        'C'     ,    //  C    0x03 -> LATIN CAPITAL LETTER C
        'D'     ,    //  D    0x04 -> LATIN CAPITAL LETTER D
        'E'     ,    //  E    0x05 -> LATIN CAPITAL LETTER E
        'F'     ,    //  F    0x06 -> LATIN CAPITAL LETTER F
        'G'     ,    //  G    0x07 -> LATIN CAPITAL LETTER G
        'H'     ,    //  H    0x08 -> LATIN CAPITAL LETTER H
        'I'     ,    //  I    0x09 -> LATIN CAPITAL LETTER I
        'J'     ,    //  J    0x0A -> LATIN CAPITAL LETTER J
        'K'     ,    //  K    0x0B -> LATIN CAPITAL LETTER K
        'L'     ,    //  L    0x0C -> LATIN CAPITAL LETTER L
        'M'     ,    //  M    0x0D -> LATIN CAPITAL LETTER M
        'N'     ,    //  N    0x0E -> LATIN CAPITAL LETTER N
        'O'     ,    //  O    0x0F -> LATIN CAPITAL LETTER O
        'P'     ,    //  P    0x10 -> LATIN CAPITAL LETTER P
        'Q'     ,    //  Q    0x11 -> LATIN CAPITAL LETTER Q
        'R'     ,    //  R    0x12 -> LATIN CAPITAL LETTER R
        'S'     ,    //  S    0x13 -> LATIN CAPITAL LETTER S
        'T'     ,    //  T    0x14 -> LATIN CAPITAL LETTER T
        'U'     ,    //  U    0x15 -> LATIN CAPITAL LETTER U
        'V'     ,    //  V    0x16 -> LATIN CAPITAL LETTER V
        'W'     ,    //  W    0x17 -> LATIN CAPITAL LETTER W
        'X'     ,    //  X    0x18 -> LATIN CAPITAL LETTER X
        'Y'     ,    //  Y    0x19 -> LATIN CAPITAL LETTER Y
        'Z'     ,    //  Z    0x1A -> LATIN CAPITAL LETTER Z
        '['     ,    //  [    0x1B -> LEFT SQUARE BRACKET
        '\u00a3',    //  £    0x1C -> POUND SIGN
        ']'     ,    //  ]    0x1D -> RIGHT SQUARE BRACKET
        '\u2191',    //  ↑    0x1E -> UPWARDS ARROW
        '\u2190',    //  ←    0x1F -> LEFTWARDS ARROW
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
        '\u2500',    //  ─    0x40 -> BOX DRAWINGS LIGHT HORIZONTAL
        '\u2660',    //  ♠    0x41 -> BLACK SPADE SUIT
        '\uf13c',    //  │    0x42 -> BOX DRAWINGS LIGHT VERTICAL ONE EIGHTH LEFT (CUS)
        '\uf13b',    //  ─    0x43 -> BOX DRAWINGS LIGHT HORIZONTAL ONE EIGHTH UP (CUS)
        '\uf122',    //      0x44 -> BOX DRAWINGS LIGHT HORIZONTAL TWO EIGHTHS UP (CUS)
        '\uf123',    //      0x45 -> BOX DRAWINGS LIGHT HORIZONTAL THREE EIGHTHS UP (CUS
        '\uf124',    //      0x46 -> BOX DRAWINGS LIGHT HORIZONTAL ONE EIGHTH DOWN (CUS)
        '\uf126',    //      0x47 -> BOX DRAWINGS LIGHT VERTICAL TWO EIGHTHS LEFT (CUS)
        '\uf128',    //      0x48 -> BOX DRAWINGS LIGHT VERTICAL ONE EIGHTH RIGHT (CUS)
        '\u256e',    //  ╮    0x49 -> BOX DRAWINGS LIGHT ARC DOWN AND LEFT
        '\u2570',    //  ╰    0x4A -> BOX DRAWINGS LIGHT ARC UP AND RIGHT
        '\u256f',    //  ╯    0x4B -> BOX DRAWINGS LIGHT ARC UP AND LEFT
        '\uf12a',    //      0x4C -> ONE EIGHTH BLOCK UP AND RIGHT (CUS)
        '\u2572',    //  ╲    0x4D -> BOX DRAWINGS LIGHT DIAGONAL UPPER LEFT TO LOWER RIGHT
        '\u2571',    //  ╱    0x4E -> BOX DRAWINGS LIGHT DIAGONAL UPPER RIGHT TO LOWER LEFT
        '\uf12b',    //      0x4F -> ONE EIGHTH BLOCK DOWN AND RIGHT (CUS)
        '\uf12c',    //      0x50 -> ONE EIGHTH BLOCK DOWN AND LEFT (CUS)
        '\u25cf',    //  ●    0x51 -> BLACK CIRCLE
        '\uf125',    //      0x52 -> BOX DRAWINGS LIGHT HORIZONTAL TWO EIGHTS DOWN (CUS)
        '\u2665',    //  ♥    0x53 -> BLACK HEART SUIT
        '\uf127',    //      0x54 -> BOX DRAWINGS LIGHT VERTICAL THREE EIGHTS LEFT (CUS)
        '\u256d',    //  ╭    0x55 -> BOX DRAWINGS LIGHT ARC DOWN AND RIGHT
        '\u2573',    //  ╳    0x56 -> BOX DRAWINGS LIGHT DIAGONAL CROSS
        '\u25cb',    //  ○    0x57 -> WHITE CIRCLE
        '\u2663',    //  ♣    0x58 -> BLACK CLUB SUIT
        '\uf129',    //      0x59 -> BOX DRAWINGS LIGHT VERTICAL TWO EIGHTS RIGHT (CUS)
        '\u2666',    //  ♦    0x5A -> BLACK DIAMOND SUIT
        '\u253c',    //  ┼    0x5B -> BOX DRAWINGS LIGHT VERTICAL AND HORIZONTAL
        '\uf12e',    //      0x5C -> LEFT HALF BLOCK MEDIUM SHADE (CUS)
        '\u2502',    //  │    0x5D -> BOX DRAWINGS LIGHT VERTICAL
        '\u03c0',    //  π    0x5E -> GREEK SMALL LETTER PI
        '\u25e5',    //  ◥    0x5F -> BLACK UPPER RIGHT TRIANGLE
        '\u00a0',    //       0x60 -> NO-BREAK SPACE
        '\u258c',    //  ▌    0x61 -> LEFT HALF BLOCK
        '\u2584',    //  ▄    0x62 -> LOWER HALF BLOCK
        '\u2594',    //  ▔    0x63 -> UPPER ONE EIGHTH BLOCK
        '\u2581',    //  ▁    0x64 -> LOWER ONE EIGHTH BLOCK
        '\u258f',    //  ▏    0x65 -> LEFT ONE EIGHTH BLOCK
        '\u2592',    //  ▒    0x66 -> MEDIUM SHADE
        '\u2595',    //  ▕    0x67 -> RIGHT ONE EIGHTH BLOCK
        '\uf12f',    //      0x68 -> LOWER HALF BLOCK MEDIUM SHADE (CUS)
        '\u25e4',    //  ◤    0x69 -> BLACK UPPER LEFT TRIANGLE
        '\uf130',    //      0x6A -> RIGHT ONE QUARTER BLOCK (CUS)
        '\u251c',    //  ├    0x6B -> BOX DRAWINGS LIGHT VERTICAL AND RIGHT
        '\u2597',    //  ▗    0x6C -> QUADRANT LOWER RIGHT
        '\u2514',    //  └    0x6D -> BOX DRAWINGS LIGHT UP AND RIGHT
        '\u2510',    //  ┐    0x6E -> BOX DRAWINGS LIGHT DOWN AND LEFT
        '\u2582',    //  ▂    0x6F -> LOWER ONE QUARTER BLOCK
        '\u250c',    //  ┌    0x70 -> BOX DRAWINGS LIGHT DOWN AND RIGHT
        '\u2534',    //  ┴    0x71 -> BOX DRAWINGS LIGHT UP AND HORIZONTAL
        '\u252c',    //  ┬    0x72 -> BOX DRAWINGS LIGHT DOWN AND HORIZONTAL
        '\u2524',    //  ┤    0x73 -> BOX DRAWINGS LIGHT VERTICAL AND LEFT
        '\u258e',    //  ▎    0x74 -> LEFT ONE QUARTER BLOCK
        '\u258d',    //  ▍    0x75 -> LEFT THREE EIGTHS BLOCK
        '\uf131',    //      0x76 -> RIGHT THREE EIGHTHS BLOCK (CUS)
        '\uf132',    //      0x77 -> UPPER ONE QUARTER BLOCK (CUS)
        '\uf133',    //      0x78 -> UPPER THREE EIGHTS BLOCK (CUS)
        '\u2583',    //  ▃    0x79 -> LOWER THREE EIGHTHS BLOCK
        '\uf12d',    //      0x7A -> ONE EIGHTH BLOCK UP AND LEFT (CUS)
        '\u2596',    //  ▖    0x7B -> QUADRANT LOWER LEFT
        '\u259d',    //  ▝    0x7C -> QUADRANT UPPER RIGHT
        '\u2518',    //  ┘    0x7D -> BOX DRAWINGS LIGHT UP AND LEFT
        '\u2598',    //  ▘    0x7E -> QUADRANT UPPER LEFT
        '\u259a',    //  ▚    0x7F -> QUADRANT UPPER LEFT AND LOWER RIGHT
        '\ufffe',    //       0x80 -> UNDEFINED
        '\ufffe',    //       0x81 -> UNDEFINED
        '\ufffe',    //       0x82 -> UNDEFINED
        '\ufffe',    //       0x83 -> UNDEFINED
        '\ufffe',    //       0x84 -> UNDEFINED
        '\ufffe',    //       0x85 -> UNDEFINED
        '\ufffe',    //       0x86 -> UNDEFINED
        '\ufffe',    //       0x87 -> UNDEFINED
        '\ufffe',    //       0x88 -> UNDEFINED
        '\ufffe',    //       0x89 -> UNDEFINED
        '\ufffe',    //       0x8A -> UNDEFINED
        '\ufffe',    //       0x8B -> UNDEFINED
        '\ufffe',    //       0x8C -> UNDEFINED
        '\ufffe',    //       0x8D -> UNDEFINED
        '\ufffe',    //       0x8E -> UNDEFINED
        '\ufffe',    //       0x8F -> UNDEFINED
        '\ufffe',    //       0x90 -> UNDEFINED
        '\ufffe',    //       0x91 -> UNDEFINED
        '\ufffe',    //       0x92 -> UNDEFINED
        '\ufffe',    //       0x93 -> UNDEFINED
        '\ufffe',    //       0x94 -> UNDEFINED
        '\ufffe',    //       0x95 -> UNDEFINED
        '\ufffe',    //       0x96 -> UNDEFINED
        '\ufffe',    //       0x97 -> UNDEFINED
        '\ufffe',    //       0x98 -> UNDEFINED
        '\ufffe',    //       0x99 -> UNDEFINED
        '\ufffe',    //       0x9A -> UNDEFINED
        '\ufffe',    //       0x9B -> UNDEFINED
        '\ufffe',    //       0x9C -> UNDEFINED
        '\ufffe',    //       0x9D -> UNDEFINED
        '\ufffe',    //       0x9E -> UNDEFINED
        '\ufffe',    //       0x9F -> UNDEFINED
        '\ufffe',    //       0xA0 -> UNDEFINED
        '\ufffe',    //       0xA1 -> UNDEFINED
        '\ufffe',    //       0xA2 -> UNDEFINED
        '\ufffe',    //       0xA3 -> UNDEFINED
        '\ufffe',    //       0xA4 -> UNDEFINED
        '\ufffe',    //       0xA5 -> UNDEFINED
        '\ufffe',    //       0xA6 -> UNDEFINED
        '\ufffe',    //       0xA7 -> UNDEFINED
        '\ufffe',    //       0xA8 -> UNDEFINED
        '\ufffe',    //       0xA9 -> UNDEFINED
        '\ufffe',    //       0xAA -> UNDEFINED
        '\ufffe',    //       0xAB -> UNDEFINED
        '\ufffe',    //       0xAC -> UNDEFINED
        '\ufffe',    //       0xAD -> UNDEFINED
        '\ufffe',    //       0xAE -> UNDEFINED
        '\ufffe',    //       0xAF -> UNDEFINED
        '\ufffe',    //       0xB0 -> UNDEFINED
        '\ufffe',    //       0xB1 -> UNDEFINED
        '\ufffe',    //       0xB2 -> UNDEFINED
        '\ufffe',    //       0xB3 -> UNDEFINED
        '\ufffe',    //       0xB4 -> UNDEFINED
        '\ufffe',    //       0xB5 -> UNDEFINED
        '\ufffe',    //       0xB6 -> UNDEFINED
        '\ufffe',    //       0xB7 -> UNDEFINED
        '\ufffe',    //       0xB8 -> UNDEFINED
        '\ufffe',    //       0xB9 -> UNDEFINED
        '\ufffe',    //       0xBA -> UNDEFINED
        '\ufffe',    //       0xBB -> UNDEFINED
        '\ufffe',    //       0xBC -> UNDEFINED
        '\ufffe',    //       0xBD -> UNDEFINED
        '\ufffe',    //       0xBE -> UNDEFINED
        '\ufffe',    //       0xBF -> UNDEFINED
        '\ufffe',    //       0xC0 -> UNDEFINED
        '\ufffe',    //       0xC1 -> UNDEFINED
        '\ufffe',    //       0xC2 -> UNDEFINED
        '\ufffe',    //       0xC3 -> UNDEFINED
        '\ufffe',    //       0xC4 -> UNDEFINED
        '\ufffe',    //       0xC5 -> UNDEFINED
        '\ufffe',    //       0xC6 -> UNDEFINED
        '\ufffe',    //       0xC7 -> UNDEFINED
        '\ufffe',    //       0xC8 -> UNDEFINED
        '\ufffe',    //       0xC9 -> UNDEFINED
        '\ufffe',    //       0xCA -> UNDEFINED
        '\ufffe',    //       0xCB -> UNDEFINED
        '\ufffe',    //       0xCC -> UNDEFINED
        '\ufffe',    //       0xCD -> UNDEFINED
        '\ufffe',    //       0xCE -> UNDEFINED
        '\ufffe',    //       0xCF -> UNDEFINED
        '\ufffe',    //       0xD0 -> UNDEFINED
        '\ufffe',    //       0xD1 -> UNDEFINED
        '\ufffe',    //       0xD2 -> UNDEFINED
        '\ufffe',    //       0xD3 -> UNDEFINED
        '\ufffe',    //       0xD4 -> UNDEFINED
        '\ufffe',    //       0xD5 -> UNDEFINED
        '\ufffe',    //       0xD6 -> UNDEFINED
        '\ufffe',    //       0xD7 -> UNDEFINED
        '\ufffe',    //       0xD8 -> UNDEFINED
        '\ufffe',    //       0xD9 -> UNDEFINED
        '\ufffe',    //       0xDA -> UNDEFINED
        '\ufffe',    //       0xDB -> UNDEFINED
        '\ufffe',    //       0xDC -> UNDEFINED
        '\ufffe',    //       0xDD -> UNDEFINED
        '\ufffe',    //       0xDE -> UNDEFINED
        '\ufffe',    //       0xDF -> UNDEFINED
        '\ufffe',    //       0xE0 -> UNDEFINED
        '\ufffe',    //       0xE1 -> UNDEFINED
        '\ufffe',    //       0xE2 -> UNDEFINED
        '\ufffe',    //       0xE3 -> UNDEFINED
        '\ufffe',    //       0xE4 -> UNDEFINED
        '\ufffe',    //       0xE5 -> UNDEFINED
        '\ufffe',    //       0xE6 -> UNDEFINED
        '\ufffe',    //       0xE7 -> UNDEFINED
        '\ufffe',    //       0xE8 -> UNDEFINED
        '\ufffe',    //       0xE9 -> UNDEFINED
        '\ufffe',    //       0xEA -> UNDEFINED
        '\ufffe',    //       0xEB -> UNDEFINED
        '\ufffe',    //       0xEC -> UNDEFINED
        '\ufffe',    //       0xED -> UNDEFINED
        '\ufffe',    //       0xEE -> UNDEFINED
        '\ufffe',    //       0xEF -> UNDEFINED
        '\ufffe',    //       0xF0 -> UNDEFINED
        '\ufffe',    //       0xF1 -> UNDEFINED
        '\ufffe',    //       0xF2 -> UNDEFINED
        '\ufffe',    //       0xF3 -> UNDEFINED
        '\ufffe',    //       0xF4 -> UNDEFINED
        '\ufffe',    //       0xF5 -> UNDEFINED
        '\ufffe',    //       0xF6 -> UNDEFINED
        '\ufffe',    //       0xF7 -> UNDEFINED
        '\ufffe',    //       0xF8 -> UNDEFINED
        '\ufffe',    //       0xF9 -> UNDEFINED
        '\ufffe',    //       0xFA -> UNDEFINED
        '\ufffe',    //       0xFB -> UNDEFINED
        '\ufffe',    //       0xFC -> UNDEFINED
        '\ufffe',    //       0xFD -> UNDEFINED
        '\ufffe',    //       0xFE -> UNDEFINED
        '\ufffe'     //       0xFF -> UNDEFINED
    )

    // encoding:  from unicode to Petscii/Screencodes (0-255)
    private val encodingPetsciiLowercase = decodingPetsciiLowercase.withIndex().associate{it.value to it.index}
    private val encodingPetsciiUppercase = decodingPetsciiUppercase.withIndex().associate{it.value to it.index}
    private val encodingScreencodeLowercase = decodingScreencodeLowercase.withIndex().associate{it.value to it.index}
    private val encodingScreencodeUppercase = decodingScreencodeUppercase.withIndex().associate{it.value to it.index}

    private fun replaceSpecial(chr: Char): Char =
        // characters often used in C like source code can be translated with a little fantasy:
        when(chr) {
            '^' -> '↑'
            '_' -> '▁'
            '{' -> '┤'
            '}' -> '├'
            '|' -> '│'
            '\\' -> '╲'
            '\r' -> '\n'        // to make \r (carriage returrn) equivalent to \n (line feed): RETURN ($0d)
            else -> chr
        }

    fun encodePetscii(text: String, lowercase: Boolean = false): Result<List<UByte>, CharConversionException> {
        fun encodeChar(chr3: Char, lowercase: Boolean): UByte {
            val chr = replaceSpecial(chr3)
            val screencode = if(lowercase) encodingPetsciiLowercase[chr] else encodingPetsciiUppercase[chr]
            return screencode?.toUByte() ?: when (chr) {
                '\u0000' -> 0u
                in '\u8000'..'\u80ff' -> {
                    // special case: take the lower 8 bit hex value directly
                    (chr.code - 0x8000).toUByte()
                }
                else -> {
                    val case = if (lowercase) "lower" else "upper"
                    if(chr.isISOControl())
                        throw CharConversionException("no ${case}Petscii character for char #${chr.code}")
                    else
                        throw CharConversionException("no ${case}Petscii character for char #${chr.code} '${chr}'")
                }
            }
        }

        return try {
            Ok(text.map {
                try {
                    encodeChar(it, lowercase)
                } catch (_: CharConversionException) {
                    encodeChar(it, !lowercase)
                }
            })
        } catch(cx: CharConversionException) {
            Err(cx)
        }
    }

    fun decodePetscii(petscii: Iterable<UByte>, lowercase: Boolean = false): Result<String, CharConversionException> {
        return try {
            Ok(petscii.map {
                val code = it.toInt()
                if(code<0 || code>= decodingPetsciiLowercase.size)
                    throw CharConversionException("petscii $code out of range 0..${decodingPetsciiLowercase.size-1}")
                if(lowercase) decodingPetsciiLowercase[code] else decodingPetsciiUppercase[code]
            }.joinToString(""))
        } catch(ce: CharConversionException) {
            return Err(ce)
        }
    }

    fun encodeScreencode(text: String, lowercase: Boolean = false): Result<List<UByte>, CharConversionException> {
        fun encodeChar(chr3: Char, lowercase: Boolean): UByte {
            val chr = replaceSpecial(chr3)
            val screencode = if(lowercase) encodingScreencodeLowercase[chr] else encodingScreencodeUppercase[chr]
            return screencode?.toUByte() ?: when (chr) {
                '\u0000' -> 0u
                in '\u8000'..'\u80ff' -> {
                    // special case: take the lower 8 bit hex value directly
                    (chr.code - 0x8000).toUByte()
                }
                else -> {
                    val case = if (lowercase) "lower" else "upper"
                    if(chr.isISOControl())
                        throw CharConversionException("no ${case}Screencode character for char #${chr.code}")
                    else
                        throw CharConversionException("no ${case}Screencode character for char #${chr.code} '${chr}'")
                }
            }
        }

        return try {
            Ok(text.map {
                try {
                    encodeChar(it, lowercase)
                } catch (_: CharConversionException) {
                    encodeChar(it, !lowercase)
                }
            })
        } catch(cx: CharConversionException) {
            Err(cx)
        }
    }

    fun decodeScreencode(screencode: Iterable<UByte>, lowercase: Boolean = false): Result<String, CharConversionException> {
        return try {
            Ok(screencode.map {
                val code = it.toInt()
                if(code<0 || code>= decodingScreencodeLowercase.size)
                    throw CharConversionException("screencode $code out of range 0..${decodingScreencodeLowercase.size-1}")
                if (lowercase) decodingScreencodeLowercase[code] else decodingScreencodeUppercase[code]
            }.joinToString(""))
        } catch(ce: CharConversionException) {
            Err(ce)
        }
    }

    fun petscii2scr(petsciicode: UByte, inverseVideo: Boolean): Result<UByte, CharConversionException> {
        val code: UInt = when {
            petsciicode <= 0x1fu -> petsciicode + 128u
            petsciicode <= 0x3fu -> petsciicode.toUInt()
            petsciicode <= 0x5fu -> petsciicode - 64u
            petsciicode <= 0x7fu -> petsciicode - 32u
            petsciicode <= 0x9fu -> petsciicode + 64u
            petsciicode <= 0xbfu -> petsciicode - 64u
            petsciicode <= 0xfeu -> petsciicode - 128u
            petsciicode == 255.toUByte() -> 95u
            else -> return Err(CharConversionException("petscii code out of range"))
        }
        if(inverseVideo) {
            return Ok((code or 0x80u).toUByte())
        }
        return Ok(code.toUByte())
    }

    fun scr2petscii(screencode: UByte): Result<UByte, CharConversionException> {
        val petscii: UInt = when {
            screencode <= 0x1fu -> screencode + 64u
            screencode <= 0x3fu -> screencode.toUInt()
            screencode <= 0x5du -> screencode +123u
            screencode == 0x5e.toUByte() -> 255u
            screencode == 0x5f.toUByte() -> 223u
            screencode <= 0x7fu -> screencode + 64u
            screencode <= 0xbfu -> screencode - 128u
            screencode <= 0xfeu -> screencode - 64u
            screencode == 255.toUByte() -> 191u
            else -> return Err(CharConversionException("screencode out of range"))
        }
        return Ok(petscii.toUByte())
    }
}
