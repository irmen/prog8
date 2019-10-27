package prog8.compiler.target.clang

import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.AssemblyError
import prog8.compiler.CompilationOptions
import prog8.compiler.Zeropage
import prog8.compiler.target.IAssemblyGenerator
import prog8.compiler.target.IAssemblyProgram
import java.io.PrintWriter
import java.nio.file.Path


internal class ClangGen(private val program: Program,
                        private val zeropage: Zeropage,
                        private val options: CompilationOptions,
                        private val outputDir: Path) : IAssemblyGenerator {
    override fun compileToAssembly(optimize: Boolean): IAssemblyProgram {

        println("Generating C++ language code... [UNFINISHED, EXPERIMENTAL]")

        // TODO manually flatten all scopes. Needed because in C++ even with forward declarations it's not possible to freely access members across all classes

        val outputFile = outputDir.resolve("${program.name}.cpp").toFile()
        outputFile.printWriter().use { out ->
            header(out)
            program.allBlocks().forEach { block(out, it) }
            footer(out)
        }

        return ClangAssemblyProgram(program.name, outputDir)
    }

    private fun header(out: PrintWriter) {
        out.println("// c++ transpiled version of Prog8 program ${program.name}\n")
        out.println("#include <cstdint>")
        out.println("""
template <typename T,T iBegin,T iEnd,T iStep=1>
class _prog8_range {
    public:
        struct iterator {
            T value;
            iterator    (T v) : value(v) {}
            operator T  () const         { return value; }
            operator T& ()               { return value; }
            T operator* () const         { return value; }
            iterator& operator++ ()      { value += iStep; return *this; }
        };
        iterator begin() { return iBegin; }
        iterator end() { return iEnd; }
};            

void _prog8_inlineasm(const char* assembly) { /* nothing */ }
void _prog8_goto_addr(uint16_t addr) { /* nothing */ }
void _prog8_directive_asminclude(const char* module, const char* scopename) { /* nothing */ }

uint8_t _prog8_register_A = 0;
uint8_t _prog8_register_X = 0;
uint8_t _prog8_register_Y = 0;

uint8_t _prog8_memory[65536];


""")
    }

    private fun footer(out: PrintWriter) {
    }

    private fun block(out: PrintWriter, block: Block) {
        out.println("// block: $block")
        out.println("class ${cname(block.name)} {")
        out.println("\tconst char* _prog8_linepos = \"${block.position}\";")
        out.print("\tvoid* _prog8_block_address = ")
        if (block.address == null)
            out.println("nullptr;")
        else {
            out.println("0x${block.address.toString(16)};")
        }
        out.println("public:")
        block.statements.forEach { statement(out, it) }
        out.println("};\n")
    }

    private fun statement(out: PrintWriter, stmt: Statement) {
        when (stmt) {
            is BuiltinFunctionStatementPlaceholder -> TODO("$stmt")
            is Directive -> directive(out, stmt)
            is Label -> out.println("${cname(stmt.name)}:")
            is Return -> {
                if (stmt.parent is Subroutine) {
                    if (stmt.value == null)
                        out.println("return;")
                    else {
                        out.println("return ${expression(stmt.value!!)};")
                    }
                }
            }
            is Continue -> out.println("continue;")
            is Break -> out.println("break;")
            is VarDecl -> {
                val sub = stmt.definingSubroutine()
                if (sub == null || sub.parameters.all { it.name != stmt.name })
                    vardecl(out, stmt)
            }
            is Assignment -> assignment(out, stmt)
            is PostIncrDecr -> out.println("${assigntarget(stmt.target)}${stmt.operator};")
            is Jump -> jump(out, stmt)
            is FunctionCallStatement -> out.println("${stmt.target.nameInSource.joinToString("::")}(${arguments(stmt.arglist)});")
            is InlineAssembly -> {
                val asmlines = stmt.assembly.lines().map { "\"$it\"" }.joinToString("\n")
                out.println("_prog8_inlineasm($asmlines);")
            }
            is AnonymousScope -> {
                out.println("{")
                stmt.statements.forEach { statement(out, it) }
                out.println("}")
            }
            is NopStatement -> out.println("// NOP")
            is Subroutine -> {
                if (stmt.isAsmSubroutine) {
                    out.println("// TODO ASM-SUBROUTINE: ${stmt.name}  @ ${stmt.position}") // TODO
                } else {
                    val returnType = datatype(stmt.returntypes.singleOrNull(), null)
                    val params = stmt.parameters.map {
                        val dt = datatype(it.type, null)
                        "${dt.first} ${cname(it.name)}${dt.second}"
                    }
                    val paramstr = params.joinToString(", ")
                    out.println("\nstatic ${returnType.first} ${cname(stmt.name)}${returnType.second}($paramstr) {")
                    out.println("\tconst char* _prog8_linepos = \"${stmt.position}\";")
                    stmt.statements.forEach { statement(out, it) }
                    out.println("}\n")
                }
            }
            is IfStatement -> ifstatement(out, stmt)
            is BranchStatement -> branchstatement(out, stmt)
            is ForLoop -> forloop(out, stmt)
            is WhileLoop -> whileloop(out, stmt)
            is RepeatLoop -> repeatloop(out, stmt)
            is WhenStatement -> out.println("// stmt: $stmt")   // TODO
            is StructDecl -> out.println("// stmt: $stmt")   // TODO
            else -> throw AssemblyError("c++ translation error: unexpected statement: $stmt")
        }
    }

    private fun assignment(out: PrintWriter, assign: Assignment) {
        val target = assigntarget(assign.target)
        val operator = assign.aug_op ?: "="
        out.println("$target $operator ${expression(assign.value)};")
    }

    private fun assigntarget(target: AssignTarget): String {
        return when {
            target.register!=null -> "_prog8_register_${target.register}"
            target.identifier!=null -> expression(target.identifier!!)
            target.memoryAddress!=null -> "(*(uint8_t*)${expression(target.memoryAddress.addressExpression)}) "
            target.arrayindexed!=null -> expression(target.arrayindexed!!)
            else -> "?????"
        }
    }

    private fun jump(out: PrintWriter, jmp: Jump) {
        when {
            jmp.generatedLabel!=null -> out.println("goto ${jmp.generatedLabel};")
            jmp.address!=null -> out.println("_prog8_goto_addr(${jmp.address.toString(16)});")
            jmp.identifier!=null -> out.println("goto ${expression(jmp.identifier)};")
        }
    }

    private fun forloop(out: PrintWriter, forlp: ForLoop) {
        out.println("// forloop $forlp")  // TODO
    }

    private fun repeatloop(out: PrintWriter, repeatLoop: RepeatLoop) {
        out.println("do {")
        statement(out, repeatLoop.body)
        out.println("} while(!(${expression(repeatLoop.untilCondition)}));")
    }

    private fun whileloop(out: PrintWriter, whileLoop: WhileLoop) {
        out.println("while(${expression(whileLoop.condition)}) {")
        statement(out, whileLoop.body)
        out.println("}")
    }

    private fun ifstatement(out: PrintWriter, ifs: IfStatement) {
        out.println("if(${expression(ifs.condition)}) {")
        statement(out, ifs.truepart)
        if(ifs.elsepart.containsCodeOrVars()) {
            out.println("} else {")
            statement(out, ifs.elsepart)
        }
        out.println("}")
    }

    private fun branchstatement(out: PrintWriter, branch: BranchStatement) {
        out.println("if(BranchFlag.${branch.condition}) {")
        statement(out, branch.truepart)
        if(branch.elsepart.containsCodeOrVars()) {
            out.println("} else {")
            statement(out, branch.elsepart)
        }
        out.println("}")
    }

    private fun directive(out: PrintWriter, dr: Directive) {
        val args = dr.args.map {
            when {
                it.int!=null -> it.int.toString()
                it.name!=null -> "\"${it.name}\""
                it.str!=null -> "\"${it.str}\""
                else -> ""
            }
        }
        out.println("_prog8_directive_${dr.directive.substring(1)}(${args.joinToString(", ")});")
    }

    private fun arguments(args: List<Expression>): String  = args.map{expression(it)}.joinToString(", ")

    private fun vardecl(out: PrintWriter, decl: VarDecl) {
        fun cdecl(memory: Boolean) {
            val dtype = datatype(decl.datatype, decl.arraysize)
            out.print(dtype.first)
            if (memory) {
                out.print("* ${cname(decl.name)}")
            } else {
                out.print(" ${cname(decl.name)}${dtype.second}")
            }
            if (decl.value != null) {
                // TODO c++ requires the value initialization to be done outside the class in a static assignment...
                if (memory)
                    out.print(" = static_cast<${dtype.first}*>(${expression(decl.value!!)})")
                else
                    out.print(" = ${expression(decl.value!!)}")
            }
            // TODO zeropage flag
            out.println(";")
        }

        out.print("static ")
        when (decl.type) {
            VarDeclType.VAR -> {
                cdecl(false)
            }
            VarDeclType.CONST -> {
                out.print("constexpr const ")
                cdecl(false)
            }
            VarDeclType.MEMORY -> {
                out.print("constexpr const ")
                cdecl(true)
            }
        }
    }


    private fun expression(expr: Expression): String {
        return when (expr) {
            is PrefixExpression -> "${expr.operator}${expression(expr.expression)}"
            is BinaryExpression -> "${expression(expr.left)} ${expr.operator} ${expression(expr.right)}"
            is ArrayIndexedExpression -> "${expression(expr.identifier)}[${expression(expr.arrayspec.index)}]"
            is TypecastExpression -> "static_cast<${datatype(expr.type, null)}>(${expression(expr.expression)}) "
            is AddressOf -> "&${expr.identifier.nameInSource.joinToString("::")}"
            is DirectMemoryRead -> "_prog8_memory[${expression(expr.addressExpression)}]"
            is NumericLiteralValue -> "${expr.number}"
            is StructLiteralValue -> TODO("struct literal")
            is StringLiteralValue -> "\"${expr.value}\""
            is ArrayLiteralValue -> arrayliteral(expr)
            is RangeExpr -> rangeexpr(expr)
            is RegisterExpr -> "_prog8_register_${expr.register}"
            is IdentifierReference -> expr.nameInSource.joinToString("::")
            is FunctionCall -> functioncall(expr)
        }
    }

    private fun rangeexpr(range: RangeExpr): String {
        val constrange = range.toConstantIntegerRange()
        return if(constrange!=null) {
            if(constrange.first>=0 && constrange.last>=0)
                "_prog8_range<uint16_t, ${constrange.first}, ${constrange.last}, ${constrange.step}>()"
            else
                "_prog8_range<int16_t, ${constrange.first}, ${constrange.last}, ${constrange.step}>()"
        } else {
            TODO("rangeexpr $range")
        }
    }

    private fun functioncall(fc: FunctionCall): String {
        val args = fc.arglist.map { expression(it) }.joinToString(", ")
        return "${expression(fc.target)}($args)"
    }

    private fun arrayliteral(arraylv: ArrayLiteralValue): String {
        val values = arraylv.value.map { expression(it) }.joinToString(", ")
        return "{ $values }"
    }

    private fun cname(name: String): String {
        return if (name in listOf("char", "int", "short", "void")) name + "__c_" else name
    }

    private fun datatype(dt: DataType?, arraysize: ArrayIndex?): Pair<String, String> {
        return when (dt) {
            DataType.UBYTE -> Pair("uint8_t", "")
            DataType.BYTE -> Pair("int8_t", "")
            DataType.UWORD -> Pair("uint16_t", "")
            DataType.WORD -> Pair("int16_t", "")
            DataType.FLOAT -> Pair("float", "")
            DataType.STR -> Pair("const char*", "")
            DataType.STR_S -> Pair("const char*", "")
            DataType.ARRAY_UB -> if (arraysize == null) Pair("uint8_t", "[]") else Pair("uint8_t", "[${expression(arraysize.index)}]")
            DataType.ARRAY_B -> if (arraysize == null) Pair("int8_t", "[]") else Pair("int8_t", "[${expression(arraysize.index)}]")
            DataType.ARRAY_UW -> if (arraysize == null) Pair("uint16_t", "[]") else Pair("uint16_t", "[${expression(arraysize.index)}]")
            DataType.ARRAY_W -> if (arraysize == null) Pair("int16_t", "[]") else Pair("int16_t", "[${expression(arraysize.index)}]")
            DataType.ARRAY_F -> if (arraysize == null) Pair("float", "[]") else Pair("float", "[${expression(arraysize.index)}]")
            DataType.STRUCT -> TODO("struct")
            null -> Pair("void", "")
        }
    }
}

class ClangAssemblyProgram(override val name: String, outputDir: Path) : IAssemblyProgram {
    override fun assemble(options: CompilationOptions) {
        println("TODO C++ COMPILE?")
        TODO("not implemented - use g++ or clang to compile the code")
    }
}
