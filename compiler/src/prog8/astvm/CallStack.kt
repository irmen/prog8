package prog8.astvm

import prog8.ast.INameScope
import java.util.*

class CallStack {

    private val stack = Stack<Pair<INameScope, Int>>()

    fun pop(): Pair<INameScope, Int> {
        return stack.pop()
    }

    fun push(scope: INameScope, index: Int) {
        stack.push(Pair(scope, index))
    }

}
