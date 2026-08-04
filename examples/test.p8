%import textio
%import floats
%zeropage basicsafe

main {
    struct S { uword value }

    sub start() {
        ; Issue 1: msb/lsb of a struct field via pointer deref.
        ; Optimizer rewrites msb(^^field)/lsb(^^field) -> @(&field+offset).
        ; Correct on little-endian 6502 but WRONG on big-endian m68k,
        ; so the rewrite is now gated to is6502 only.
        ^^S p = memory("sstorage", 2, 0)
        p.value = $1234
        ubyte lo = lsb(p.value)
        ubyte hi = msb(p.value)
        txt.print("struct msb/lsb: lo=")
        txt.print_ub(lo)
        txt.print(" hi=")
        txt.print_ub(hi)
        txt.print("  (expected lo=52 hi=18)\n")

        ; Issue 2: word + (byte<<1 as uword) -> (word+byte)+byte.
        ; 6502-specific addressing-mode win; on m68k a zero-extend is not free,
        ; so the rewrite is now gated to is6502 only.
        word @shared w = 1000
        byte @shared b = 5
        word result = w + (b as uword << 1)
        txt.print("w + b<<1: ")
        txt.print_w(result)
        txt.print("  (expected 1010)\n")

        ; Issue 3: float == 0.0 -> sgn(float) == 0.
        ; 6502 MFLPT trick; on m68k a direct ftst/fbcc is far better,
        ; so the rewrite is now gated to is6502 only.
        float @shared f = 3.14
        if f == 0.0
            txt.print("float f: zero\n")
        else
            txt.print("float f: nonzero\n")
        float @shared g = 0.0
        if g == 0.0
            txt.print("float g: zero\n")
        else
            txt.print("float g: nonzero\n")

        txt.print("done\n")
        ;sys.poweroff_system()
    }
}
