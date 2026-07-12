%import textio
%import dos
%import exec

main {
    sub start() {
        txt.nl()
        txt.nl()
        txt.spc()
        txt.spc()
        txt.spc()
        txt.chrout('1')
        txt.chrout('2')
        txt.chrout('3')
        txt.chrout('4')
        txt.nl()
        txt.print("Boiiiingggg! Hello from prog8.\n\n")
;        long @shared avail = exec.AvailMem(0)
;        pointer mem = exec.AllocMem(256, 0)
;        if mem != 0 {
;            void  dos.PutStr("allocated 256 bytes\n")
;            exec.FreeMem(mem, 256)
;            void  dos.PutStr("freed\n")
;        } else {
;            void  dos.PutStr("alloc failed!\n")
;        }
;
;        ; dos calls
;        void dos.WriteChars("via WriteChars\n", 16)
;
;        sys.wait(100)
;        ;dos.Delay(50)
;
;        long @shared ioerr = dos.IoErr()
;        void dos.PutStr("done.\n")
    }
}

