====
TODO
====


IF_XX::

    if[_XX] [<expression>] {
            ...
    }
    [ else {
            ...     ; evaluated when the condition is not met
    } ]


==> DESUGARING ==>

(no else:)::

                    if[_!XX] [<expression>] goto prog8_if_999_end          ; !XX being the conditional inverse of XX
                    .... (true part)
    prog8_if_999_end ; code continues after this


(with else)::

                    if[_XX] [<expression>] goto prog8_if_999
                    ... (else part)
                    goto prog8_if_999_end
    prog8_if_999     ... (true part)
    prog8_if_999_end ; code continues after this


IF  X  <COMPARISON>  Y

==> DESUGARING ==>::

        compare X, Y
        if_XX goto ....
        XX based on <COMPARISON>.


While::

    while[_XX] <expression> {
        ...
        continue
        break
    }

==> DESUGARING ==>::

        goto prog8_while_999_check    ; jump to the check
    prog8_while_999
        ... (code)
        goto  prog8_while_999          ;continue
        goto  prog8_while_999_end      ;break
    prog8_while_999_check
            if[_XX] <expression> goto prog8_while_999  ; loop condition
    prog8_while_999_end      ; code continues after this


Repeat::

    repeat {
        ...
        continue
        break
    } until[_XX] <expressoin>

==> DESUGARING ==>::

    prog8_repeat_999
            ... (code)
            goto prog8_repeat_999          ;continue
            goto prog8_repeat_999_end      ;break
            if[_!XX] <expression> goto prog8_repeat_999        ; loop condition via conditional inverse of XX
    prog8_repeat_999_end         ; code continues after this


For::

    for <loopvar> = <from_expression> to <to_expression> [step <step_expression>] {
        ...
        break
        continue
    }


@todo how to do signed integer loopvars?


==> DESUGARING ==>::

            loopvar = <from_expression>
            compare loopvar, <to_expression>
            if_ge goto prog8_for_999_end       ; loop condition
            step = <step_expression>        ; (store only if step < -1 or step > 1)
    prog8_for_999
            goto prog8_for_999_end        ;break
            goto prog8_for_999_loop       ;continue
            ....  (code)
    prog8_for_999_loop
            loopvar += step         ; (if step > 1 or step < -1)
            loopvar++               ; (if step == 1)
            loopvar--               ; (if step == -1)
            goto prog8_for_999         ; continue the loop
    prog8_for_999_end        ; code continues after this



### Macros

@todo macros are meta-code that is executed by the compiler, in a preprecessing step
during the compilation, and can produce output that is then replaced on that point in the input source.
Allows us to create pre calculated sine tables and such.



Memory Block Operations
^^^^^^^^^^^^^^^^^^^^^^^

@todo matrix,list,string memory block operations:

- matrix type operations (whole matrix, per row, per column, individual row/column)
  operations: set, get, copy (from another matrix with the same dimensions, or list with same length),
  shift-N (up, down, left, right, and diagonals, meant for scrolling)
  rotate-N (up, down, left, right, and diagonals, meant for scrolling)
  clear (set whole matrix to the given value, default 0)

- list operations (whole list, individual element)
  operations: set, get, copy (from another list with the same length), shift-N(left,right), rotate-N(left,right)
  clear (set whole list to the given value, default 0)

- list and matrix operations ofcourse work identical on vars and on memory mapped vars of these types.

- strings: identical operations as on lists.

- matrix with row-interleave can only be a memory mapped variable and can be used to directly
  access a rectangular area within another piece of memory - such as a rectangle on the (character) screen

these should call (or emit inline) optimized pieces of assembly code, so they run as fast as possible



Bitmap Definition (for Sprites and Characters)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

to define CHARACTERS (8x8 monochrome or 4x8 multicolor = 8 bytes)
--> PLACE in memory on correct address (???k aligned)

and SPRITES (24x21 monochrome or 12x21 multicolor = 63 bytes)
--> PLACE in memory on correct address (base+sprite pointer, 64-byte aligned)

