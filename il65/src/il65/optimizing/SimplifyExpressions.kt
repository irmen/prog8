package il65.optimizing

import il65.ast.IAstProcessor
import il65.ast.INameScope

/*
    todo eliminate useless terms:
        *0 -> constant 0
        X*1, X/1, X//1 -> just X
        X*-1 -> unary prefix -X
        X**0 -> 1
        X**1 -> X
        X**-1 -> 1.0/X
        X << 0 -> X
        X | 0 -> X
        x & 0 -> 0
        X ^ 0 -> X

    todo expression optimization: remove redundant builtin function calls
    todo expression optimization: reduce expression nesting / flattening of parenthesis
    todo expression optimization: simplify logical expression when a term makes it always true or false (1 or 0)
    todo expression optimization: optimize some simple multiplications into shifts  (A*8 -> A<<3,  A/4 -> A>>2)
    todo optimize addition with self into shift 1  (A+=A -> A<<=1)
    todo expression optimization: common (sub) expression elimination (turn common expressions into single subroutine call)
    todo remove or simplify logical aug assigns like A |= 0, A |= true, A |= false  (or perhaps turn them into byte values first?)

 */

class SimplifyExpressions(namespace: INameScope) : IAstProcessor {
    var optimizationsDone: Int = 0

    // @todo build this optimizer
}