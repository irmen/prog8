package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.*
import prog8.code.core.*
import prog8.code.target.C64Target
import prog8.code.target.c64.C64Zeropage
import prog8.codegen.cpu6502.AsmGen
import prog8.compiler.astprocessing.SymbolTableMaker
import prog8tests.helpers.*

class TestArrayInplaceAssign: FunSpec({
    test("assign prefix var to array should compile fine and is not split into inplace array modification") {
        val text = """
            main {
                sub start() {
                    byte[5] array
                    byte bb
                    array[1] = -bb
                }
            }
        """
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }
})

