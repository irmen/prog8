package prog8.compiler.astprocessing


/*
This is here for reference only, reflection based ast walking is very slow
when compared to the more verbose visitor pattern interfaces.
Too bad, because the code is very small
*/


//import prog8.ast.NoAstWalk
//import prog8.ast.Node
//import prog8.ast.Program
//import prog8.ast.base.Position
//import prog8.ast.expressions.BinaryExpression
//import prog8.ast.expressions.NumericLiteral
//import kotlin.reflect.KClass
//import kotlin.reflect.KVisibility
//import kotlin.reflect.full.declaredMemberProperties
//import kotlin.reflect.full.isSubtypeOf
//import kotlin.reflect.full.starProjectedType
//
//
//class ReflectionAstWalker {
//    private val nodeType = Node::class.starProjectedType
//    private val collectionType = Collection::class.starProjectedType
//
//
//    fun walk(node: Node, nesting: Int) {
//        val nodetype: KClass<out Node> = node::class
//        val indent = "  ".repeat(nesting)
//        //println("$indent VISITING ${nodetype.simpleName}")
//        val visibleAstMembers = nodetype.declaredMemberProperties.filter {
//            it.visibility!=KVisibility.PRIVATE && !it.isLateinit &&
//                    !(it.annotations.any{a->a is NoAstWalk})
//        }
//        for(prop in visibleAstMembers) {
//            if(prop.returnType.isSubtypeOf(nodeType)) {
//                // println("$indent +PROP: ${prop.name}")
//                walk(prop.call(node) as Node, nesting + 1)
//            }
//            else if(prop.returnType.isSubtypeOf(collectionType)) {
//                val elementType = prop.returnType.arguments.single().type
//                if(elementType!=null && elementType.isSubtypeOf(nodeType)) {
//                    val nodes = prop.call(node) as Collection<Node>
//                    nodes.forEach { walk(it, nesting+1) }
//                }
//            }
//        }
//    }
//    fun walk(program: Program) {
//        for(module in program.modules) {
//            println("---MODULE $module---")
//            walk(module, 0)
//        }
//    }
//}
//
//
//fun main() {
//    val ast = BinaryExpression(
//            NumericLiteral.optimalInteger(100, Position.DUMMY),
//            "+",
//            NumericLiteral.optimalInteger(200, Position.DUMMY),
//            Position.DUMMY
//    )
//
//    val walker = ReflectionAstWalker()
//    walker.walk(ast,0)
//
//}
