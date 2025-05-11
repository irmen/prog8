package prog8.code.target.encodings

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.io.CharConversionException
import java.nio.charset.Charset


object JapaneseCharacterConverter {
    // adapted from https://github.com/raminduw/Japanese-Character-Converter

    private val ZENKAKU_KATAKANA = charArrayOf(
        'ァ', 'ア', 'ィ', 'イ', 'ゥ',
        'ウ', 'ェ', 'エ', 'ォ', 'オ', 'カ', 'ガ', 'キ', 'ギ', 'ク', 'グ', 'ケ', 'ゲ',
        'コ', 'ゴ', 'サ', 'ザ', 'シ', 'ジ', 'ス', 'ズ', 'セ', 'ゼ', 'ソ', 'ゾ', 'タ',
        'ダ', 'チ', 'ヂ', 'ッ', 'ツ', 'ヅ', 'テ', 'デ', 'ト', 'ド', 'ナ', 'ニ', 'ヌ',
        'ネ', 'ノ', 'ハ', 'バ', 'パ', 'ヒ', 'ビ', 'ピ', 'フ', 'ブ', 'プ', 'ヘ', 'ベ',
        'ペ', 'ホ', 'ボ', 'ポ', 'マ', 'ミ', 'ム', 'メ', 'モ', 'ャ', 'ヤ', 'ュ', 'ユ',
        'ョ', 'ヨ', 'ラ', 'リ', 'ル', 'レ', 'ロ', 'ヮ', 'ワ', 'ヰ', 'ヱ', 'ヲ', 'ン',
        'ヴ', 'ヵ', 'ヶ'
    )

    private val HANKAKU_HIRAGANA = charArrayOf(
        'ぁ', 'あ', 'ぃ', 'い', 'ぅ', 'う', 'ぇ', 'え',
        'ぉ', 'お', 'か', 'が', 'き', 'ぎ', 'く', 'ぐ',
        'け', 'げ', 'こ', 'ご', 'さ', 'ざ', 'し', 'じ',
        'す', 'ず', 'せ', 'ぜ', 'そ', 'ぞ', 'た', 'だ',
        'ち', 'ぢ', 'っ', 'つ', 'づ', 'て', 'で', 'と',
        'ど', 'な', 'に', 'ぬ', 'ね', 'の', 'は', 'ば',
        'ぱ', 'ひ', 'び', 'ぴ', 'ふ', 'ぶ', 'ぷ', 'へ',
        'べ', 'ぺ', 'ほ', 'ぼ', 'ぽ', 'ま', 'み', 'む',
        'め', 'も', 'ゃ', 'や', 'ゅ', 'ゆ', 'ょ', 'よ',
        'ら', 'り', 'る', 'れ', 'ろ', 'ゎ', 'わ', 'ゐ',
        'ゑ', 'を', 'ん', 'ゔ', 'ゕ', 'ゖ'
    )

    private val HANKAKU_KATAKANA = arrayOf(
        "ｧ", "ｱ", "ｨ", "ｲ", "ｩ",
        "ｳ", "ｪ", "ｴ", "ｫ", "ｵ", "ｶ", "ｶﾞ", "ｷ", "ｷﾞ", "ｸ", "ｸﾞ", "ｹ",
        "ｹﾞ", "ｺ", "ｺﾞ", "ｻ", "ｻﾞ", "ｼ", "ｼﾞ", "ｽ", "ｽﾞ", "ｾ", "ｾﾞ", "ｿ",
        "ｿﾞ", "ﾀ", "ﾀﾞ", "ﾁ", "ﾁﾞ", "ｯ", "ﾂ", "ﾂﾞ", "ﾃ", "ﾃﾞ", "ﾄ", "ﾄﾞ",
        "ﾅ", "ﾆ", "ﾇ", "ﾈ", "ﾉ", "ﾊ", "ﾊﾞ", "ﾊﾟ", "ﾋ", "ﾋﾞ", "ﾋﾟ", "ﾌ",
        "ﾌﾞ", "ﾌﾟ", "ﾍ", "ﾍﾞ", "ﾍﾟ", "ﾎ", "ﾎﾞ", "ﾎﾟ", "ﾏ", "ﾐ", "ﾑ", "ﾒ",
        "ﾓ", "ｬ", "ﾔ", "ｭ", "ﾕ", "ｮ", "ﾖ", "ﾗ", "ﾘ", "ﾙ", "ﾚ", "ﾛ", "ﾜ",
        "ﾜ", "ｲ", "ｴ", "ｦ", "ﾝ", "ｳﾞ", "ｶ", "ｹ"
    )

