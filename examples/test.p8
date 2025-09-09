%zeropage basicsafe

main {
    struct Node {
        ubyte id
    }

    ^^byte @shared bptr = 2000
    ^^word @shared swptr = 2000
    ^^uword @shared uwptr = 2000
    ^^Node @shared node = 2000

    sub start() {
        alias nid = node.id
        cx16.r0 = mkword(0, cx16.r9L)
        swptr^^ = mkword(0, cx16.r9L) as word
        uwptr^^ = mkword(0, cx16.r9L)
    }
}
