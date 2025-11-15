%import textio
%zeropage basicsafe

main {

    sub start() {
        repeat test()
    }

    sub test() {
        txt.cls()
        txt.plot(10, 10)
        txt.print("enter four digit access code:")
        txt.plot(20, 12)
        txt.print("╭──────╮")
        txt.plot(20, 13)
        txt.print("│      │")
        txt.plot(20, 14)
        txt.print("╰──────╯")


        txt.plot(22, 13)
        cx16.blink_enable(true)
        str code = "????"

        ubyte numbers = 0
        repeat {
            ubyte char = txt.waitkey()
            when char {
                '0' to '9' -> {
                    if numbers<4 {
                        cx16.blink_enable(false)
                        txt.chrout(char)
                        cx16.blink_enable(true)
                        code[numbers] = char
                        numbers++
                    }
                }
                '\r' -> {
                    if numbers==4 {
                        cx16.blink_enable(false)
                        break
                    }
                }
                20, 25 -> {
                    if numbers>0 {
                        cx16.blink_enable(false)
                        txt.chrout(157)     ; cursor left
                        txt.spc()           ; clear digit
                        txt.chrout(157)     ; cursor left
                        cx16.blink_enable(true)
                        numbers--
                    }
                }
            }
        }

        txt.print("\n\n\ncode entered: ")
        txt.print(code)

        sys.wait(120)
    }
}
