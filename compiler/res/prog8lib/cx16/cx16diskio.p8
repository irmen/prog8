; Cx16 specific disk drive I/O routines.
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0

%import diskio

cx16diskio {

    ; Use kernal LOAD routine to load the given program file in memory.
    ; This mimimics Basic's  LOAD "filename",drive  /  LOAD "filename",drive,1
    ; If you don't give an address_override, the location in memory is taken from the 2-byte file header.
    ; If you specify a custom address_override, the first 2 bytes in the file are ignored
    ; and the rest is loaded at the given location in memory.
    ; Returns the number of bytes loaded (truncated to 16 bits, if the file is larger than 64 Kb,
    ; you'll have to compensate yourself by checking the ram banks).
    sub load(ubyte drivenumber, uword filenameptr, ubyte bank, uword address_override) -> uword {
        cx16.rambank(bank)
        uword size = diskio.load(drivenumber, filenameptr, address_override)
        if size
            return size + $2000 * (cx16.getrambank() - bank)
        return 0
    }

    ; Use kernal LOAD routine to load the given file in memory.
    ; INCLUDING the first 2 bytes in the file: no program header is assumed in the file.
    ; This is different from Basic's LOAD instruction which always skips the first two bytes.
    ; The load address is mandatory. Returns the number of bytes loaded.
    ; Returns the number of bytes loaded (truncated to 16 bits, if the file is larger than 64 Kb,
    ; you'll have to compensate yourself by checking the ram banks).
    sub load_raw(ubyte drivenumber, uword filenameptr, ubyte bank, uword address) -> uword {
        cx16.rambank(bank)
        uword size = diskio.load_raw(drivenumber, filenameptr, address)
        if size
            return size + $2000 * (cx16.getrambank() - bank)
        return 0
    }

}
