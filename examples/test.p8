%import c64utils
%import c64flt

~ main {

    sub start()  {

; @todo create word function           ;c64.SPXY[i] = (rnd() as uword) * 256 + (50+25*i)
; @todo more efficient +1/-1 additions in expressions

        float f1 = c64flt.TWOPI

        c64flt.print_fln(3.1415)
        c64flt.print_fln(f1)
        f1 = 3.1415
        f1 = 3.1415
        f1 = 3.1415
        f1 = 3.1415
    }
}
