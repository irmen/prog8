list {
    %option ignore_unused

    struct Node {
        ^^Node Succ
        ^^Node Pred
    }

    struct List {
        ^^Node Head
        pointer Tail
        ^^Node TailPred
    }

    ; helper for priority/name operations (full node layout)
    struct FullNode {
        ^^FullNode Succ
        ^^FullNode Pred
        ubyte Type
        byte Pri
        str Name
    }

    sub init(pointer listptr) {
        ^^List lst = listptr as ^^List
        lst.Head = (&lst.Tail as ^^Node)
        lst.Tail = 0
        lst.TailPred = (&lst.Head as ^^Node)
    }

    sub is_empty(pointer listptr) -> bool {
        ^^List lst = listptr as ^^List
        return lst.TailPred == (&lst.Head as ^^Node)
    }

    sub add_head(pointer listptr, pointer node) {
        ^^List lst = listptr as ^^List
        ^^Node nd = node as ^^Node
        ^^Node first = lst.Head
        nd.Succ = first
        nd.Pred = (&lst.Head as ^^Node)
        first.Pred = nd
        lst.Head = nd
    }

    sub add_tail(pointer listptr, pointer node) {
        ^^List lst = listptr as ^^List
        ^^Node nd = node as ^^Node
        ^^Node last = lst.TailPred
        nd.Succ = (&lst.Tail as ^^Node)
        nd.Pred = last
        last.Succ = nd
        lst.TailPred = nd
    }

    sub insert(pointer listptr, pointer node, pointer pred) {
        if pred==0 {
            add_head(listptr, node)
            return
        }
        ^^Node nd = node as ^^Node
        ^^Node pr = pred as ^^Node
        ^^Node succ = pr.Succ
        nd.Succ = succ
        nd.Pred = pr
        succ.Pred = nd
        pr.Succ = nd
    }

    sub remove(pointer node) {
        ^^Node nd = node as ^^Node
        ^^Node pred = nd.Pred
        ^^Node succ = nd.Succ
        pred.Succ = succ
        succ.Pred = pred
    }

    sub remove_head(pointer listptr) -> pointer {
        if is_empty(listptr)
            return 0
        ^^List lst = listptr as ^^List
        ^^Node nd = lst.Head
        ^^Node succ = nd.Succ
        succ.Pred = (&lst.Head as ^^Node)
        lst.Head = succ
        return nd as pointer
    }

    sub remove_tail(pointer listptr) -> pointer {
        if is_empty(listptr)
            return 0
        ^^List lst = listptr as ^^List
        ^^Node nd = lst.TailPred
        ^^Node pred = nd.Pred
        pred.Succ = (&lst.Tail as ^^Node)
        lst.TailPred = pred
        return nd as pointer
    }

    sub enqueue(pointer listptr, pointer node) {
        if is_empty(listptr) {
            add_head(listptr, node)
            return
        }
        ^^List lst = listptr as ^^List
        ^^FullNode nd = node as ^^FullNode
        ^^FullNode cur = lst.Head as ^^FullNode
        while cur.Succ != (&lst.Tail as ^^FullNode) {
            if nd.Pri > cur.Pri {
                ^^FullNode pred = cur.Pred
                nd.Succ = cur as ^^FullNode
                nd.Pred = pred
                pred.Succ = nd as ^^FullNode
                cur.Pred = nd
                return
            }
            cur = cur.Succ as ^^FullNode
        }
        add_tail(listptr, node)
    }

    sub find_name(pointer listptr, str name) -> pointer {
        ^^List lst = listptr as ^^List
        ^^FullNode cur = lst.Head as ^^FullNode
        while cur.Succ != (&lst.Tail as ^^FullNode) {
            if cur.Name != 0 and cur.Name == name
                return cur as pointer
            cur = cur.Succ as ^^FullNode
        }
        return 0
    }
}
