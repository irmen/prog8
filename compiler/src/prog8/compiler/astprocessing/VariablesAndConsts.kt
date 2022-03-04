package prog8.compiler.astprocessing

import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.Block
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl
import prog8.ast.walk.IAstVisitor
import prog8.compilerinterface.IVariablesAndConsts


internal class VariableExtractor: IAstVisitor {
    private val allBlockVars = mutableMapOf<Block, MutableSet<VarDecl>>()
    private val allBlockConsts = mutableMapOf<Block, MutableSet<VarDecl>>()
    private val allBlockMemoryvars = mutableMapOf<Block, MutableSet<VarDecl>>()
    private val allSubroutineVars = mutableMapOf<Subroutine, MutableSet<VarDecl>>()
    private val allSubroutineConsts = mutableMapOf<Subroutine, MutableSet<VarDecl>>()
    private val allSubroutineMemoryvars = mutableMapOf<Subroutine, MutableSet<VarDecl>>()

    fun extractVars(program: Program): IVariablesAndConsts {
        this.visit(program)
        return VariablesAndConsts(
            allBlockVars, allBlockConsts, allBlockMemoryvars,
            allSubroutineVars, allSubroutineConsts, allSubroutineMemoryvars)
    }

    override fun visit(decl: VarDecl) {
        val scope=decl.definingScope
        when (decl.type) {
            VarDeclType.VAR -> {
                when (scope) {
                    is Block -> {
                        val decls = allBlockVars[scope] ?: mutableSetOf()
                        decls.add(decl)
                        allBlockVars[scope] = decls
                    }
                    is Subroutine -> {
                        val decls = allSubroutineVars[scope] ?: mutableSetOf()
                        decls.add(decl)
                        allSubroutineVars[scope] = decls
                    }
                    else -> {
                        throw FatalAstException("var can only occur in subroutine or block scope")
                    }
                }
            }
            VarDeclType.CONST -> {
                when(scope) {
                    is Block -> {
                        val decls = allBlockConsts[scope] ?: mutableSetOf()
                        decls.add(decl)
                        allBlockConsts[scope] = decls
                    }
                    is Subroutine -> {
                        val decls = allSubroutineConsts[scope] ?: mutableSetOf()
                        decls.add(decl)
                        allSubroutineConsts[scope] = decls
                    }
                    else -> {
                        throw FatalAstException("var can only occur in subroutine or block scope")
                    }
                }
            }
            VarDeclType.MEMORY -> {
                when(scope) {
                    is Block -> {
                        val decls = allBlockMemoryvars[scope] ?: mutableSetOf()
                        decls.add(decl)
                        allBlockMemoryvars[scope] = decls
                    }
                    is Subroutine -> {
                        val decls = allSubroutineMemoryvars[scope] ?: mutableSetOf()
                        decls.add(decl)
                        allSubroutineMemoryvars[scope] = decls
                    }
                    else -> {
                        throw FatalAstException("var can only occur in subroutine or block scope")
                    }
                }
            }
            else -> {
                throw FatalAstException("invalid var type")
            }
        }
        super.visit(decl)
    }
}

internal class VariablesAndConsts (
    astBlockVars: Map<Block, Set<VarDecl>>,
    astBlockConsts: Map<Block, Set<VarDecl>>,
    astBlockMemvars: Map<Block, Set<VarDecl>>,
    astSubroutineVars: Map<Subroutine, Set<VarDecl>>,
    astSubroutineConsts: Map<Subroutine, Set<VarDecl>>,
    astSubroutineMemvars: Map<Subroutine, Set<VarDecl>>
) : IVariablesAndConsts
{
    override val blockVars: Map<Block, Set<IVariablesAndConsts.StaticVariable>>
    override val blockConsts: Map<Block, Set<IVariablesAndConsts.ConstantNumberSymbol>>
    override val blockMemvars: Map<Block, Set<IVariablesAndConsts.MemoryMappedVariable>>
    override val subroutineVars: Map<Subroutine, Set<IVariablesAndConsts.StaticVariable>>
    override val subroutineConsts: Map<Subroutine, Set<IVariablesAndConsts.ConstantNumberSymbol>>
    override val subroutineMemvars: Map<Subroutine, Set<IVariablesAndConsts.MemoryMappedVariable>>

    private val bv = astBlockVars.keys.associateWith { mutableSetOf<IVariablesAndConsts.StaticVariable>() }.toMutableMap()
    private val bc = astBlockConsts.keys.associateWith { mutableSetOf<IVariablesAndConsts.ConstantNumberSymbol>() }
    private val bmv = astBlockMemvars.keys.associateWith { mutableSetOf<IVariablesAndConsts.MemoryMappedVariable>() }
    private val sv = astSubroutineVars.keys.associateWith { mutableSetOf<IVariablesAndConsts.StaticVariable>() }
    private val sc = astSubroutineConsts.keys.associateWith { mutableSetOf<IVariablesAndConsts.ConstantNumberSymbol>() }
    private val smv = astSubroutineMemvars.keys.associateWith { mutableSetOf<IVariablesAndConsts.MemoryMappedVariable>() }

    init {
        astBlockVars.forEach { (block, decls) ->
            val vars = bv.getValue(block)
            vars.addAll(decls.map { toStatic(it) })
        }
        astBlockConsts.forEach { (block, decls) ->
            bc.getValue(block).addAll(
                decls.map {
                    IVariablesAndConsts.ConstantNumberSymbol(
                        it.datatype,
                        it.scopedName,
                        (it.value as NumericLiteral).number,
                        it.position
                    )
                })
        }
        astBlockMemvars.forEach { (block, decls) ->
            val vars = bmv.getValue(block)
            for(decl in decls) {
                // make sure the 'stubs' for the scratch variables in zeropage are not included as normal variables
                if(!decl.name.startsWith("P8ZP_SCRATCH_")) {
                    vars.add(
                        IVariablesAndConsts.MemoryMappedVariable(
                            decl.datatype,
                            decl.scopedName,
                            (decl.value as NumericLiteral).number.toUInt(),
                            decl.position
                        )
                    )
                }
            }
        }
        astSubroutineVars.forEach { (sub, decls) ->
            val vars = sv.getValue(sub)
            vars.addAll(decls.map { toStatic(it) })
        }
        astSubroutineConsts.forEach { (sub, decls) ->
            sc.getValue(sub).addAll(
                decls.map {
                    IVariablesAndConsts.ConstantNumberSymbol(
                        it.datatype,
                        it.scopedName,
                        (it.value as NumericLiteral).number,
                        it.position
                    )
                })
        }
        astSubroutineMemvars.forEach { (sub, decls) ->
            smv.getValue(sub).addAll(
                decls.map {
                    IVariablesAndConsts.MemoryMappedVariable(
                        it.datatype,
                        it.scopedName,
                        (it.value as NumericLiteral).number.toUInt(),
                        it.position
                    )
                })
        }
        blockVars = bv
        blockConsts = bc
        blockMemvars = bmv
        subroutineVars = sv
        subroutineConsts = sc
        subroutineMemvars = smv
    }

    private fun toStatic(decl: VarDecl) =
        IVariablesAndConsts.StaticVariable(decl.datatype, decl.scopedName, decl.value, decl.arraysize?.constIndex(), decl.zeropage, decl.position)

    override fun addIfUnknown(definingBlock: Block, variable: VarDecl) {
        var blockvars = bv[definingBlock]
        if(blockvars==null) {
            blockvars = mutableSetOf()
            bv[definingBlock] = blockvars
        }
        blockvars.add(toStatic(variable))
    }
}
