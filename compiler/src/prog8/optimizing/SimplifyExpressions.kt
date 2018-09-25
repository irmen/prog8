package prog8.optimizing

import prog8.ast.IAstProcessor
import prog8.ast.INameScope

/*
    todo simplify expression terms:
        X*0 -> 0
        X*1 -> X
        X*2 -> X << 1
        X*3 -> X + (X << 1)
        X*4 -> X << 2
        X*5 -> X + (X << 2)
        X*6 -> (X<<1) + (X << 2)
        X*7 -> X + (X<<1) + (X << 2)
        X*8 -> X << 3
        X*9 -> X + (X<<3)
        X*10 -> (X<<1) + (X<<3)

        X/1, X//1, X**1 -> just X
        X/2, X//2 -> X >> 1  (if X is byte or word)
        X/4, X//4 -> X >> 2  (if X is byte or word)
        X/8, X//8 -> X >> 3 (if X is byte or word)
        X/16, X//16 -> X >> 4 (if X is byte or word)
        X/32, X//32 -> X >> 5 (if X is byte or word)
        X/64, X//64 -> X >> 6 (if X is byte or word)
        X/128, X//128 -> X >> 7 (if X is byte or word)
        X / (n>=256) -> 0 (if x is byte)    X >> 8  (if X is word)
        X // (n>=256) -> 0 (if x is byte)    X >> 8  (if X is word)

        X+X -> X << 1
        X << n << m  ->  X << (n+m)

        1**X -> 1
        0**X -> 0
        X*-1 -> unary prefix -X
        X**0 -> 1
        X**1 -> X
        X**2 -> X*X
        X**3 -> X*X*X
        X**0.5 -> sqrt(X)
        X**-1 -> 1.0/X
        X**-2 -> 1.0/X/X
        X**-3 -> 1.0/X/X/X
        X << 0 -> X
        X | 0 -> X
        x & 0 -> 0
        X ^ 0 -> X
        X*Y - X  ->  X*(Y-1)
        -X + A ->  A - X
        -X - A -> -(X+A)
        X % 1 -> constant 0 (if X is byte/word)
        X % 2 -> X and 255 (byte)  X and 65535 (word)


    todo expression optimization: remove redundant builtin function calls
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