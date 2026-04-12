%import textio

plane {
   struct Point {
       ubyte x
       ubyte y
   }
}

txt {
    %option merge
    sub print_pt(^^plane.Point p) {
        txt.chrout('(')
        txt.print_ub(p.x)
        txt.chrout(',')
        txt.print_ub(p.y)
        txt.chrout(')')
    }
}

main {
    sub start() {
        ^^plane.Point origin = ^^plane.Point:[0,0]
        txt.print_pt(origin)
    }
}
