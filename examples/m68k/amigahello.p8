; %import textio

exec {
    %option no_symbol_prefixing, ignore_unused

    &pointer ExecBase = 4

    asmsub OpenLibrary(str libname @A1, long version @D0) -> pointer @D0 {
        %asm {{
            move.l  exec.ExecBase,a6
            jmp     -552(a6)        ; OpenLibrary
        }}
    }

    asmsub CloseLibrary(pointer handle @A1) {
        %asm {{
            move.l  exec.ExecBase,a6
            jmp     -414(a6)        ; CloseLibrary
        }}
    }
}

dos {
    %option no_symbol_prefixing, ignore_unused

    pointer @shared DosBase

    asmsub Output() -> pointer @D0 {
        %asm {{
            move.l  dos.DosBase,a6
            jmp     -60(a6)         ; Output
        }}
    }

    asmsub Write(pointer handle @D1, ^^ubyte data @D2, long length @D3) -> long @D0 {
        %asm {{
            move.l  dos.DosBase,a6
            jmp     -48(a6)         ; Write
        }}
    }
}

main {

    str message = "Hello World! Greetings from Prog8.\n"
    uword msglen = 35       ; TODO strlen

    sub start() {
        dos.DosBase = exec.OpenLibrary("dos.library", 0)
        if dos.DosBase!=0 {
            pointer filehandle = dos.Output()
            if filehandle!=0 {
                void dos.Write(filehandle, message, msglen)
            }
            exec.CloseLibrary(dos.DosBase)
        }
    }
}

