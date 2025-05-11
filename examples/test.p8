%import textio
%zeropage basicsafe

main {
    str buf = "?" * 20

    sub start() {
        latin()
        wait()
        cyrillic()
        wait()
        eastern()
        wait()
        kata()
        wait()
    }

    sub latin() {
        txt.iso()
        repeat 3 txt.nl()
        txt.print(iso:"Latin: Le garçon\nn'a pas acheté\nd'œuf.")
    }

    sub cyrillic() {
        txt.iso5()
        repeat 3 txt.nl()
        txt.print(iso5:"Cyrillic: 'Хозяин и Работник'\nнаписана Лев\nТолстой.")
    }

    sub eastern() {
        txt.iso16()
        repeat 3 txt.nl()
        txt.print(iso16:"Eastern European: zażółć \ngęślą\njaźń")
    }

    sub kata() {
        txt.kata()
        repeat 3 txt.nl()
        txt.print(kata:"Katakana: ｱﾉ ﾆﾎﾝｼﾞﾝ ﾜ ｶﾞｲｺｸｼﾞﾝ \nﾉ ﾆﾎﾝｺﾞ ｶﾞ ｼﾞｮｳｽﾞ \nﾀﾞｯﾃ ﾕｯﾀ｡ ## がが ## ガガ")
    }

    sub wait() {
        txt.print("\r\rpress enter: ")
        void txt.input_chars(buf)
    }
}
