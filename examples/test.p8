%import c64utils
%zeropage basicsafe

~ main {

    sub start() {

        str naam = "irmen"

        ubyte length = len(naam)
        c64scr.print(naam)
        c64scr.print("irmen")
        c64scr.print("irmen2")
        c64scr.print("irmen2")
        ubyte length2 = len("irmen")        ; @todo same string as 'naam'
        ubyte length3 = len("zxfdsfsf")     ; @todo new string
        return
    }

}
