%import textio
%import strings
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        txt.lowercase()
        edit_file("readme.md")
    }

    sub edit_file(uword filename) {
        ; activate rom based x16edit, see https://github.com/stefan-b-jakobsson/x16-edit/tree/master/docs
        ubyte x16edit_bank = cx16.search_x16edit()
        if x16edit_bank<255 {
            sys.enable_caseswitch()     ; workaround for character set issue in X16Edit 0.7.1
            ubyte filename_length = 0
            if filename!=0
                filename_length = strings.length(filename)
            ubyte old_bank = cx16.getrombank()
            cx16.rombank(x16edit_bank)
            ; NOTE: this entrypoint doesn't exist yet in the X16Edit version in current ROMS (V49)?
            cx16.x16edit_loadfile_options2(1, 255, filename,
                mkword(%00000011, filename_length),         ; auto-indent and word-wrap enable
                mkword(80, 4),          ; wrap and tabstop
                mkword(11<<4 | 7, 8),
                mkword(0,0),
                178, 0)         ; starting line number
            cx16.rombank(old_bank)
            sys.disable_caseswitch()
        } else {
            txt.print("error: no x16edit found in rom")
            sys.wait(180)
        }
    }
}
