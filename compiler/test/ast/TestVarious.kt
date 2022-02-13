package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.ast.base.Position
import prog8.ast.statements.InlineAssembly

class TestVarious: FunSpec({
    test("symbol names in inline assembly blocks") {
        val names1 = InlineAssembly("""
            
        """, Position.DUMMY).names
        names1 shouldBe emptySet()

        val names2 = InlineAssembly("""
label:   lda #<value
         sta ${'$'}ea
         sta 123
label2: 
         sta  othervalue    ; but not these in the comments
; also not these
        ;;   ...or these
   // valid words  123456
        """, Position.DUMMY).names

        names2 shouldBe setOf("label", "lda", "sta", "ea", "value", "label2", "othervalue", "valid", "words")
    }
})

