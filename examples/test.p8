%import c64utils
%import c64flt

~ main {

    sub start()  {

        ubyte ub
        byte b
        word w
        uword uw


        ubyte[2] uba
        byte[2] ba
        word[2] wa
        uword[2] uwa
        str s
        str_p sp
        str_s ss
        str_ps sps

        s = ub as str
        sp = ub as str_p
        ss = ub as str_s
        sps = ub as str_ps
        s = b as str
        sp = b as str_p
        ss = b as str_s
        sps = b as str_ps
        s = w as str
        sp = w as str_p
        ss = w as str_s
        sps = w as str_ps
        s = uw as str
        sp = uw as str_p
        ss = uw as str_s
        sps = uw as str_ps


    }

}

