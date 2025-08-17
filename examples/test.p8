main {
    struct Node {
        ubyte weight
    }

    sub start() {
        ^^Node nodes
        ^^Node[5] nodesarray

        cx16.r0L = nodesarray[2].weight
        cx16.r0L = nodes[2].weight
    }
}


;main {
;    sub start() {
;        struct List {
;            bool b
;            uword value
;        }
;
;        ^^List[10] listarray
;        cx16.r0 = listarray[2].value
;        cx16.r1 = listarray[3]^^.value
;    }
;}
