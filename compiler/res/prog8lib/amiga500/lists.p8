%import exec

list {
    %option ignore_unused

    ; = exec.MinNode
    struct Node {
        ^^Node Succ
        ^^Node Pred
    }

    ; = exec.MinList
    struct List {
        ^^Node Head
        pointer Tail
        ^^Node TailPred
    }

    ; helper for priority/name operations (full node layout)
    ; = exec.Node
    struct FullNode {
        ^^FullNode Succ
        ^^FullNode Pred
        ubyte Type
        byte Pri
        str Name
    }

    inline sub init(pointer listptr) {
        exec.NewList(listptr as ^^exec.List)
    }

    inline sub is_empty(pointer listptr) -> bool {
        ^^List lst = listptr as ^^List
        return lst.TailPred == (&lst.Head as ^^Node)
    }

    inline sub add_head(pointer listptr, pointer node) {
        exec.AddHead(listptr, node)
    }

    inline sub add_tail(pointer listptr, pointer node) {
        exec.AddTail(listptr, node)
    }

    inline sub insert(pointer listptr, pointer node, pointer pred) {
        exec.Insert(listptr, node, pred)
    }

    inline sub remove(pointer node) {
        exec.Remove(node)
    }

    inline sub remove_head(pointer listptr) -> pointer {
        return exec.RemHead(listptr)
    }

    inline sub remove_tail(pointer listptr) -> pointer {
        return exec.RemTail(listptr)
    }

    inline sub enqueue(pointer listptr, pointer node) {
        exec.Enqueue(listptr, node)
    }

    inline sub find_name(pointer listptr, str name) -> pointer {
        return exec.FindName(listptr, name)
    }
}
