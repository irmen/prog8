; Binary Tree.

%import math
%import textio

main {

    sub start() {
        repeat 20 {
            btree.add(math.rndw() % 1000)
        }

        txt.print("sorted values: ")
        btree.print_tree_inorder()
        txt.print("'tree' form:\n")
        btree.print_tree_preorder()

        txt.print("203 in tree? ")
        txt.print_bool(btree.contains(203))
        txt.print("\n204 in tree? ")
        txt.print_bool(btree.contains(204))
        txt.print("\n605 in tree? ")
        txt.print_bool(btree.contains(605))
        txt.print("\n606 in tree? ")
        txt.print_bool(btree.contains(606))
        txt.nl()

        txt.print("REMOVING 9999:\n")
        btree.remove(9999)
;        btree.remove(97)
;        btree.remove(187)
;        btree.remove(203)
;        btree.remove(275)
;        btree.remove(321)
;        btree.remove(520)
;        btree.remove(562)
;        btree.remove(606)
;        btree.remove(716)
;        btree.remove(794)

;        txt.print("sorted values: ")
;        btree.print_tree_inorder()
;        txt.print("'tree' form:\n")
;        btree.print_tree_preorder()
    }
}

btree {

    struct Node {
        ^^Node left
        ^^Node right
        uword value
    }

    ^^Node root = 0

    sub add(uword value) {
        ^^Node node = arena.alloc(sizeof(Node))
        node.value = value
        node.left = node.right = 0

        if root==0
            root=node
        else {
            ^^Node parent = root
            repeat {
                if parent.value >= value {
                    if parent.left
                        parent = parent.left
                    else {
                        parent.left = node
                        return
                    }
                } else {
                    if parent.right
                        parent = parent.right
                    else {
                        parent.right = node
                        return
                    }
                }
            }
        }
    }

    sub contains(uword value) -> bool {
        ^^Node r = root
        while r {
            if r.value==value
                return true
            if r.value>value
                r = r.left
            else
                r = r.right
        }
        return false
    }

    sub remove(uword value) {
        ; note: we don't deallocate the memory from the node, for simplicity sake
        txt.print("REMOVE ")
        txt.print_uw(value)
        txt.nl()

        ^^Node n = root
        ^^Node parent = 0
        while n {
            if n.value==value {
                txt.print("FOUND!\n")
                if n.left==0
                    replacechild(parent, n, n.right)
                else if n.right==0
                    replacechild(parent, n, n.left)
                else {
                    ; Both left & right subtrees are present.
                    ; N = node to delete.
                    ;    Find N's successor S. (N's right subtree's minimum element)
                    ;    Attach N's left subtree to S.left (S doesn't have a left child)
                    ;    Attach N's right subtree to Parent in place of N.
                    txt.print("NEED SUCCESSOR OF ")
                    txt.print_uw(n)
                    txt.print(": ")
                    txt.print_uw(n.value)
                    txt.nl()
                    ^^Node successor = find_successor(n)
                    successor.left = n.left
                    replacechild(parent, n, n.right)
                }
                return
            }
            parent = n
            if n.value>value
                n = n.left
            else
                n = n.right
        }

        sub find_successor(^^Node p) -> ^^Node {
            txt.print("SUCCESSOR OF ")
            txt.print_uw(p)
            txt.print(": ")
            txt.print_uw(p.value)
            txt.nl()
;            ^^Node succ = p
;            p = p.right
;            while p!=0 {
;                succ = p
;                p = p.left
;                txt.print("SUCC ")
;                txt.print_uw(p)
;                txt.nl()
;            }
            return 0
        }

        sub replacechild(^^Node p, ^^Node child, ^^Node newchild) {
            if p.left==child
                p.left = newchild
            else
                p.right = newchild
        }
    }


    sub print_tree_inorder() {
        if root
            print_tree(root)
        txt.nl()

        sub print_tree(^^Node r) {
            if r.left {
                sys.pushw(r)
                print_tree(r.left)
                r = sys.popw()
            }
            txt.print_uw(r.value)
            txt.print(", ")
            if r.right {
                sys.pushw(r)
                print_tree(r.right)
                r = sys.popw()
            }
        }
    }


    sub print_tree_preorder() {
        if root
            print_tree(root,0)
        txt.nl()

        sub print_tree(^^Node r, ubyte depth) {
            repeat depth txt.print("  ")
            txt.print_uw(r.value)
            txt.nl()
            if r.left {
                sys.pushw(r)
                sys.push(depth)
                print_tree(r.left, depth+1)
                depth = sys.pop()
                r = sys.popw()
            }
            if r.right {
                sys.pushw(r)
                sys.push(depth)
                print_tree(r.right, depth+1)
                depth = sys.pop()
                r = sys.popw()
            }
        }
    }
}


arena {
    ; extremely trivial arena allocator (that never frees)
    uword buffer = memory("arena", 2000, 0)
    uword next = buffer

    sub alloc(ubyte size) -> uword {
        defer next += size
        return next
    }
}
