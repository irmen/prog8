%zeropage basicsafe

main {
    alias print = mytxt.print
    alias width = mytxt.DEFAULT_WIDTH
    alias textOverlay_top = mytxt.overlayTop
    alias textOverlay_bot = mytxt.overlayBot

    sub start() {
        address_of_alias()
        alias1()
        alias2()
        alias3()
        alias4()
        alias5()
        alias6()
        player.test()
        alias_scopes()
        alias_loop_error()
        alias_error()
        aliased_func_error()
    }

    sub address_of_alias() {
        ubyte @shared index = 3
        ubyte[10] array
        alias curframe = array

        cx16.r0 = &curframe
        cx16.r1 = &curframe[3]
        cx16.r2 = &curframe + 3
        cx16.r3 = &curframe[index]
        cx16.r4 = &curframe + index
    }

    sub alias1() {
        alias TheNode = structdefs.Node
        ^^TheNode @shared node = 20000
        node.value = 100
    }

    sub alias2() {
        ^^structdefs.Node node = 20000
        alias thing = node
        thing.value=200
    }

    sub alias3() {
        alias TheNode = structdefs.Node
        ^^TheNode @shared node = 20000
        node++
    }

    sub alias4() {
        alias currentElement = structdefs.element
        currentElement = 20000

        ; all 3 should be the same:
        structdefs.element.value = 42
        currentElement.value = 42
        currentElement^^.value = 42

        ; all 3 should be the same:
        structdefs.element.value2 = 4242
        currentElement.value2 = 4242
        currentElement^^.value2 = 4242

        cx16.r0 = currentElement^^.value2
        cx16.r1 = currentElement.value2
    }

    sub alias5() {
        alias nid = structdefs.element.value
        nid++
    }

    sub alias6() {
        alias print2 = mytxt.print
        alias width2 = mytxt.DEFAULT_WIDTH
        print("one")
        print2("two")
        mytxt.print_ub(width)
        mytxt.print_ub(width2)

        ; chained aliases
        alias chained = print2
        chained("chained")

        ; multi vardecls
        textOverlay_bot++
        textOverlay_top++
    }

    sub alias_scopes() {
        alias mything = other.thing
        alias myvariable = other.variable

        mything()
        myvariable ++

        other.thing2()
        other.variable2 ++

        alias nid = structdefs.element.value
        nid++
    }

    sub alias_loop_error() {
        alias vv = vv
        alias xx = xx.yy
        alias zz = mm
        alias mm = zz
    }

    alias print = mytxt.print2222
    alias width = mytxt.DEFAULT_WIDTH

    sub alias_error() {
        alias print2 = mytxt.print
        alias width2 = mytxt.DEFAULT_WIDTH_XXX
        print("one")
        print2("two")
        mytxt.print_ub(width)
        mytxt.print_ub(width2)
    }

    sub aliased_func_error() {
        alias func1 = actualfunc
        alias func2 = mkword
        alias func3 = func1
        alias func4 = func2

        ; all wrong:
        func1(1,2)
        func1()
        func2(1,2,3,4)
        func2()
        func3()
        func4()

        ; all ok:
        func1(1)
        cx16.r0 = func2(1,2)
        func3(1)
        cx16.r0 = func4(1,2)

        sub actualfunc(ubyte a) {
            a++
        }
    }
}



cx16 {
      %option merge
      &^^word pword4 = &cx16.r4
}

player {
      alias sxPtr = cx16.pword4
      &^^word zxPtr      = &cx16.r6

  sub test() {
    sxPtr^^ = -99           ; aliased assignment
    if (zxPtr^^ - sxPtr^^) in -13 to 13 {       ; aliased expression
        cx16.r0++
    }
  }
}


mytxt {
    uword overlayTop, overlayBot

    const ubyte DEFAULT_WIDTH = 80
    sub print_ub(ubyte value) {
        ; nothing
    }
    sub print(str msg) {
        ; nothing
    }
}

structdefs {
    struct Node {
        ubyte value
        uword value2
    }

    ^^Node @shared element
}

other {
    sub thing() {
        cx16.r0++
    }

    ubyte @shared variable

    alias thing2 = thing
    alias variable2 = variable
}
