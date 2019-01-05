%import c64utils
%import c64flt

~ main {

    const uword width = 40
    const uword height = 25

    ; vertices
    byte[8] xcoor = [ -50, -50, -50, -50,  50,  50,  50, 50 ]
    byte[8] ycoor = [ -50, -50,  50,  50, -50, -50,  50, 50 ]
    byte[8] zcoor = [ -50,  50, -50,  50, -50,  50, -50, 50 ]

    ; storage for rotated coordinates
    word[len(xcoor)] rotatedx=0
    word[len(ycoor)] rotatedy=0
    word[len(zcoor)] rotatedz=-1

    sub start()  {
        uword anglex
        uword angley
        uword anglez
        while(true) {
            rotate_vertices(msb(anglex), msb(angley), msb(anglez))
            c64.CLEARSCR()
            draw_edges()
            anglex+=1000
            angley+=333
            anglez+=807
        }
    }

    sub rotate_vertices(ubyte ax, ubyte ay, ubyte az) {
        ; rotate around origin (0,0,0)

        ; set up the 3d rotation matrix values
        word wcosa = cos8(ax) as word
        word wsina = sin8(ax) as word
        word wcosb = cos8(ay) as word
        word wsinb = sin8(ay) as word
        word wcosc = cos8(az) as word
        word wsinc = sin8(az) as word

        word wcosa_sinb = wcosa*wsinb
        lsr(wcosa_sinb)
        lsr(wcosa_sinb)
        lsr(wcosa_sinb)
        lsr(wcosa_sinb)
        lsr(wcosa_sinb)
        lsr(wcosa_sinb)
        lsr(wcosa_sinb)      ; / 128
        word wsina_sinb = wsina*wsinb
        lsr(wsina_sinb)
        lsr(wsina_sinb)
        lsr(wsina_sinb)
        lsr(wsina_sinb)
        lsr(wsina_sinb)
        lsr(wsina_sinb)
        lsr(wsina_sinb)     ; / 128

        float cosa_sinb = wcosa_sinb as float / 128.0
        float sina_sinb = wsina_sinb as float / 128.0
        float Axx = wcosa*wcosb as float / 16384.0
        float Axy = cosa_sinb*(wsinc as float / 128.0) - (wsina*wcosc as float / 16384.0)
        float Axz = cosa_sinb*(wcosc as float / 128.0) + (wsina*wsinc as float / 16384.0)
        float Ayx = wsina*wcosb as float / 16384.0
        float Ayy = sina_sinb*(wsinc as float / 128.0) + (wcosa*wcosc as float / 16384.0)
        float Ayz = sina_sinb*(wcosc as float / 128.0) - (wcosa*wsinc as float / 16384.0)
        float Azx = -wsinb as float / 128.0
        float Azy = wcosb*wsinc as float / 16384.0
        float Azz = wcosb*wcosc as float / 16384.0

        word wAxx = Axx * 128.0 as word
        word wAxy = Axy * 128.0 as word
        word wAxz = Axz * 128.0 as word
        word wAyx = Ayx * 128.0 as word
        word wAyy = Ayy * 128.0 as word
        word wAyz = Ayz * 128.0 as word
        word wAzx = Azx * 128.0 as word
        word wAzy = Azy * 128.0 as word
        word wAzz = Azz * 128.0 as word

        for ubyte i in 0 to len(xcoor)-1 {
            word xc = xcoor[i] as word
            word yc = ycoor[i] as word
            word zc = zcoor[i] as word
            word zz = wAxx*xc + wAxy*yc + wAxz*zc
            lsr(zz)
            lsr(zz)
            lsr(zz)
            lsr(zz)
            lsr(zz)
            lsr(zz)
            lsr(zz)   ; /128
            rotatedx[i] = zz
            zz=wAyx*xc + wAyy*yc + wAyz*zc
            lsr(zz)
            lsr(zz)
            lsr(zz)
            lsr(zz)
            lsr(zz)
            lsr(zz)
            lsr(zz)   ; /128
            rotatedy[i] = zz
            zz = wAzx*xc + wAzy*yc + wAzz*zc
            lsr(zz)
            lsr(zz)
            lsr(zz)
            lsr(zz)
            lsr(zz)
            lsr(zz)
            lsr(zz)   ; /128
            rotatedz[i] = zz
        }
    }

    sub draw_edges() {

        sub toscreenx(float x, float z) -> byte {
            return x/(250.0+z) * (height as float) as byte + width // 2
        }

        sub toscreeny(float y, float z) -> byte {
            return y/(250.0+z) * (height as float) as byte + height // 2
        }

        ; plot the points of the 3d cube
        ; first the points on the back, then the points on the front (painter algorithm)

        for ubyte i in 0 to len(xcoor)-1 {
            word rz = rotatedz[i]
            if rz >= 10 {
                ubyte sx = toscreenx(rotatedx[i] as float, rz as float) as ubyte     ; @todo crash when not using 'as float' for the param!
                ubyte sy = toscreeny(rotatedy[i] as float, rz as float) as ubyte     ; @todo crash when not using 'as float' for the param!
                c64scr.setchrclr(sx, sy, 46, i+2)
            }
        }

        for ubyte i in 0 to len(xcoor)-1 {
            word rz = rotatedz[i]
            if rz < 10 {
                ubyte sx = toscreenx(rotatedx[i] as float, rz as float) as ubyte     ; @todo crash when not using 'as float' for the param!
                ubyte sy = toscreeny(rotatedy[i] as float, rz as float) as ubyte     ; @todo crash when not using 'as float' for the param!
                c64scr.setchrclr(sx, sy, 81, i+2)
            }
        }
    }
}
