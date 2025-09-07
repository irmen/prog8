main {
    sub start() {
        alias derp = structdefs.normal
        derp ++
        alias1()
        alias2()
        alias3()
    }

    sub alias1() {
        alias TheNode = structdefs.Node
        ^^TheNode node = 20000
        node.value = 100
    }

    sub alias2() {
        ^^structdefs.Node node = 20000
        alias thing = node
        thing.value=200
    }

    sub alias3() {
        alias TheNode = structdefs.Node
        ^^TheNode node = 20000
        node++
    }
}

structdefs {
    struct Node {
        ubyte value
    }

    ubyte @shared normal
}



;
;%import textio
;%zeropage kernalsafe
;
;
;main {
;    struct Node {
;        ubyte type, frame, framecounter
;    }
;
;
;    sub start() {
;        ^^Node storageElementBuffer = 20000
;        ^^Node t_element = 30000
;
;        alias node2 = t_element
;        alias StructAlias = Node
;        alias OtherNodeAlias = structdefs.OtherNode
;
;        ;node2.type = 10     ;; TODO: fix undefined symbol error
;        ;^^StructAlias node3 = 30000
;        ; node3.type = 10     ;; TODO: fix "unknown field 'type"  error
;
;        ^^OtherNodeAlias other = 20000
;        other++         ;; TODO fix compiler crash Key POINTER is missing in the map
;
;
;        ubyte @shared i = 0
;        storageElementBuffer[0] = t_element^^
;        storageElementBuffer[0] = t_element^^
;        storageElementBuffer[1] = t_element^^
;        storageElementBuffer[2] = t_element^^
;        storageElementBuffer[i] = t_element^^
;        ;storageElementBuffer[10]^^ = t_element^^        ; TODO support this with memcopy it's the same as the one above
;        ;storageElementBuffer[i]^^ = t_element^^
;    }
;}
;
;structdefs {
;    struct OtherNode {
;        ubyte type, frame, framecounter
;    }
;}
