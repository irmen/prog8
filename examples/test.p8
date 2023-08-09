%zeropage dontuse

main {
    sub start () { foo() derp() other() }
    sub foo() { cx16.r0++ }
    asmsub derp() { %asm {{ nop }} %ir {{ loadr.b r0,1 }} }

    sub other()
    {
        cx16.r0++
        asmother()
        asmir()
    }

    asmsub asmother()
    {
        %asm
        {{
            txa
            tay
        }}
    }

    asmsub asmir()
    {
        %ir
        {{
            loadr.b r0,1
        }}
    }
}
