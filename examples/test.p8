%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {

    struct Enemy {
        ubyte x
        ubyte y
        uword value
        float rotation
        bool alive
        ; no strings or arrays allowed in struct type declarations.
        ; typed pointers are allowed though because these are just a uword:
        ^^float floatptr
        ^^str stringpointer
    }

    sub start() {

        ; struct declarations also allowed inside subroutine scope
        struct Node {
            ubyte type
            uword value
            ^^Node nextnode  ; linked list?
        }

        ; declare pointer vars
        ^^bool @shared bool_ptr
        ^^ubyte @shared ubyte_ptr
        ^^word @shared word_ptr
        ^^Node @shared node_ptr
        ^^Enemy @shared enemy_ptr
        ^^bool[5] @shared boolptr_list   ; array of pointers to bools (bit silly, should we even support this)
        ^^Node[5] @shared node_list      ; array of pointers to nodes
        ^^Enemy[5] @shared enemy_list    ; array of pointers to enemies

        txt.print("sizeofs: ")
        txt.print_ub(sizeof(Enemy))
        txt.spc()
        txt.print_ub(sizeof(Node))
        txt.spc()
        txt.print_ub(sizeof(bool_ptr))
        txt.nl()

        ; point to a memory address.
        bool_ptr  = 2000
        bool_ptr  = 2002 as ^^bool
        ubyte_ptr  = 2000
        word_ptr  = 2000
        node_ptr  = 2000
        enemy_ptr  = 2000
        bool_ptr = enemy_ptr as ^^bool   ; cast makes no sense, but hey, programmer knows best right? (without cast would give error)

        ; dereference
        bool @shared bvar = bool_ptr^^
        bool_ptr^^ = false

        ; writing and reading fields using explicit deref
        enemy_ptr^^.y = 42
        node_ptr^^.nextnode^^.value = 888
        node_ptr^^.nextnode^^.nextnode^^.nextnode^^.nextnode^^.nextnode^^.value = 888
        cx16.r0=node_ptr^^.nextnode^^.nextnode^^.nextnode^^.nextnode^^.value
        node_ptr^^.nextnode = node_ptr

        ; writing and reading fields using implicit deref
        enemy_ptr.y = 42
        node_ptr.nextnode.value = 888
        node_ptr.nextnode.nextnode.nextnode.nextnode.nextnode.value = 888
        cx16.r0=node_ptr.nextnode.nextnode.nextnode.nextnode.value

        ; address of fields
        txt.print("address of field: ")
        txt.print_uw(&enemy_ptr.alive)
        txt.spc()
        enemy_ptr = 8000
        txt.print_uw(&enemy_ptr.alive)
        txt.nl()

        ; array elements, point to a memory address
        node_list[0] = 1000
        node_list[1] = 2000
        node_list[1] = 2002 as ^^Node
        node_list[2] = 3000


        ; BELOW DOESN'T WORK YET:
        ; pointer arithmetic
;        ubyte_ptr^^ ++
;        enemy_ptr ++        ; add 1*sizeof
;        enemy_ptr += 10     ; add 10*sizeof

        ; TODO how to statically allocate/initialize a struct? Difficult.. see TODO in docs
    }
}


