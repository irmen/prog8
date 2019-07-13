%import c64utils
%zeropage basicsafe

~ main {

    struct Color {
        uword red
        ubyte green
        ubyte blue
    }

    str naam = "irmen"
    word[] array = [1,2,3,4]
    uword uw = $ab12
    Color rgb = [255,128,0]
    Color rgb2 = [111,222,33]

    ubyte @zp zpvar=99

    sub start() {

        uword fake_address

        fake_address = &naam
        c64scr.print_uwhex(1, fake_address)
        c64scr.print(", ")

        fake_address = &array
        c64scr.print_uwhex(1, fake_address)
        c64scr.print(", ")

        fake_address = &rgb
        c64scr.print_uwhex(1, fake_address)
        c64scr.print("\n")

        ; @todo only works once reference types are actually references:
        ;str name2 = naam              ; @todo name2 points to same str as naam
        ;str name2 = fake_address      ; @todo fake_address hopefully points to a str
        ;Color colz = fake_address     ; @todo fake_address hopefully points to a Color

        return
    }

}
