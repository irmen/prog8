; Amiga asl.library file requester example
; Requires kickstart 2.0+ (asl.library)

%import asllib
%import textio
%import exec

main {
    sub start() {
        if not asllib.openlib() {
            txt.print("could not open asl.library\n")
            return
        }
        defer asllib.closelib()

        ^^asllib.FileRequester fr = asllib.AllocAslRequest(asllib.ASL_FileRequest, 0) as^^ asllib.FileRequester
        if fr == 0 {
            txt.print("could not allocate file requester\n")
            return
        }
        defer asllib.FreeAslRequest(fr as pointer)

        ; set a title via tags would need a tag list; keep it simple here
        bool ok = asllib.AslRequest(fr as pointer, 0)
        if not ok {
            txt.print("requester cancelled\n")
            return
        }

        txt.print("drawer: ")
        if fr.Drawer != 0
            txt.print(fr.Drawer)
        else
            txt.print("(none)")
        txt.print("\n")

        txt.print("file:   ")
        if fr.File != 0
            txt.print(fr.File)
        else
            txt.print("(none)")
        txt.print("\n")
    }
}