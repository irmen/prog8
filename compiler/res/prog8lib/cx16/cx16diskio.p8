; Cx16 specific disk drive I/O routines.
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0

%import diskio

cx16diskio {

    ; Use kernal LOAD routine to load the given program file in memory.
    ; This is similar to Basic's  LOAD "filename",drive  /  LOAD "filename",drive,1
    ; If you don't give an address_override, the location in memory is taken from the 2-byte file header.
    ; If you specify a custom address_override, the first 2 bytes in the file are ignored
    ; and the rest is loaded at the given location in memory.
    ; Returns the end load address+1 if successful or 0 if a load error occurred.
    ; You can use the load_size() function to calcuate the size of the file that was loaded.
    sub load(ubyte drivenumber, uword filenameptr, ubyte bank, uword address_override) -> uword {
        cx16.rambank(bank)
        return diskio.load(drivenumber, filenameptr, address_override)
    }

    ; Use kernal LOAD routine to load the given file in memory.
    ; INCLUDING the first 2 bytes in the file: no program header is assumed in the file.
    ; The load address is mandatory. Returns the number of bytes loaded.
    ; If you load into regular system ram, use cx16.getrambank() for the bank argument,
    ; or alternatively make sure to reset the correct ram bank yourself after the load!
    ; Returns the end load address+1 if successful or 0 if a load error occurred.
    ; You can use the load_size() function to calcuate the size of the file that was loaded.
    sub load_raw(ubyte drivenumber, uword filenameptr, ubyte bank, uword address) -> uword {
        cx16.rambank(bank)
        return diskio.load_headerless_cx16(drivenumber, filenameptr, address, true)
    }

    ; For use directly after a load or load_raw call (don't mess with the ram bank yet):
    ; Calculates the number of bytes loaded (files > 64Kb ar truncated to 16 bits)
    sub load_size(ubyte startbank, uword startaddress, uword endaddress) -> uword {
        return $2000 * (cx16.getrambank() - startbank) + endaddress - startaddress
    }

    asmsub vload(str name @R0, ubyte device @Y, ubyte bank @A, uword address @R1) -> ubyte @A {
        ; -- like the basic command VLOAD "filename",device,bank,address
        ;    loads a file into Vera's video memory in the given bank:address, returns success in A
        %asm {{
            ; -- load a file into video ram
            phx
            pha
            tya
            tax
            lda  #1
            ldy  #0
            jsr  c64.SETLFS
            lda  cx16.r0
            ldy  cx16.r0+1
            jsr  prog8_lib.strlen
            tya
            ldx  cx16.r0
            ldy  cx16.r0+1
            jsr  c64.SETNAM
            pla
            clc
            adc  #2
            ldx  cx16.r1
            ldy  cx16.r1+1
            stz  P8ZP_SCRATCH_B1
            jsr  c64.LOAD
            bcs  +
            inc  P8ZP_SCRATCH_B1
    +       jsr  c64.CLRCHN
            lda  #1
            jsr  c64.CLOSE
            plx
            lda  P8ZP_SCRATCH_B1
            rts
        }}
    }

}
