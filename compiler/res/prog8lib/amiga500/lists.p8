;; Exec doubly linked lists - usage summary
;;
;; Purpose: this module is a super thin wrapper over the exec list
;; headers and library routines. It exists for source code
;; compatibility with other targets that supply their own lists.p8
;; implementation. On amiga500 everything simply forwards to exec.library.
;;
;; Data structures (from Include_H/exec/nodes.h and exec/lists.h):
;;
;; A full Node is 14 bytes: Succ and Pred pointers at offsets 0 and 4,
;; an ubyte Type at 8, a signed priority byte Pri at 9 and a Name
;; string pointer at 10. A MinNode holds just the two link pointers
;; (8 bytes). The list headers mirror this split: List consists of
;; Head, Tail and TailPred pointers plus Type and Pad bytes (14 bytes),
;; MinList has only the three pointers (12 bytes). This module aliases
;; List/Node to the minimal variants; use FullNode when you need the
;; Type/Pri/Name fields.
;;
;; Layout: an Exec list is a circular doubly linked list in which the
;; header itself acts as the sentinel node - there are no separate
;; head/tail marker pointers. The header's Tail field stays zero
;; forever and serves as terminator: forward traversal via Succ ends
;; when it reaches &Header.Tail, backward traversal via Pred ends when
;; it reaches the header itself. The first real node's Pred points at
;; the header, the last real node's Succ points at &Header.Tail.
;;
;; Creation and initialization:
;; Place the list header in static memory or allocate it, then call
;; init(listptr) once before any other use. init() is a thin wrapper
;; around exec.NewList(), which sets Head = &Tail, Tail = 0 and
;; TailPred = &Head. Nodes need no explicit setup beyond being
;; cleared; fill in Type/Pri/Name as needed before using the routines
;; below that depend on them. For MinList headers on very new
;; Kickstarts there is also NewMinList (LVO -828), but NewList covers
;; both variants because their layouts coincide.
;;
;; Empty test: the list is empty iff TailPred points back at the
;; header itself (equivalently Head == &Tail); see is_empty().
;;
;; Operations (all resolve to exec.library LVO calls):
;; - add_head / add_tail: link node at front / back of list
;;   (AddHead LVO -240, AddTail LVO -246).
;; - insert(list, node, pred): links node after the given predecessor
;;   node anywhere in the list; pred = 0 means insert at head
;;   (Insert LVO -234).
;; - enqueue(list, node): priority-sorted insertion using Pri, placed
;;   before the first lower-priority node so equal priorities come out
;;   FIFO. Requires full Nodes (Enqueue LVO -270).
;; - remove(node): unlinks a node from whichever list it currently
;;   sits in; it must actually be linked into one (Remove LVO -252).
;; - remove_head / remove_tail: unlink and return the first / last
;;   node, or null if empty; these give you queue and stack behaviour
;;   (RemHead LVO -258, RemTail LVO -264).
;; - find_name(list, name): linear search over Name strings, returns
;;   matching node or null; requires full Nodes (FindName LVO -276).
;;
;; Concurrency warning: none of these routines arbitrate access to
;; the list. If tasks or interrupts share a list you must serialize
;; access yourself (Forbid/Permit, Disable/Enable, or semaphores).
;;
;; Allocator agnostic: the routines never allocate or free memory
;; themselves, they only link the `pointer` values you pass in. Nodes
;; and headers can come from static `[]` variables, `memory()` slabs,
;; or any arena/bump allocator such as `arena_alloc(sizeof(MyNode))`
;; as `^^MyNode` or `exec.AllocMem` on amiga. Just ensure the
;; allocation is suitably aligned (long-aligned on m68k).
;;
;; Custom nodes: you can define your own node struct for use with the
;; exec/lists routines and with `for node in mylist` loops. The only
;; requirement is that the link pointers are the first fields at offset
;; 0 (Succ at 0, Pred at 4 on 32-bit amiga). Minimal operations only
;; need Succ/Pred; enqueue() and find_name() require the full
;; Type/Pri/Name layout of exec.Node / lists.FullNode. Define the list
;; header with your node type so `for`-iteration infers the correct
;; type. Example:
;;
;;   main {
;;       struct MyNode {
;;           ^^MyNode Succ    ; must be first (offset 0)
;;           ^^MyNode Pred    ; must be second (offset 4)
;;           ubyte Type       ; include for FullNode compatibility if needed
;;           byte Pri
;;           str Name
;;           ubyte value      ; your payload follows
;;       }
;;       struct MyList {
;;           ^^MyNode Head
;;           pointer Tail
;;           ^^MyNode TailPred
;;       }
;;       sub start() {
;;           ^^MyList mylist = []                 ; zero-initialized header
;;           ^^MyNode n1 = [0, 0, 0, 0, 0, 11]    ; Succ, Pred, Type, Pri, Name, value
;;           ^^MyNode n2 = [0, 0, 0, 0, 0, 22]
;;           exec.NewList(mylist as ^^exec.List)  ; or lists.init(mylist as pointer)
;;           lists.add_tail(mylist as pointer, n1 as pointer)
;;           lists.add_tail(mylist as pointer, n2 as pointer)
;;           ; or directly: exec.AddTail(mylist as pointer, n1 as pointer)
;;       }
;;   }

%import exec

lists {
    %option ignore_unused

    alias Node = exec.MinNode
    alias List = exec.MinList
    alias FullNode = exec.Node

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
