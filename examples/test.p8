%import floats
%import textio
%zeropage basicsafe

main {
    sub start() {
        struct List {
            bool b
            word w
            float f
            ^^List next
        }

        struct Foo {
            byte bb
        }

        ^^List l1 = 2000
        ^^List l2 = 3000
        ^^Foo f1 = 4000

        l1.b = true
        l1.w = 1111
        l1.f = 11.234

        l2.b = false
        l2.w = 2222
        l2.f = 222.222

        txt.print_uwhex(l1, true)
        txt.spc()
        txt.print_bool(l1.b)
        txt.spc()
        txt.print_w(l1.w)
        txt.spc()
        txt.print_f(l1.f)
        txt.nl()

        l1^^ = l2^^
        txt.print_uwhex(l1, true)
        txt.spc()
        txt.print_bool(l1.b)
        txt.spc()
        txt.print_w(l1.w)
        txt.spc()
        txt.print_f(l1.f)
        txt.nl()
    }
}
