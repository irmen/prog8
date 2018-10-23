; %import c64utils
%option enable_floats
%output raw
%launcher none

~ main  {

sub start() {

    const uword width = 160
    const uword height = 128
    ubyte pixely = 255
    const ubyte yoffset = 50

    float y11a = (pixely-30)
    float y11b = (pixely-30)/128
    float y11c = (pixely-30)/128/3.6
    float y11d = (pixely-30)/500+0.4
    float y11e = (pixely-30)/128/3.6+0.4
    float y11f = flt(pixely-30)/128/3.6+0.4
    float y111 = (((pixely-30)))/128/3.6+0.4
    float y1 = (pixely-yoffset)/128/3.6+0.4
    float y1a = flt(pixely-yoffset)/128/3.6+0.4
    float y1a2 = (flt(pixely-yoffset))/128/3.6+0.4
    float y1b = (pixely-40)/128/3.6+0.4
    float y1c = (pixely-40.0)/128/3.6+0.4
    float y2 = flt((pixely-yoffset))/128.0/3.6+0.4
    float y3 = flt((pixely-yoffset))/height/3.6+0.4
    float y4 = flt((pixely-yoffset))/height/3.6+0.4
    return
}

}
