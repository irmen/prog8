plane {
   struct Point {
       ubyte x
       ubyte y
   }
}

mytxt {
    %option no_symbol_prefixing

    sub print_ub(ubyte value) {
        cx16.r0L++
    }
}

mytxt {
    %option merge
    sub print_pt(^^plane.Point p) {
        mytxt.print_ub(p.x)
        mytxt.print_ub(p.y)
    }
}

main {
    sub start() {
        ^^plane.Point origin = ^^plane.Point:[0,0]
        mytxt.print_pt(origin)
    }
}
