%import floats
%import textio
%option no_sysinit
%zeropage basicsafe

main {

    uword b_wordvar
    ubyte b_bb =123
    float b_fl
    ubyte[10] b_emptyarray
    ubyte[10] b_filledarray = [1,2,3,4,5,6,7,8,9,10]
    float[3] b_floatarray
    uword[3] b_wordarray

    sub start() {
        uword wordvar
        float fl
        ubyte bb =123
        ubyte[10] emptyarray
        ubyte[10] filledarray = [1,2,3,4,5,6,7,8,9,10]
        float[3] floatarray
        uword[3] wordarray

        txt.print("**subroutine scope**\n")
        txt.print("uninit wordvar=")
        txt.print_uw(wordvar)
        txt.print("\nuninit float=")
        floats.print_f(fl)
        txt.print("\ninit bb=")
        txt.print_ub(bb)
        txt.print("\nuninit emptyarray[2]=")
        txt.print_ub(emptyarray[2])
        txt.print("\nuninit wordarray[2]=")
        txt.print_uw(wordarray[2])
        txt.print("\nuninit floatarray[2]=")
        floats.print_f(floatarray[2])
        txt.print("\ninit filledarray[2]=")
        txt.print_ub(filledarray[2])

        txt.print("\n**block scope**\n")
        txt.print("uninit b_wordvar=")
        txt.print_uw(b_wordvar)
        txt.print("\nuninit b_float=")
        floats.print_f(b_fl)
        txt.print("\ninit b_bb=")
        txt.print_ub(b_bb)
        txt.print("\nuninit b_emptyarray[2]=")
        txt.print_ub(b_emptyarray[2])
        txt.print("\nuninit b_wordarray[2]=")
        txt.print_uw(b_wordarray[2])
        txt.print("\nuninit b_floatarray[2]=")
        floats.print_f(b_floatarray[2])
        txt.print("\ninit b_filledarray[2]=")
        txt.print_ub(b_filledarray[2])

        txt.print("\n\nadding 42 to all values.\n")
        wordvar += 42
        bb += 42
        fl += 42.42
        floatarray[2] += 42.42
        wordarray[2] += 42
        emptyarray[2] += 42
        filledarray[2] += 42
        b_wordvar += 42
        b_bb += 42
        b_fl += 42.42
        b_floatarray[2] += 42.42
        b_wordarray[2] += 42
        b_emptyarray[2] += 42
        b_filledarray[2] += 42

        txt.print("\n**subroutine scope**\n")
        txt.print("uninit wordvar=")
        txt.print_uw(wordvar)
        txt.print("\nuninit float=")
        floats.print_f(fl)
        txt.print("\ninit bb=")
        txt.print_ub(bb)
        txt.print("\nuninit emptyarray[2]=")
        txt.print_ub(emptyarray[2])
        txt.print("\nuninit wordarray[2]=")
        txt.print_uw(wordarray[2])
        txt.print("\nuninit floatarray[2]=")
        floats.print_f(floatarray[2])
        txt.print("\ninit filledarray[2]=")
        txt.print_ub(filledarray[2])

        txt.print("\n**block scope**\n")
        txt.print("uninit b_wordvar=")
        txt.print_uw(b_wordvar)
        txt.print("\nuninit b_float=")
        floats.print_f(b_fl)
        txt.print("\ninit b_bb=")
        txt.print_ub(b_bb)
        txt.print("\nuninit b_emptyarray[2]=")
        txt.print_ub(b_emptyarray[2])
        txt.print("\nuninit b_wordarray[2]=")
        txt.print_uw(b_wordarray[2])
        txt.print("\nuninit b_floatarray[2]=")
        floats.print_f(b_floatarray[2])
        txt.print("\ninit b_filledarray[2]=")
        txt.print_ub(b_filledarray[2])

        txt.print("\n\nrun again to see effect of re-init.\n")
    }
}
