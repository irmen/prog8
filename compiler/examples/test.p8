%output prg
%launcher basic
%option enable_floats

~ main {

sub start() -> () {

    word tx = 0
    word ty = 12 % 5
    float time = 0.0
    _vm_gfx_clearscr(0)

    for X in 3 to 100 step 3/3 {
        A=44
        continue
        continue
        continue
        break
        break
        break
        A=99
    }

    for X in AX {
        A=44
        break
        continue
    }

loop:
    tx = round(sin(time*1.01)*150 + 160)
    ty = round((cos(time)+sin(time/44.1))*60 + 128)
    _vm_gfx_pixel(tx, ty, rnd() % 4 + 7)
    time += 0.02
    goto loop
}
}