    private val ZENKAKU_KATAKANA_FIRST_CHAR_CODE = ZENKAKU_KATAKANA.first().code
    private val HANKAKU_HIRAGANA_FIRST_CHAR_CODE = HANKAKU_HIRAGANA.first().code

    private fun zenkakuKatakanaToHankakuKatakana(c: Char): String = if (c in ZENKAKU_KATAKANA) HANKAKU_KATAKANA[c.code - ZENKAKU_KATAKANA_FIRST_CHAR_CODE] else c.toString()
    private fun hankakuKatakanaToZenkakuKatakana(c: Char): Char = if (c in HANKAKU_HIRAGANA) ZENKAKU_KATAKANA[c.code - HANKAKU_HIRAGANA_FIRST_CHAR_CODE] else c

    fun zenkakuKatakanaToHankakuKatakana(s: String): String = buildString {
        for (element in s) {
            val converted = hankakuKatakanaToZenkakuKatakana(element)
            val convertedChar = zenkakuKatakanaToHankakuKatakana(converted)
            append(convertedChar)
        }
    }
}

object KatakanaEncoding {
    val charset: Charset = Charset.forName("JIS_X0201")

    fun encode(str: String, newlineToCarriageReturn: Boolean): Result<List<UByte>, CharConversionException> {
        return try {
            val mapped = str.map { chr ->
                when (chr) {
                    '\n' -> if(newlineToCarriageReturn) 13u else 10u

                    '\u0000' -> 0u
                    '\u00a0' -> 0xa0u // $a0 isn't technically a part of JIS X 0201 spec, and so we need to handle this ourselves

                    '♥' -> 0xe3u
                    '♦' -> 0xe4u
                    '♣' -> 0xe5u
                    '♠' -> 0xe6u

                    '大' -> 0xeau
                    '中' -> 0xebu
                    '小' -> 0xecu
                    '百' -> 0xedu
                    '千' -> 0xeeu
                    '万' -> 0xefu
                    '♪' -> 0xf0u
                    '土' -> 0xf1u
                    '金' -> 0xf2u
                    '木' -> 0xf3u
                    '水' -> 0xf4u
                    '火' -> 0xf5u
                    '月' -> 0xf6u
                    '日' -> 0xf7u
                    '時' -> 0xf8u
                    '分' -> 0xf9u
                    '秒' -> 0xfau
                    '年' -> 0xfbu
                    '円' -> 0xfcu
                    '人' -> 0xfdu
                    '生' -> 0xfeu
                    '〒' -> 0xffu
                    in '\u8000'..'\u80ff' -> {
                        // special case: take the lower 8 bit hex value directly
                        (chr.code - 0x8000).toUByte()
                    }
                    else -> charset.encode(chr.toString())[0].toUByte()
                }
            }
            Ok(mapped)
        } catch (ce: CharConversionException) {
            Err(ce)
        }
    }

    fun decode(bytes: Iterable<UByte>, newlineToCarriageReturn: Boolean): Result<String, CharConversionException> {
        return try {
            Ok(String(bytes.map {
                when(it) {
                    13u.toUByte() -> if(newlineToCarriageReturn) 10 else 13
                    else -> it.toByte()
                }
            }.toByteArray(), charset))
        } catch (ce: CharConversionException) {
            Err(ce)
        }
    }
}