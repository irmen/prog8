%import textio
%import diskio
%option no_sysinit
%zeropage basicsafe

;
; Dynamic banking example.
;
; This demonstrates the `@bank bankselector` mechanism: a bank selector subroutine
; that decides at runtime which HiRAM bank contains the target routine.
;
; Contrast this with static banking (`extsub @bank 4 $A003 = func1()`) where the
; bank number is fixed at compile time.  The dynamic approach is useful when libraries
; can be placed in different banks at different times (e.g. after user selection).
;
; In this simple example the mapping is still fixed (each library always goes to
; the same bank) so a static bank would work just well.  The dynamic bank selector
; is used here purely to illustrate the mechanism.
;
; To see the real power of dynamic banking, imagine the bank selector picking a bank
; based on, say, a configuration flag, a user selection at runtime, or the current
; cartridge slot.  For instance: libraries A and B both contain the same set of
; routines but for different languages.  The bank selector reads a global "language"
; variable and returns the bank holding the matching language library.  The calling
; code never needs to know which physical bank the routines live in.
;
; Another pattern is an overlay manager: all overlay routines live in the same bank
; slot (same addresses), and the bank selector simply returns the bank where the
; current overlay is loaded.  You swap overlays by loading a new .bin into that bank
; and updating a global variable that the selector reads.  All extsubs stay the same;
; only the variable changes.
;
; Or a scenario where you don't know the bank number upfront: a library might be
; loaded at runtime into the first available HiRAM bank.  The bank selector maps
; its call-site IDs to whichever bank the loader chose, for instance by consulting
; a table filled in by the loader at runtime.
;

main {

    ; library 1 — init + functions in bank 4
    extsub @bank bankselector $A000 = init_lib1()
    extsub @bank bankselector $A003 = lib1_func1(uword value @AY) clobbers(X) -> uword @AY
    extsub @bank bankselector $A006 = lib1_func2(uword value @AY) clobbers(X) -> uword @AY
    extsub @bank bankselector $A009 = lib1_func3(uword value @AY) clobbers(X) -> uword @AY
    extsub @bank bankselector $A00C = lib1_func4(uword value @AY) clobbers(X) -> uword @AY
    ; library 2 — init + functions in bank 5
    extsub @bank bankselector $A000 = init_lib2()
    extsub @bank bankselector $A003 = lib2_func1(uword value @AY) clobbers(X) -> uword @AY
    extsub @bank bankselector $A006 = lib2_func2(uword value @AY) clobbers(X) -> uword @AY
    extsub @bank bankselector $A009 = lib2_func3(uword value @AY) clobbers(X) -> uword @AY
    extsub @bank bankselector $A00C = lib2_func4(uword value @AY) clobbers(X) -> uword @AY
    ; library 3 — init + functions in bank 6
    extsub @bank bankselector $A000 = init_lib3()
    extsub @bank bankselector $A003 = lib3_func1(uword value @AY) clobbers(X) -> uword @AY
    extsub @bank bankselector $A006 = lib3_func2(uword value @AY) clobbers(X) -> uword @AY
    extsub @bank bankselector $A009 = lib3_func3(uword value @AY) clobbers(X) -> uword @AY
    extsub @bank bankselector $A00C = lib3_func4(uword value @AY) clobbers(X) -> uword @AY

    sub bankselector(ubyte call_id) -> ubyte {
        if call_id <= 4
            return 4          ; library1: init + 4 functions
        else if call_id <= 9
            return 5          ; library2: init + 4 functions
        else
            return 6          ; library3: init + 4 functions
    }

    sub start() {
        ; load and initialize all three library blobs in their respective HiRAM banks.
        ; imagine these banks are not static but dynamically chosen.
        txt.print("\nloading library1 in bank 4...\n")
        cx16.push_rambank(4)
        void diskio.loadlib("library1.bin", $a000)
        init_lib1()
        txt.print("loading library2 in bank 5...\n")
        cx16.rambank(5)
        void diskio.loadlib("library2.bin", $a000)
        init_lib2()
        txt.print("loading library3 in bank 6...\n\n")
        cx16.rambank(6)
        void diskio.loadlib("library3.bin", $a000)
        init_lib3()
        cx16.pop_rambank()

        ; call all 12 routines — the compiler passes values through the extsubs
        uword result

        result = lib1_func1(1111)
        txt.print_uw(result)
        txt.nl()

        result = lib1_func2(2222)
        txt.print_uw(result)
        txt.nl()

        result = lib1_func3(3333)
        txt.print_uw(result)
        txt.nl()

        result = lib1_func4(4444)
        txt.print_uw(result)
        txt.nl()

        result = lib2_func1(5555)
        txt.print_uw(result)
        txt.nl()

        result = lib2_func2(6666)
        txt.print_uw(result)
        txt.nl()

        result = lib2_func3(7777)
        txt.print_uw(result)
        txt.nl()

        result = lib2_func4(8888)
        txt.print_uw(result)
        txt.nl()

        result = lib3_func1(9999)
        txt.print_uw(result)
        txt.nl()

        result = lib3_func2(11110)
        txt.print_uw(result)
        txt.nl()

        result = lib3_func3(12221)
        txt.print_uw(result)
        txt.nl()

        result = lib3_func4(13332)
        txt.print_uw(result)
        txt.nl()
    }
}
