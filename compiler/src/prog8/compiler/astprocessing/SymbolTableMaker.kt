package prog8.compiler.astprocessing

import prog8.ast.Program
import prog8.ast.base.Position
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.Block
import prog8.ast.statements.Label
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl
import prog8.ast.walk.IAstVisitor
import prog8.compilerinterface.*
import java.util.*

internal class SymbolTableMaker: IAstVisitor {

    private val st = SymbolTable()
    private val scopestack = Stack<StNode>()

    fun makeFrom(program: Program): SymbolTable {
        scopestack.clear()
        st.children.clear()
        this.visit(program)
        program.builtinFunctions.names.forEach {
            val node = StNode(it, StNodeType.BUILTINFUNC, Position.DUMMY)
            st.add(node)
        }
        return st
    }

    override fun visit(block: Block) {
        val node = StNode(block.name, StNodeType.BLOCK, block.position)
        scopestack.push(node)
        super.visit(block)
        scopestack.pop()
        st.add(node)
        st.origAstLinks[block] = node
    }

    override fun visit(subroutine: Subroutine) {
        val node = StNode(subroutine.name, StNodeType.SUBROUTINE, subroutine.position)
        scopestack.push(node)
        super.visit(subroutine)
        scopestack.pop()
        scopestack.peek().add(node)
        st.origAstLinks[subroutine] = node
    }

    override fun visit(decl: VarDecl) {
        val node =
            when(decl.type) {
                VarDeclType.VAR -> StStaticVariable(decl.name, decl.datatype, decl.value, decl.arraysize?.constIndex(), decl.zeropage, decl.position)
                VarDeclType.CONST -> StConstant(decl.name, decl.datatype, (decl.value as NumericLiteral).number, decl.position)
                VarDeclType.MEMORY -> StMemVar(decl.name, decl.datatype, (decl.value as NumericLiteral).number.toUInt(), decl.position)
            }
        scopestack.peek().add(node)
        st.origAstLinks[decl] = node
    }

    override fun visit(label: Label) {
        val node = StNode(label.name, StNodeType.LABEL, label.position)
        scopestack.peek().add(node)
        st.origAstLinks[label] = node
    }
}