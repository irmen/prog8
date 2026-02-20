; Binary Search Tree.
; It's a simple implementation for test/demonstration purposes of the pointer support;
; no balancing is done and memory is not freed when elements are removed.

%import textio

btree {

    sub benchmark(uword max_time) -> uword {
        txt.nl()
        cbm.SETTIM(0,0,0)
        uword score
        while cbm.RDTIM16() < max_time {
            bench_operations()
            txt.chrout('.')
            score++
        }
        txt.nl()
        return score
    }

    sub bench_operations() {
        arena.freeall()
        btree.root = 0

        for cx16.r0 in [321, 719, 194, 550, 187, 203, 520, 562, 221, 676, 97, 852, 273, 326, 589, 606, 275, 794, 63, 716]
            btree.add(cx16.r0)

        cx16.r0L = btree.size()
        btree.process_tree_inorder()
        btree.process_tree_preorder()

        void btree.contains(203)
        void btree.contains(204)
        void btree.contains(605)
        void btree.contains(606)

        btree.remove(9999)
        btree.remove(97)
        btree.remove(187)
        btree.remove(203)
        btree.remove(275)
        btree.remove(321)
        btree.remove(520)
        btree.remove(562)
        btree.remove(606)
        btree.remove(719)
        btree.remove(794)

        cx16.r0L = btree.size()
        btree.process_tree_inorder()
        btree.process_tree_preorder()
    }

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
                    if parent.left!=0
                        parent = parent.left
                    else {
                        parent.left = node
                        return
                    }
                } else {
                    if parent.right!=0
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
        while r!=0 {
            if r.value==value
                return true
            if r.value>value
                r = r.left
            else
                r = r.right
        }
        return false
    }

    sub size() -> ubyte {
        ubyte count

        if root!=0
            count_node(root)

        return count

        sub count_node(^^Node r) {
            count++
            if r.left!=0 {
                pushw(r)
                count_node(r.left)
                r = popw()
            }
            if r.right!=0 {
                pushw(r)
                count_node(r.right)
                r = popw()
            }
        }
    }

    sub remove(uword value) {
        ; note: we don't deallocate the memory from the node, for simplicity sake
        ^^Node n = root
        ^^Node parent = 0
        while n!=0 {
            if n.value==value {
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
            ^^Node succ = p
            p = p.right
            while p!=0 {
                succ = p
                p = p.left
            }
            return succ
        }

        sub replacechild(^^Node p, ^^Node child, ^^Node newchild) {
            if p.left==child
                p.left = newchild
            else
                p.right = newchild
        }
    }


    sub process_tree_inorder() {
        if root!=0
            process_tree(root)

        sub process_tree(^^Node r) {
            if r.left!=0 {
                pushw(r)
                process_tree(r.left)
                r = popw()
            }
            cx16.r0 = r.value
            if r.right!=0 {
                pushw(r)
                process_tree(r.right)
                r = popw()
            }
        }
    }


    sub process_tree_preorder() {
        if root!=0
            process_tree(root,0)

        sub process_tree(^^Node r, ubyte depth) {
            cx16.r0 = r.value
            if r.left!=0 {
                pushw(r)
                push(depth)
                process_tree(r.left, depth+1)
                depth = pop()
                r = popw()
            }
            if r.right!=0 {
                pushw(r)
                push(depth)
                process_tree(r.right, depth+1)
                depth = pop()
                r = popw()
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

    sub freeall() {
        next = buffer
    }
}
