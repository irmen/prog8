package prog8.codegen.experimental6502

import prog8.ast.Program
import prog8.ast.base.Position
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.Block
import prog8.ast.statements.Label
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl
import prog8.ast.walk.IAstVisitor
import java.util.*

class SymbolTableMaker: IAstVisitor {

    private val st = SymbolTable()
    private val scopestack = Stack<StNode>()

    fun make(program: Program): SymbolTable {
        scopestack.clear()
        st.children.clear()
        this.visit(program)
        program.builtinFunctions.names.forEach {
            val node = StNode(it, StNodeType.BUILTINFUNC, Position.DUMMY)
            node.parent = st
            st.children[it] = node
        }
        return st
    }

    override fun visit(block: Block) {
        val node = StNode(block.name, StNodeType.BLOCK, block.position)
        node.parent = st
        scopestack.push(node)
        super.visit(block)
        scopestack.pop()
        st.children[node.name] = node
    }

    override fun visit(subroutine: Subroutine) {
        val node = StNode(subroutine.name, StNodeType.SUBROUTINE, subroutine.position)
        node.parent = scopestack.peek()
        scopestack.push(node)
        super.visit(subroutine)
        scopestack.pop()
        scopestack.peek().children[node.name] = node
    }

    override fun visit(decl: VarDecl) {
        val node =
            when(decl.type) {
                VarDeclType.VAR -> StVariable(decl.name, decl.datatype, decl.position)
                VarDeclType.CONST -> StConstant(decl.name, decl.datatype, (decl.value as NumericLiteral).number, decl.position)
                VarDeclType.MEMORY -> StMemVar(decl.name, decl.datatype, (decl.value as NumericLiteral).number.toUInt(), decl.position)
            }
        node.parent = scopestack.peek()
        node.parent.children[node.name] = node
    }

    override fun visit(label: Label) {
        val node = StNode(label.name, StNodeType.LABEL, label.position)
        node.parent = scopestack.peek()
        node.parent.children[node.name] = node
    }
}
