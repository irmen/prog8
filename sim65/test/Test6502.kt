import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import sim65.components.Cpu6502
import kotlin.test.*


/*

Unit test suite adapted from Py65   https://github.com/mnaberez/py65

Copyright (c) 2008-2018, Mike Naberezny and contributors.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of the copyright holder nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */


@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class Test6502 : TestCommon6502() {
    // NMOS 6502 tests

    // ADC Indirect, Indexed (X)

    @Test
    fun test_adc_ind_indexed_has_page_wrap_bug() {
        mpu.A = 0x01
        mpu.X = 0xFF
        // $0000 ADC ($80,X)
        // $007f Vector to $BBBB (read if page wrapped)
        // $017f Vector to $ABCD (read if no page wrap)
        writeMem(memory, 0x0000, listOf(0x61, 0x80))
        writeMem(memory, 0x007f, listOf(0xBB, 0xBB))
        writeMem(memory, 0x017f, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x01
        memory[0xBBBB] = 0x02
        mpu.step()
        assertEquals(0x03, mpu.A)
    }

    // ADC Indexed, Indirect (Y)

    @Test
    fun test_adc_indexed_ind_y_has_page_wrap_bug() {
        mpu.PC = 0x1000
        mpu.A = 0x42
        mpu.Y = 0x02
        // $1000 ADC ($FF),Y
        writeMem(memory, 0x1000, listOf(0x71, 0xff))
        // Vector
        memory[0x00ff] = 0x10 // low byte
        memory[0x0100] = 0x20 // high byte if no page wrap
        memory[0x0000] = 0x00 // high byte if page wrapped
        // Data
        memory[0x2012] = 0x14 // read if no page wrap
        memory[0x0012] = 0x42 // read if page wrapped
        mpu.step()
        assertEquals(0x84, mpu.A)
    }

    // LDA Zero Page, X-Indexed

    @Test
    fun test_lda_zp_x_indexed_page_wraps() {
        mpu.A = 0x00
        mpu.X = 0xFF
        // $0000 LDA $80,X
        writeMem(memory, 0x0000, listOf(0xB5, 0x80))
        memory[0x007F] = 0x42
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x42, mpu.A)
    }

    // AND Indexed, Indirect (Y)

    @Test
    fun test_and_indexed_ind_y_has_page_wrap_bug() {
        mpu.PC = 0x1000
        mpu.A = 0x42
        mpu.Y = 0x02
        // $1000 AND ($FF),Y
        writeMem(memory, 0x1000, listOf(0x31, 0xff))
        // Vector
        memory[0x00ff] = 0x10 // low byte
        memory[0x0100] = 0x20 // high byte if no page wrap
        memory[0x0000] = 0x00 // high byte if page wrapped
        // Data
        memory[0x2012] = 0x00 // read if no page wrap
        memory[0x0012] = 0xFF // read if page wrapped
        mpu.step()
        assertEquals(0x42, mpu.A)
    }

    // BRK

    @Test
    fun test_brk_preserves_decimal_flag_when_it_is_set() {
        mpu.Status.D = true
        // $C000 BRK
        memory[0xC000] = 0x00
        mpu.PC = 0xC000
        mpu.step()
        assertTrue(mpu.Status.B)
        assertTrue(mpu.Status.D)
    }

    @Test
    fun test_brk_preserves_decimal_flag_when_it_is_clear() {
        // $C000 BRK
        memory[0xC000] = 0x00
        mpu.PC = 0xC000
        mpu.step()
        assertTrue(mpu.Status.B)
        assertFalse(mpu.Status.D)
    }

    // CMP Indirect, Indexed (X)

    @Test
    fun test_cmp_ind_x_has_page_wrap_bug() {
        mpu.A = 0x42
        mpu.X = 0xFF
        // $0000 CMP ($80,X)
        // $007f Vector to $BBBB (read if page wrapped)
        // $017f Vector to $ABCD (read if no page wrap)
        writeMem(memory, 0x0000, listOf(0xC1, 0x80))
        writeMem(memory, 0x007f, listOf(0xBB, 0xBB))
        writeMem(memory, 0x017f, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x00
        memory[0xBBBB] = 0x42
        mpu.step()
        assertTrue(mpu.Status.Z)
    }

    // CMP Indexed, Indirect (Y)

    @Test
    fun test_cmp_indexed_ind_y_has_page_wrap_bug() {
        mpu.PC = 0x1000
        mpu.A = 0x42
        mpu.Y = 0x02
        // $1000 CMP ($FF),Y
        writeMem(memory, 0x1000, listOf(0xd1, 0xff))
        // Vector
        memory[0x00ff] = 0x10 // low byte
        memory[0x0100] = 0x20 // high byte if no page wrap
        memory[0x0000] = 0x00 // high byte if page wrapped
        // Data
        memory[0x2012] = 0x14 // read if no page wrap
        memory[0x0012] = 0x42 // read if page wrapped
        mpu.step()
        assertTrue(mpu.Status.Z)
    }

    // EOR Indirect, Indexed (X)

    @Test
    fun test_eor_ind_x_has_page_wrap_bug() {
        mpu.A = 0xAA
        mpu.X = 0xFF
        // $0000 EOR ($80,X)
        // $007f Vector to $BBBB (read if page wrapped)
        // $017f Vector to $ABCD (read if no page wrap)
        writeMem(memory, 0x0000, listOf(0x41, 0x80))
        writeMem(memory, 0x007f, listOf(0xBB, 0xBB))
        writeMem(memory, 0x017f, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x00
        memory[0xBBBB] = 0xFF
        mpu.step()
        assertEquals(0x55, mpu.A)
    }

    // EOR Indexed, Indirect (Y)

    @Test
    fun test_eor_indexed_ind_y_has_page_wrap_bug() {
        mpu.PC = 0x1000
        mpu.A = 0xAA
        mpu.Y = 0x02
        // $1000 EOR ($FF),Y
        writeMem(memory, 0x1000, listOf(0x51, 0xff))
        // Vector
        memory[0x00ff] = 0x10 // low byte
        memory[0x0100] = 0x20 // high byte if no page wrap
        memory[0x0000] = 0x00 // high byte if page wrapped
        // Data
        memory[0x2012] = 0x00 // read if no page wrap
        memory[0x0012] = 0xFF // read if page wrapped
        mpu.step()
        assertEquals(0x55, mpu.A)
    }

    // LDA Indirect, Indexed (X)

    @Test
    fun test_lda_ind_indexed_x_has_page_wrap_bug() {
        mpu.A = 0x00
        mpu.X = 0xff
        // $0000 LDA ($80,X)
        // $007f Vector to $BBBB (read if page wrapped)
        // $017f Vector to $ABCD (read if no page wrap)
        writeMem(memory, 0x0000, listOf(0xA1, 0x80))
        writeMem(memory, 0x007f, listOf(0xBB, 0xBB))
        writeMem(memory, 0x017f, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x42
        memory[0xBBBB] = 0xEF
        mpu.step()
        assertEquals(0xEF, mpu.A)
    }

    // LDA Indexed, Indirect (Y)

    @Test
    fun test_lda_indexed_ind_y_has_page_wrap_bug() {
        mpu.PC = 0x1000
        mpu.A = 0x00
        mpu.Y = 0x02
        // $1000 LDA ($FF),Y
        writeMem(memory, 0x1000, listOf(0xb1, 0xff))
        // Vector
        memory[0x00ff] = 0x10 // low byte
        memory[0x0100] = 0x20 // high byte if no page wrap
        memory[0x0000] = 0x00 // high byte if page wrapped
        // Data
        memory[0x2012] = 0x14 // read if no page wrap
        memory[0x0012] = 0x42 // read if page wrapped
        mpu.step()
        assertEquals(0x42, mpu.A)
    }

    // LDA Zero Page, X-Indexed

    @Test
    fun test_lda_zp_x_has_page_wrap_bug() {
        mpu.A = 0x00
        mpu.X = 0xFF
        // $0000 LDA $80,X
        writeMem(memory, 0x0000, listOf(0xB5, 0x80))
        memory[0x007F] = 0x42
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x42, mpu.A)
    }

    // JMP Indirect

    @Test
    fun test_jmp_jumps_to_address_with_page_wrap_bug() {
        memory[0x00ff] = 0
        // $0000 JMP ($00)
        writeMem(memory, 0, listOf(0x6c, 0xff, 0x00))
        mpu.step()
        assertEquals(0x6c00, mpu.PC)
        assertEquals((5+Cpu6502.resetCycles).toLong(), mpu.totalCycles)
    }

    // ORA Indexed, Indirect (Y)

    @Test
    fun test_ora_indexed_ind_y_has_page_wrap_bug() {
        mpu.PC = 0x1000
        mpu.A = 0x00
        mpu.Y = 0x02
        // $1000 ORA ($FF),Y
        writeMem(memory, 0x1000, listOf(0x11, 0xff))
        // Vector
        memory[0x00ff] = 0x10 // low byte
        memory[0x0100] = 0x20 // high byte if no page wrap
        memory[0x0000] = 0x00 // high byte if page wrapped
        // Data
        memory[0x2012] = 0x00 // read if no page wrap
        memory[0x0012] = 0x42 // read if page wrapped
        mpu.step()
        assertEquals(0x42, mpu.A)
    }

    // SBC Indexed, Indirect (Y)

    @Test
    fun test_sbc_indexed_ind_y_has_page_wrap_bug() {

        mpu.PC = 0x1000
        mpu.Status.C = true
        mpu.A = 0x42
        mpu.Y = 0x02
        // $1000 SBC ($FF),Y
        writeMem(memory, 0x1000, listOf(0xf1, 0xff))
        // Vector
        memory[0x00ff] = 0x10 // low byte
        memory[0x0100] = 0x20 // high byte if no page wrap
        memory[0x0000] = 0x00 // high byte if page wrapped
        // Data
        memory[0x2012] = 0x02 // read if no page wrap
        memory[0x0012] = 0x03 // read if page wrapped
        mpu.step()
        assertEquals(0x3f, mpu.A)
    }
}
