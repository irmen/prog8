%import textio
%zeropage basicsafe

main {
    sub start() {
        ; Issue 4: @(...) is always an UNSIGNED byte read. The optimizer rewrite
        ; @(&x) -> x (ConstantFoldingOptimizer / MemoryOptimizers) must not turn the
        ; unsigned memory read into the signed variable, which would sign-extend on widen.
        ; Reading -1 from memory (0xFF) must give 255, not 65535.
        byte @shared sv = -1       ; memory holds 0xFF
        word a = @(&sv)            ; zero-extends the unsigned byte to 255
        txt.print("mem-read signed byte -1: ")
        txt.print_w(a)
        txt.print("  (raw 0xFF; zero-extends to 255)\n")

        ; Contrast: an UNSIGNED byte read via @(&x) is correctly zero-extended.
        ubyte @shared uv = 255     ; memory also holds 0xFF
        word b = @(&uv)            ; zero-extends to 255
        txt.print("mem-read unsigned byte 255: ")
        txt.print_w(b)
        txt.print("  (raw 0xFF; zero-extends to 255)\n")

        txt.print("done\n")
        ;sys.poweroff_system()
    }
}
