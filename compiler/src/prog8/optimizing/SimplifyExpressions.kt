package prog8.optimizing

import prog8.ast.*

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
        X*Y - X  ->  X*(Y-1)
        -X + A ->  A - X
        -X - A -> -(X+A)
        X % 1 -> constant 0 (if X is byte/word)
        X % 2 -> X and 1 (if X is byte/word)


    todo expression optimization: remove redundant builtin function calls
    todo expression optimization: optimize some simple multiplications into shifts  (A*8 -> A<<3,  A/4 -> A>>2)
    todo optimize addition with self into shift 1  (A+=A -> A<<=1)
    todo expression optimization: common (sub) expression elimination (turn common expressions into single subroutine call)

 */

class SimplifyExpressions(private val namespace: INameScope) : IAstProcessor {
    var optimizationsDone: Int = 0

    override fun process(assignment: Assignment): IStatement {
        if(assignment.aug_op!=null) {
            throw AstException("augmented assignments should have been converted to normal assignments before this optimizer")
        }
        return super.process(assignment)
    }

    override fun process(expr: BinaryExpression): IExpression {
        super.process(expr)
        val leftVal = expr.left.constValue(namespace)
        val rightVal = expr.right.constValue(namespace)
        val constTrue = LiteralValue.fromBoolean(true, expr.position)
        val constFalse = LiteralValue.fromBoolean(false, expr.position)

        // simplify logical expressions when a term is constant and determines the outcome
        when(expr.operator) {
            "or" -> {
                if((leftVal!=null && leftVal.asBooleanValue) || (rightVal!=null && rightVal.asBooleanValue)) {
                    optimizationsDone++
                    return constTrue
                }
                if(leftVal!=null && !leftVal.asBooleanValue) {
                    optimizationsDone++
                    return expr.right
                }
                if(rightVal!=null && !rightVal.asBooleanValue) {
                    optimizationsDone++
                    return expr.left
                }
            }
            "and" -> {
                if((leftVal!=null && !leftVal.asBooleanValue) || (rightVal!=null && !rightVal.asBooleanValue)) {
                    optimizationsDone++
                    return constFalse
                }
                if(leftVal!=null && leftVal.asBooleanValue) {
                    optimizationsDone++
                    return expr.right
                }
                if(rightVal!=null && rightVal.asBooleanValue) {
                    optimizationsDone++
                    return expr.left
                }
            }
            "xor" -> {
                if(leftVal!=null && !leftVal.asBooleanValue) {
                    optimizationsDone++
                    return expr.right
                }
                if(rightVal!=null && !rightVal.asBooleanValue) {
                    optimizationsDone++
                    return expr.left
                }
                if(leftVal!=null && leftVal.asBooleanValue) {
                    optimizationsDone++
                    return PrefixExpression("not", expr.right, expr.right.position)
                }
                if(rightVal!=null && rightVal.asBooleanValue) {
                    optimizationsDone++
                    return PrefixExpression("not", expr.left, expr.left.position)
                }
            }
            "|", "^" -> {
                if(leftVal!=null && !leftVal.asBooleanValue)
                    return expr.right
                if(rightVal!=null && !rightVal.asBooleanValue)
                    return expr.left
            }
            "&" -> {
                if(leftVal!=null && !leftVal.asBooleanValue)
                    return constFalse
                if(rightVal!=null && !rightVal.asBooleanValue)
                    return constFalse
            }
        }
        return expr
    }
}