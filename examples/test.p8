%import textio

main {
    sub start() {
        str name1 = "name1"
        str name2 = "name2"
        uword[] names = [name1, name2, "name3"]
        cx16.r0++
        uword ww
        for ww in names {
            txt.print(ww)
            txt.spc()
        }
        txt.nl()

        names = [1111,2222,3333]
        for ww in names {
            txt.print_uw(ww)
            txt.spc()
        }
        txt.nl()
        txt.print("end.")
        %asm {{
            lda  #$3e
        }}
    }
}

