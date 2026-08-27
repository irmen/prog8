%import dos
%import textio
%import ptplayer

main {
    sub start() {
        if ptplayer.install() {
            txt.print("ptplayer init ok\n")

            str modname = "?" * 60
            txt.print("name of mod file to load: ")
            void txt.input_chars(modname)

            pointer fh = dos.Open(modname, dos.MODE_OLDFILE)
            if fh!=0 {

                ; get filesize
                void dos.Seek(fh, 0, dos.OFFSET_END)
                long modSize = dos.Seek(fh, 0, dos.OFFSET_CURRENT)
                void dos.Seek(fh, 0, dos.OFFSET_BEGINNING)

                pointer mod = exec.AllocMem(modSize, exec.MEMF_CHIP | exec.MEMF_PUBLIC)
                if mod!=0 {
                    txt.print("loading...\n")
                    if dos.Read(fh, mod, modSize)==modSize {
                        void dos.Close(fh)
                        ptplayer.init(mod)

                        txt.print("\nsong name: ")
                        txt.print(ptplayer.songname())
                        txt.print("\nnumber of patterns: ")
                        txt.print_ub(ptplayer.numpatterns())
                        txt.nl()
                        txt.nl()

                        txt.print("playing... (press CTRL+C to stop)\n")
                        txt.cursor_off()

                        ptplayer.enable()
                        do {
                            print_song_vars()
                            dos.Delay(1)
                        } until dos.CheckSignal(dos.SIGBREAKF_CTRL_C)!=0
                        ptplayer.disable()

                        txt.cursor_on()
                        txt.nl()

                        ptplayer.end()
                    } else {
                        txt.print("load error\n")
                        void dos.Close(fh)
                    }
                    exec.FreeMem(mod, modSize)
                } else {
                    void dos.Close(fh)
                }

            } else {
                txt.print("load error\n")
            }

            ptplayer.remove()
        } else {
            txt.print("ptplayer failed to init\n")
        }
    }

    sub print_song_vars() {
        ubyte pos, pattern, row = ptplayer.currentpos()
        ubyte vol1, vol2, vol3, vol4 = ptplayer.volumes()
        bool note1, note2, note3, note4 = ptplayer.notestrike()
        txt.print("\r  ")
        txt.print_ub0(pos)
        txt.print("  ")
        txt.print_ub0(pattern)
        txt.print("  ")
        txt.print_ub0(row)
        txt.print("  ")
        txt.print_ub(note1 as ubyte)
        txt.chrout(':')
        txt.print_ub(note2 as ubyte)
        txt.chrout(':')
        txt.print_ub(note3 as ubyte)
        txt.chrout(':')
        txt.print_ub(note4 as ubyte)
    }
}
