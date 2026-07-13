%import textio
%import exec
%import dos
%import graphics
%import intuition

main {
    struct Node {
        bool flag
        uword value
    }

    sub start() {
        ^^Node node = exec.AllocVec(sizeof(Node), 0)

        txt.print_ulhex(node, true)
        node.flag = true
        node.value=4242

        exec.FreeVec(node)
    }
}
