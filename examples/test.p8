%import textio
%import bmx
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        bmx.palette_buffer_ptr = memory("palette", 512, 0)
        if bmx.open(8, "desertfish.bmx") {
            if bmx.continue_load(0,0) {
                uword offset = 10*320 + 100
                bmx.width = 100
                bmx.height = 150
                if bmx.save(8, "@:stamp.bmx", 0, offset, 320) {
                    txt.print("save stamp ok\n")
                    return
                }
            }
        }
        txt.print("error: ")
        txt.print(bmx.error_message)
        txt.nl()
    }
}
