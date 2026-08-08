%import textio
%zeropage basicsafe

main {
    sub start() {
        struct Foo {
            bool field
            pointer[5] Planes
        }

        ^^Foo bm = [
            true,
            [ $1111,$2222,$3333,$4444,$5555]
        ]

        txt.print("bitplane0: ")
        txt.print_ulhex(bm.Planes[0], true)
        txt.print("\nbitplane1: ")
        txt.print_ulhex(bm.Planes[1], true)
        txt.print("\nbitplane2: ")
        txt.print_ulhex(bm.Planes[2], true)
        txt.print("\nbitplane3: ")
        txt.print_ulhex(bm.Planes[3], true)
        txt.print("\nbitplane4: ")
        txt.print_ulhex(bm.Planes[4], true)
        txt.nl()
        txt.nl()

        ubyte plane = 0
        txt.print("bitplane0: ")
        txt.print_ulhex(bm.Planes[plane], true)
        txt.print("\nbitplane1: ")
        txt.print_ulhex(bm.Planes[plane+1], true)
        txt.print("\nbitplane2: ")
        txt.print_ulhex(bm.Planes[plane+2], true)
        txt.print("\nbitplane3: ")
        txt.print_ulhex(bm.Planes[plane+3], true)
        txt.print("\nbitplane4: ")
        txt.print_ulhex(bm.Planes[plane+4], true)
        txt.nl()
        txt.nl()

        for plane in 0 to 4 {
            ^^ubyte target = bm.Planes[plane] ; + bytesPerRow*y
            txt.print_ulhex(target as long, true)
            txt.spc()
        }
        txt.nl()
    }
}
