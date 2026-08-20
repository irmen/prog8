%import dos
%import textio
%import ptplayer

main {
    sub start() {
        if ptplayer.mt_install() {
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
                        ptplayer.mt_init(mod)
                        ptplayer.mt_enable()
                        txt.print("playing...\n")
                        dos.Delay(1000)
                        ptplayer.mt_disable()
                    } else {
                        txt.print("load error\n")
                    }
                    exec.FreeMem(mod, modSize)
                }

                ptplayer.mt_end()

            } else {
                txt.print("load error\n")
            }

            ptplayer.mt_remove()
        } else {
            txt.print("ptplayer failed to init\n")
        }
    }
}
