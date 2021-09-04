%import textio
%zeropage dontuse

main {

label:
    sub start() {

        txt.print("hey\n")
        %breakpoint
        txt.print("hey2\n")

;        sub2(&label)
;        sub2(&label_local)
;        sub2(&main.sub2.label_in_sub2)
;        uword xx = &label_local
;        txt.print_uwhex(xx, true)
;        txt.nl()
;        xx = &label
;        txt.print_uwhex(xx, true)
;        txt.nl()
;        xx = &main.label
;        txt.print_uwhex(xx, true)
;        txt.nl()
;        xx = &main.sub2.label_in_sub2
;        txt.print_uwhex(xx, true)
;        txt.nl()
;        xx = main.sub2.sub2var
;        txt.print_uwhex(xx, true)
;        txt.nl()
;        xx = &main.start.label_local
;        txt.print_uwhex(xx, true)
;        txt.nl()

label_local:
        return
    }

;    sub sub2(uword ad) {
;        uword sub2var = 42
;
;        txt.print_uwhex(ad,true)
;        txt.nl()
;label_in_sub2:
;        txt.nl()
;    }
}
