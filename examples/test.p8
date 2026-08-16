%import textio
%zeropage basicsafe

main {
    struct Foo {
        ubyte[4] name
        bool flag
    }

    sub start() {
        ;; ^^Foo f = ^^Foo : [ ['a', 'b', 'c', '\x00'], true ]
        ^^Foo f = ^^Foo : [ "abc", true ]

        txt.print(f.name)
        txt.nl()
    }
}
