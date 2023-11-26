%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        str[] names = [iso:"irmen", iso:"jurrian", iso:"houtzaagmolen 41", iso:"the Quick Brown Fox jumps Over the LAZY dog!"]

        ; txt.iso()

        uword name
        for name in names {
            txt.print_ub(string.hash(name))
            txt.spc()
            txt.print(name)
            txt.nl()
        }
    }
}
