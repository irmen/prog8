;; Doubly linked lists - portable implementation
;;
;; Purpose: this module implements the classic Amiga Exec doubly
;; linked list scheme natively for all non-amiga targets. It is
;; source code compatible with the amiga500 lists.p8 module, which
;; simply forwards to the exec.library routines; everywhere else the
;; Prog8 code below does the work directly.
;;
;; Data structures:
;;
;; Node holds just the two link pointers Succ and Pred. List consists
;; of Head, Tail and TailPred pointers. FullNode adds an ubyte Type,
;; a signed priority byte Pri and a Name string pointer to the two
;; links; only the priority and name aware routines (enqueue,
;; find_name) require nodes to follow that extended layout.
;;
;; Layout: a list is a circular doubly linked list in which the
;; header itself acts as the sentinel node - there are no separate
;; head/tail marker pointers. The header's Tail field stays zero
;; forever and serves as terminator: forward traversal via Succ ends
;; when it reaches &List.Tail, backward traversal via Pred ends at
;; the header itself. The first real node's Pred points at the
;; header, the last real node's Succ points at &List.Tail.
;;
;; Creation and initialization:
;; Define the list header as a variable (or inside a memory() area),
;; then call init(listptr) once before any other use. It sets
;; Head = &Tail, Tail = 0 and TailPred = &Head. Nodes need no
;; explicit setup beyond being cleared; fill in Pri/Name as needed
;; before using enqueue() or find_name().
;;
;; Empty test: the list is empty iff TailPred points back at the
;; header itself (equivalently Head == &Tail); see is_empty().
;;
;; Operations (plain Prog8 subroutines in this module):
;; - add_head / add_tail: link node at front / back of the list.
;; - insert(list, node, pred): links node after the given predecessor
;;   node anywhere in the list; pred = 0 means insert at head.
;; - enqueue(list, node): priority-sorted insertion using Pri,
;;   placed before the first lower-priority node so equal priorities
;;   come out FIFO. Requires the FullNode layout.
;; - remove(node): unlinks a node from whichever list it currently
;;   sits in; it must actually be linked into one.
;; - remove_head / remove_tail: unlink and return the first / last
;;   node, or null if empty; these give you queue and stack behaviour.
;; - find_name(list, name): linear search over Name strings, returns
;;   matching node or null. Requires the FullNode layout.
;;
;; Notes: none of these routines provide any form of locking; keep
;; all manipulation of a shared list within one execution context or
;; add your own protection around it. Never call remove() on a node
;; that is not currently linked into a list.
;;
;; Allocator agnostic: the routines never allocate or free memory
;; themselves, they only link the `pointer` values you pass in. Nodes
;; and headers can come from static `[]` variables, `memory()` slabs,
;; or any arena/bump allocator such as `arena_alloc(sizeof(MyNode))`
;; as `^^MyNode`. Just ensure the allocation is suitably aligned.
;;
;; Custom nodes: you can define your own node struct for use with these
;; routines and with `for node in mylist` loops. The only requirement
;; is that the link pointers are the first fields at offset 0. The
;; minimal operations (add_head/add_tail/insert/remove) only need
;; Succ/Pred; enqueue() and find_name() require the full Type/Pri/Name
;; layout of FullNode. Example:
;;
;;   main {
;;       struct MyNode {
;;           ^^MyNode Succ    ; must be first (offset 0)
;;           ^^MyNode Pred    ; must be second
;;           ubyte value      ; payload follows
;;       }
;;       struct MyList {
;;           ^^MyNode Head
;;           pointer Tail
;;           ^^MyNode TailPred
;;       }
;;       sub start() {
;;           ^^MyList mylist = []              ; zero-initialized header
;;           ^^MyNode n1 = [0, 0, 11]           ; Succ, Pred, value
;;           ^^MyNode n2 = [0, 0, 22]
;;           lists.init(mylist)
;;           lists.add_tail(mylist, n1)
;;           lists.add_tail(mylist, n2)
;;       }
;;   }

lists {
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
