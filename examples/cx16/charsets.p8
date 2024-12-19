%import textio
%zeropage basicsafe

main {
    str buf = "?" * 20

    sub start() {
        txt.print("a demonstration of the various non-petscii character sets in the x16.")
        wait()
        latin()
        wait()
        cyrillic()
        wait()
        eastern()
        wait()
        kata()
        wait()
        ibmpc()
        wait()
    }

    sub latin() {
        txt.iso()
        repeat 3 txt.nl()
        write_screencodes(iso:"Latin: Le garçon n'a pas acheté d'œuf.")
        txt.print(iso:"Latin: Le garçon n'a pas acheté d'œuf.")
    }

    sub cyrillic() {
        txt.iso5()
        repeat 3 txt.nl()
        write_screencodes(iso5:"Cyrillic: 'Хозяин и Работник' написана Лев Толстой.")
        txt.print(iso5:"Cyrillic: 'Хозяин и Работник' написана Лев Толстой.")
    }

    sub eastern() {
        txt.iso16()
        repeat 3 txt.nl()
        write_screencodes(iso16:"Eastern European: zażółć gęślą jaźń")
        txt.print(iso16:"Eastern European: zażółć gęślą jaźń")
    }

    sub ibmpc() {
        txt.color2(5,0)
        cx16.set_screen_mode(1)
        txt.cp437()
        repeat 3 txt.nl()
        write_screencodes(cp437:"≈ IBM Pc ≈ ÇüéâäàåçêëèïîìÄ ░▒▓│┤╡╢╖╕╣║╗╝╜╛┐ ☺☻♥♦♣♠•◘○◙♂♀♪♫☼ ►◄↕‼¶§▬↨↑↓→←∟↔▲▼")
        ; regular print() won't work because of control codes (<32) in this one.
        txt.print_lit(cp437:"≈ IBM Pc ≈ ÇüéâäàåçêëèïîìÄ ░▒▓│┤╡╢╖╕╣║╗╝╜╛┐ ☺☻♥♦♣♠•◘○◙♂♀♪♫☼ ►◄↕‼¶§▬↨↑↓→←∟↔▲▼")
        txt.nl()
    }

    sub kata() {
        txt.kata()
        repeat 3 txt.nl()
        write_screencodes(kata:"Katakana: ｱﾉ ﾆﾎﾝｼﾞﾝ ﾜ ｶﾞｲｺｸｼﾞﾝ ﾉ ﾆﾎﾝｺﾞ ｶﾞ ｼﾞｮｳｽﾞ ﾀﾞｯﾃ ﾕｯﾀ｡ ## がが ## ガガ")
        txt.print(kata:"Katakana: ｱﾉ ﾆﾎﾝｼﾞﾝ ﾜ ｶﾞｲｺｸｼﾞﾝ ﾉ ﾆﾎﾝｺﾞ ｶﾞ ｼﾞｮｳｽﾞ ﾀﾞｯﾃ ﾕｯﾀ｡ ## がが ## ガガ")
    }

    sub wait() {
        txt.print("\n\npress enter: ")
        void txt.input_chars(buf)
    }

    sub write_screencodes(str message) {
        ubyte column=0
        repeat {
            if message[column]==0
                return
            txt.setchr(column, 1, message[column])
            column++
        }
    }
}
