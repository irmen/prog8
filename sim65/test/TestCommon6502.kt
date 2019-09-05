import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import sim65.components.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


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
abstract class TestCommon6502 {
    // Tests common to 6502-based microprocessors

    val mpu = Cpu6502(true)     // TODO make a 65C02 cpu as well and let the subclasses testsuites define the appropriate instance
    val memory = Ram(0, 0xffff)
    val bus = Bus()

    init {
        bus.add(mpu)
        bus.add(memory)
        memory.fill(0xaa)
        memory.write(Cpu6502.RESET_vector, 0)
        memory.write(Cpu6502.RESET_vector+1, 0)
        mpu.reset()
        mpu.Status.I = false        // allow interrupts again
    }

    companion object {
        // processor flags
        const val fNEGATIVE = 128
        const val fOVERFLOW = 64
        const val fUNUSED = 32
        const val fBREAK = 16
        const val fDECIMAL = 8
        const val fINTERRUPT = 4
        const val fZERO = 2
        const val fCARRY = 1
    }

    // test helpers
    fun writeMem(memory: MemMappedComponent, startAddress: Address, data: Iterable<UByte>) {
        var addr = startAddress
        data.forEach { memory.write(addr++, it) }
    }


    // Reset
    @Test
    fun test_reset_sets_registers_to_initial_states() {

        mpu.reset()
        assertEquals(0xFD, mpu.SP)
        assertEquals(0, mpu.A)
        assertEquals(0, mpu.X)
        assertEquals(0, mpu.Y)
        assertTrue(mpu.Status.I)   // the other status flags are undefined after reset
    }

    // ADC Absolute
    @Test
    fun test_adc_bcd_off_absolute_carry_clear_in_accumulator_zeroes() {
        mpu.A = 0
        // $0000 ADC $C000
        writeMem(memory, 0x0000, listOf(0x6D, 0x00, 0xC0))
        // assertEquals(0x10000, memory.size)

        memory[0xC000] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_adc_bcd_off_absolute_carry_set_in_accumulator_zero() {
        mpu.A = 0
        mpu.Status.C = true
        // $0000 ADC $C000
        writeMem(memory, 0x0000, listOf(0x6D, 0x00, 0xC0))
        memory[0xC000] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x01, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C)
    }

    @Test
    fun test_adc_bcd_off_absolute_carry_clear_in_no_carry_clear_out() {
        mpu.A = 0x01
        // $0000 ADC $C000
        writeMem(memory, 0x0000, listOf(0x6D, 0x00, 0xC0))
        memory[0xC000] = 0xFE
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xFF, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.Z)
    }

    @Test
    fun test_adc_bcd_off_absolute_carry_clear_in_carry_set_out() {
        mpu.A = 0x02
        // $0000 ADC $C000
        writeMem(memory, 0x0000, listOf(0x6D, 0x00, 0xC0))
        memory[0xC000] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x01, mpu.A)
        assertTrue(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
    }

    @Test
    fun test_adc_bcd_off_absolute_overflow_clr_no_carry_01_plus_01() {
        mpu.Status.C = false
        mpu.A = 0x01
        // $0000 ADC $C000
        writeMem(memory, 0x0000, listOf(0x6D, 0x00, 0xC0))
        memory[0xC000] = 0x01
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x02, mpu.A)
        assertFalse(mpu.Status.V)
    }

    @Test
    fun test_adc_bcd_off_absolute_overflow_clr_no_carry_01_plus_ff() {
        mpu.Status.C = false
        mpu.A = 0x01
        // $0000 ADC $C000
        writeMem(memory, 0x0000, listOf(0x6D, 0x00, 0xC0))
        memory[0xC000] = 0xff
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.V)
    }

    @Test
    fun test_adc_bcd_off_absolute_overflow_set_no_carry_7f_plus_01() {
        mpu.Status.C = false
        mpu.A = 0x7f
        // $0000 ADC $C000
        writeMem(memory, 0x0000, listOf(0x6D, 0x00, 0xC0))
        memory[0xC000] = 0x01
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.V)
    }

    @Test
    fun test_adc_bcd_off_absolute_overflow_set_no_carry_80_plus_ff() {
        mpu.Status.C = false
        mpu.A = 0x80
        // $0000 ADC $C000
        writeMem(memory, 0x0000, listOf(0x6D, 0x00, 0xC0))
        memory[0xC000] = 0xff
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x7f, mpu.A)
        assertTrue(mpu.Status.V)
    }

    @Test
    fun test_adc_bcd_off_absolute_overflow_set_on_40_plus_40() {
        mpu.Status.V = false
        mpu.A = 0x40
        // $0000 ADC $C000
        writeMem(memory, 0x0000, listOf(0x6D, 0x00, 0xC0))
        memory[0xC000] = 0x40
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertTrue(mpu.Status.V)
        assertFalse(mpu.Status.Z)
    }

    // ADC Zero Page
    @Test
    fun test_adc_bcd_off_zp_carry_clear_in_accumulator_zeroes() {

        mpu.A = 0
        // $0000 ADC $00B0
        writeMem(memory, 0x0000, listOf(0x65, 0xB0))
        memory[0x00B0] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_zp_carry_set_in_accumulator_zero() {

        mpu.A = 0
        mpu.Status.C = true
        // $0000 ADC $00B0
        writeMem(memory, 0x0000, listOf(0x65, 0xB0))
        memory[0x00B0] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x01, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_adc_bcd_off_zp_carry_clear_in_no_carry_clear_out() {

        mpu.A = 0x01
        // $0000 ADC $00B0
        writeMem(memory, 0x0000, listOf(0x65, 0xB0))
        memory[0x00B0] = 0xFE
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_zp_carry_clear_in_carry_set_out() {

        mpu.A = 0x02
        // $0000 ADC $00B0
        writeMem(memory, 0x0000, listOf(0x65, 0xB0))
        memory[0x00B0] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x01, mpu.A)
        assertTrue(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_zp_overflow_clr_no_carry_01_plus_01() {

        mpu.Status.C = false
        mpu.A = 0x01
        // $0000 ADC $00B0
        writeMem(memory, 0x0000, listOf(0x65, 0xB0))
        memory[0x00B0] = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x02, mpu.A)
        assertFalse(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_zp_overflow_clr_no_carry_01_plus_ff() {

        mpu.Status.C = false
        mpu.A = 0x01
        // $0000 ADC $00B0
        writeMem(memory, 0x0000, listOf(0x65, 0xB0))
        memory[0x00B0] = 0xff
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_zp_overflow_set_no_carry_7f_plus_01() {

        mpu.Status.C = false
        mpu.A = 0x7f
        // $0000 ADC $00B0
        writeMem(memory, 0x0000, listOf(0x65, 0xB0))
        memory[0x00B0] = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_zp_overflow_set_no_carry_80_plus_ff() {

        mpu.Status.C = false
        mpu.A = 0x80
        // $0000 ADC $00B0
        writeMem(memory, 0x0000, listOf(0x65, 0xB0))
        memory[0x00B0] = 0xff
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x7f, mpu.A)
        assertTrue(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_zp_overflow_set_on_40_plus_40() {

        mpu.A = 0x40
        mpu.Status.V = false
        // $0000 ADC $00B0
        writeMem(memory, 0x0000, listOf(0x65, 0xB0))
        memory[0x00B0] = 0x40
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertTrue(mpu.Status.V)
        assertFalse(mpu.Status.Z)

        // ADC Immediate

    }

    @Test
    fun test_adc_bcd_off_immediate_carry_clear_in_accumulator_zeroes() {

        mpu.A = 0
        // $0000 ADC #$00
        writeMem(memory, 0x0000, listOf(0x69, 0x00))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_immediate_carry_set_in_accumulator_zero() {

        mpu.A = 0
        mpu.Status.C = true
        // $0000 ADC #$00
        writeMem(memory, 0x0000, listOf(0x69, 0x00))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x01, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_adc_bcd_off_immediate_carry_clear_in_no_carry_clear_out() {

        mpu.A = 0x01
        // $0000 ADC #$FE
        writeMem(memory, 0x0000, listOf(0x69, 0xFE))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_immediate_carry_clear_in_carry_set_out() {

        mpu.A = 0x02
        // $0000 ADC #$FF
        writeMem(memory, 0x0000, listOf(0x69, 0xFF))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x01, mpu.A)
        assertTrue(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_immediate_overflow_clr_no_carry_01_plus_01() {

        mpu.Status.C = false
        mpu.A = 0x01
        // $0000 ADC #$01
        writeMem(memory, 0x000, listOf(0x69, 0x01))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x02, mpu.A)
        assertFalse(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_immediate_overflow_clr_no_carry_01_plus_ff() {

        mpu.Status.C = false
        mpu.A = 0x01
        // $0000 ADC #$FF
        writeMem(memory, 0x000, listOf(0x69, 0xff))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_immediate_overflow_set_no_carry_7f_plus_01() {

        mpu.Status.C = false
        mpu.A = 0x7f
        // $0000 ADC #$01
        writeMem(memory, 0x000, listOf(0x69, 0x01))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_immediate_overflow_set_no_carry_80_plus_ff() {

        mpu.Status.C = false
        mpu.A = 0x80
        // $0000 ADC #$FF
        writeMem(memory, 0x000, listOf(0x69, 0xff))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x7f, mpu.A)
        assertTrue(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_immediate_overflow_set_on_40_plus_40() {

        mpu.A = 0x40
        // $0000 ADC #$40
        writeMem(memory, 0x0000, listOf(0x69, 0x40))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertTrue(mpu.Status.V)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_on_immediate_79_plus_00_carry_set() {

        mpu.Status.D = true
        mpu.Status.C = true
        mpu.A = 0x79
        // $0000 ADC #$00
        writeMem(memory, 0x0000, listOf(0x69, 0x00))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.V)
        assertTrue(mpu.Status.N)
    }

    @Test
    fun test_adc_bcd_on_immediate_6f_plus_00_carry_set() {

        mpu.Status.D = true
        mpu.Status.C = true
        mpu.A = 0x6f
        // $0000 ADC #$00
        writeMem(memory, 0x0000, listOf(0x69, 0x00))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x76, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.V)
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_adc_bcd_on_immediate_9c_plus_9d() {

        mpu.Status.D = true
        mpu.Status.C = false
        mpu.A = 0x9c
        // $0000 ADC #$9d
        // $0002 ADC #$9d
        writeMem(memory, 0x0000, listOf(0x69, 0x9d))
        writeMem(memory, 0x0002, listOf(0x69, 0x9d))
        mpu.step()
        assertEquals(0x9f, mpu.A)
        assertTrue(mpu.Status.C)
        mpu.step()
        assertEquals(0x0004, mpu.PC)
        assertEquals(0x93, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.V)
    }

    // ADC Absolute, X-Indexed

    @Test
    fun test_adc_bcd_off_abs_x_carry_clear_in_accumulator_zeroes() {

        mpu.A = 0x00
        mpu.X = 0x03
        // $0000 ADC $C000,X
        writeMem(memory, 0x0000, listOf(0x7D, 0x00, 0xC0))
        memory[0xC000 + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_abs_x_carry_set_in_accumulator_zero() {

        mpu.A = 0
        mpu.X = 0x03
        mpu.Status.C = true
        // $0000 ADC $C000,X
        writeMem(memory, 0x0000, listOf(0x7D, 0x00, 0xC0))
        memory[0xC000 + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x01, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_adc_bcd_off_abs_x_carry_clear_in_no_carry_clear_out() {

        mpu.A = 0x01
        mpu.X = 0x03
        // $0000 ADC $C000,X
        writeMem(memory, 0x0000, listOf(0x7D, 0x00, 0xC0))
        memory[0xC000 + mpu.X] = 0xFE
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xFF, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_abs_x_carry_clear_in_carry_set_out() {

        mpu.A = 0x02
        mpu.X = 0x03
        // $0000 ADC $C000,X
        writeMem(memory, 0x0000, listOf(0x7D, 0x00, 0xC0))
        memory[0xC000 + mpu.X] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x01, mpu.A)
        assertTrue(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_abs_x_overflow_clr_no_carry_01_plus_01() {

        mpu.Status.C = false
        mpu.A = 0x01
        // $0000 ADC $C000,X
        writeMem(memory, 0x0000, listOf(0x7D, 0x00, 0xC0))
        memory[0xC000 + mpu.X] = 0x01
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x02, mpu.A)
        assertFalse(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_abs_x_overflow_clr_no_carry_01_plus_ff() {

        mpu.Status.C = false
        mpu.A = 0x01
        // $0000 ADC $C000,X
        writeMem(memory, 0x0000, listOf(0x7D, 0x00, 0xC0))
        memory[0xC000 + mpu.X] = 0xff
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_abs_x_overflow_set_no_carry_7f_plus_01() {

        mpu.Status.C = false
        mpu.A = 0x7f
        // $0000 ADC $C000,X
        writeMem(memory, 0x0000, listOf(0x7D, 0x00, 0xC0))
        memory[0xC000 + mpu.X] = 0x01
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_abs_x_overflow_set_no_carry_80_plus_ff() {

        mpu.Status.C = false
        mpu.A = 0x80
        // $0000 ADC $C000,X
        writeMem(memory, 0x0000, listOf(0x7D, 0x00, 0xC0))
        memory[0xC000 + mpu.X] = 0xff
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x7f, mpu.A)
        assertTrue(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_abs_x_overflow_set_on_40_plus_40() {

        mpu.Status.V = false
        mpu.A = 0x40
        mpu.X = 0x03
        // $0000 ADC $C000,X
        writeMem(memory, 0x0000, listOf(0x7D, 0x00, 0xC0))
        memory[0xC000 + mpu.X] = 0x40
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertTrue(mpu.Status.V)
        assertFalse(mpu.Status.Z)

        // ADC Absolute, Y-Indexed

    }

    @Test
    fun test_adc_bcd_off_abs_y_carry_clear_in_accumulator_zeroes() {

        mpu.A = 0x00
        mpu.Y = 0x03
        // $0000 ADC $C000,Y
        writeMem(memory, 0x0000, listOf(0x79, 0x00, 0xC0))
        memory[0xC000 + mpu.Y] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_abs_y_carry_set_in_accumulator_zero() {

        mpu.A = 0
        mpu.Y = 0x03
        mpu.Status.C = true
        // $0000 ADC $C000,Y
        writeMem(memory, 0x0000, listOf(0x79, 0x00, 0xC0))
        memory[0xC000 + mpu.Y] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x01, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_adc_bcd_off_abs_y_carry_clear_in_no_carry_clear_out() {

        mpu.A = 0x01
        mpu.Y = 0x03
        // $0000 ADC $C000,Y
        writeMem(memory, 0x0000, listOf(0x79, 0x00, 0xC0))
        memory[0xC000 + mpu.Y] = 0xFE
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xFF, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_abs_y_carry_clear_in_carry_set_out() {

        mpu.A = 0x02
        mpu.Y = 0x03
        // $0000 ADC $C000,Y
        writeMem(memory, 0x0000, listOf(0x79, 0x00, 0xC0))
        memory[0xC000 + mpu.Y] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x01, mpu.A)
        assertTrue(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_abs_y_overflow_clr_no_carry_01_plus_01() {

        mpu.Status.C = false
        mpu.A = 0x01
        // $0000 ADC $C000,Y
        writeMem(memory, 0x0000, listOf(0x79, 0x00, 0xC0))
        memory[0xC000 + mpu.Y] = 0x01
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x02, mpu.A)
        assertFalse(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_abs_y_overflow_clr_no_carry_01_plus_ff() {

        mpu.Status.C = false
        mpu.A = 0x01
        // $0000 ADC $C000,Y
        writeMem(memory, 0x0000, listOf(0x79, 0x00, 0xC0))
        memory[0xC000 + mpu.Y] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_abs_y_overflow_set_no_carry_7f_plus_01() {

        mpu.Status.C = false
        mpu.A = 0x7f
        // $0000 ADC $C000,Y
        writeMem(memory, 0x0000, listOf(0x79, 0x00, 0xC0))
        memory[0xC000 + mpu.Y] = 0x01
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_abs_y_overflow_set_no_carry_80_plus_ff() {

        mpu.Status.C = false
        mpu.A = 0x80
        // $0000 ADC $C000,Y
        writeMem(memory, 0x0000, listOf(0x79, 0x00, 0xC0))
        memory[0xC000 + mpu.Y] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x7f, mpu.A)
        assertTrue(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_abs_y_overflow_set_on_40_plus_40() {

        mpu.Status.V = false
        mpu.A = 0x40
        mpu.Y = 0x03
        // $0000 ADC $C000,Y
        writeMem(memory, 0x0000, listOf(0x79, 0x00, 0xC0))
        memory[0xC000 + mpu.Y] = 0x40
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertTrue(mpu.Status.V)
        assertFalse(mpu.Status.Z)

        // ADC Zero Page, X-Indexed

    }

    @Test
    fun test_adc_bcd_off_zp_x_carry_clear_in_accumulator_zeroes() {

        mpu.A = 0x00
        mpu.X = 0x03
        // $0000 ADC $0010,X
        writeMem(memory, 0x0000, listOf(0x75, 0x10))
        memory[0x0010 + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_zp_x_carry_set_in_accumulator_zero() {

        mpu.A = 0
        mpu.X = 0x03
        mpu.Status.C = true
        // $0000 ADC $0010,X
        writeMem(memory, 0x0000, listOf(0x75, 0x10))
        memory[0x0010 + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x01, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_adc_bcd_off_zp_x_carry_clear_in_no_carry_clear_out() {

        mpu.A = 0x01
        mpu.X = 0x03
        // $0000 ADC $0010,X
        writeMem(memory, 0x0000, listOf(0x75, 0x10))
        memory[0x0010 + mpu.X] = 0xFE
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_zp_x_carry_clear_in_carry_set_out() {

        mpu.A = 0x02
        mpu.X = 0x03
        // $0000 ADC $0010,X
        writeMem(memory, 0x0000, listOf(0x75, 0x10))
        memory[0x0010 + mpu.X] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x01, mpu.A)
        assertTrue(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_zp_x_overflow_clr_no_carry_01_plus_01() {

        mpu.Status.C = false
        mpu.A = 0x01
        mpu.X = 0x03
        // $0000 ADC $0010,X
        writeMem(memory, 0x0000, listOf(0x75, 0x10))
        memory[0x0010 + mpu.X] = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x02, mpu.A)
        assertFalse(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_zp_x_overflow_clr_no_carry_01_plus_ff() {

        mpu.Status.C = false
        mpu.A = 0x01
        mpu.X = 0x03
        // $0000 ADC $0010,X
        writeMem(memory, 0x0000, listOf(0x75, 0x10))
        memory[0x0010 + mpu.X] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_zp_x_overflow_set_no_carry_7f_plus_01() {

        mpu.Status.C = false
        mpu.A = 0x7f
        mpu.X = 0x03
        // $0000 ADC $0010,X
        writeMem(memory, 0x0000, listOf(0x75, 0x10))
        memory[0x0010 + mpu.X] = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_zp_x_overflow_set_no_carry_80_plus_ff() {

        mpu.Status.C = false
        mpu.A = 0x80
        mpu.X = 0x03
        // $0000 ADC $0010,X
        writeMem(memory, 0x0000, listOf(0x75, 0x10))
        memory[0x0010 + mpu.X] = 0xff
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x7f, mpu.A)
        assertTrue(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_zp_x_overflow_set_on_40_plus_40() {

        mpu.Status.V = false
        mpu.A = 0x40
        mpu.X = 0x03
        // $0000 ADC $0010,X
        writeMem(memory, 0x0000, listOf(0x75, 0x10))
        memory[0x0010 + mpu.X] = 0x40
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertTrue(mpu.Status.V)
        assertFalse(mpu.Status.Z)

        // ADC Indirect, Indexed (X)

    }

    @Test
    fun test_adc_bcd_off_ind_indexed_carry_clear_in_accumulator_zeroes() {

        mpu.A = 0x00
        mpu.X = 0x03
        // $0000 ADC ($0010,X)
        // $0013 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x61, 0x10))
        writeMem(memory, 0x0013, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_ind_indexed_carry_set_in_accumulator_zero() {

        mpu.A = 0
        mpu.X = 0x03
        mpu.Status.C = true
        // $0000 ADC ($0010,X)
        // $0013 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x61, 0x10))
        writeMem(memory, 0x0013, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x01, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_adc_bcd_off_ind_indexed_carry_clear_in_no_carry_clear_out() {

        mpu.A = 0x01
        mpu.X = 0x03
        // $0000 ADC ($0010,X)
        // $0013 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x61, 0x10))
        writeMem(memory, 0x0013, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0xFE
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_ind_indexed_carry_clear_in_carry_set_out() {

        mpu.A = 0x02
        mpu.X = 0x03
        // $0000 ADC ($0010,X)
        // $0013 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x61, 0x10))
        writeMem(memory, 0x0013, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x01, mpu.A)
        assertTrue(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_ind_indexed_overflow_clr_no_carry_01_plus_01() {

        mpu.Status.C = false
        mpu.A = 0x01
        mpu.X = 0x03
        // $0000 ADC ($0010,X)
        // $0013 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x61, 0x10))
        writeMem(memory, 0x0013, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x02, mpu.A)
        assertFalse(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_ind_indexed_overflow_clr_no_carry_01_plus_ff() {

        mpu.Status.C = false
        mpu.A = 0x01
        mpu.X = 0x03
        // $0000 ADC ($0010,X)
        // $0013 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x61, 0x10))
        writeMem(memory, 0x0013, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_ind_indexed_overflow_set_no_carry_7f_plus_01() {

        mpu.Status.C = false
        mpu.A = 0x7f
        mpu.X = 0x03
        // $0000 ADC ($0010,X)
        // $0013 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x61, 0x10))
        writeMem(memory, 0x0013, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_ind_indexed_overflow_set_no_carry_80_plus_ff() {

        mpu.Status.C = false
        mpu.A = 0x80
        mpu.X = 0x03
        // $0000 ADC ($0010,X)
        // $0013 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x61, 0x10))
        writeMem(memory, 0x0013, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x7f, mpu.A)
        assertTrue(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_ind_indexed_overflow_set_on_40_plus_40() {

        mpu.Status.V = false
        mpu.A = 0x40
        mpu.X = 0x03
        // $0000 ADC ($0010,X)
        // $0013 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x61, 0x10))
        writeMem(memory, 0x0013, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x40
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertTrue(mpu.Status.V)
        assertFalse(mpu.Status.Z)

        // ADC Indexed, Indirect (Y)

    }

    @Test
    fun test_adc_bcd_off_indexed_ind_carry_clear_in_accumulator_zeroes() {

        mpu.A = 0x00
        mpu.Y = 0x03
        // $0000 ADC ($0010),Y
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x71, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_indexed_ind_carry_set_in_accumulator_zero() {

        mpu.A = 0
        mpu.Y = 0x03
        mpu.Status.C = true
        // $0000 ADC ($0010),Y
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x71, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x01, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_adc_bcd_off_indexed_ind_carry_clear_in_no_carry_clear_out() {

        mpu.A = 0x01
        mpu.Y = 0x03
        // $0000 ADC ($0010),Y
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x71, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0xFE
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_indexed_ind_carry_clear_in_carry_set_out() {

        mpu.A = 0x02
        mpu.Y = 0x03
        // $0000 ADC ($0010),Y
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x71, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x01, mpu.A)
        assertTrue(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_adc_bcd_off_indexed_ind_overflow_clr_no_carry_01_plus_01() {

        mpu.Status.C = false
        mpu.A = 0x01
        mpu.Y = 0x03
        // $0000 $0000 ADC ($0010),Y
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x71, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x02, mpu.A)
        assertFalse(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_indexed_ind_overflow_clr_no_carry_01_plus_ff() {

        mpu.Status.C = false
        mpu.A = 0x01
        mpu.Y = 0x03
        // $0000 ADC ($0010),Y
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x71, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_indexed_ind_overflow_set_no_carry_7f_plus_01() {

        mpu.Status.C = false
        mpu.A = 0x7f
        mpu.Y = 0x03
        // $0000 ADC ($0010),Y
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x71, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_indexed_ind_overflow_set_no_carry_80_plus_ff() {

        mpu.Status.C = false
        mpu.A = 0x80
        mpu.Y = 0x03
        // $0000 $0000 ADC ($0010),Y
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x71, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x7f, mpu.A)
        assertTrue(mpu.Status.V)

    }

    @Test
    fun test_adc_bcd_off_indexed_ind_overflow_set_on_40_plus_40() {

        mpu.Status.V = false
        mpu.A = 0x40
        mpu.Y = 0x03
        // $0000 ADC ($0010),Y
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x71, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x40
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertTrue(mpu.Status.V)
        assertFalse(mpu.Status.Z)
    }

    // AND (Absolute)

    @Test
    fun test_and_absolute_all_zeros_setting_zero_flag() {

        mpu.A = 0xFF
        // $0000 AND $ABCD
        writeMem(memory, 0x0000, listOf(0x2D, 0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_and_absolute_zeros_and_ones_setting_negative_flag() {

        mpu.A = 0xFF
        // $0000 AND $ABCD
        writeMem(memory, 0x0000, listOf(0x2D, 0xCD, 0xAB))
        memory[0xABCD] = 0xAA
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xAA, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)
    }

    // AND (Absolute)

    @Test
    fun test_and_zp_all_zeros_setting_zero_flag() {

        mpu.A = 0xFF
        // $0000 AND $0010
        writeMem(memory, 0x0000, listOf(0x25, 0x10))
        memory[0x0010] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_and_zp_zeros_and_ones_setting_negative_flag() {

        mpu.A = 0xFF
        // $0000 AND $0010
        writeMem(memory, 0x0000, listOf(0x25, 0x10))
        memory[0x0010] = 0xAA
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xAA, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // AND (Immediate)

    }

    @Test
    fun test_and_immediate_all_zeros_setting_zero_flag() {

        mpu.A = 0xFF
        // $0000 AND #$00
        writeMem(memory, 0x0000, listOf(0x29, 0x00))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_and_immediate_zeros_and_ones_setting_negative_flag() {

        mpu.A = 0xFF
        // $0000 AND #$AA
        writeMem(memory, 0x0000, listOf(0x29, 0xAA))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xAA, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // AND (Absolute, X-Indexed)

    }

    @Test
    fun test_and_abs_x_all_zeros_setting_zero_flag() {

        mpu.A = 0xFF
        mpu.X = 0x03
        // $0000 AND $ABCD,X
        writeMem(memory, 0x0000, listOf(0x3d, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_and_abs_x_zeros_and_ones_setting_negative_flag() {

        mpu.A = 0xFF
        mpu.X = 0x03
        // $0000 AND $ABCD,X
        writeMem(memory, 0x0000, listOf(0x3d, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0xAA
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xAA, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // AND (Absolute, Y-Indexed)

    }

    @Test
    fun test_and_abs_y_all_zeros_setting_zero_flag() {

        mpu.A = 0xFF
        mpu.Y = 0x03
        // $0000 AND $ABCD,X
        writeMem(memory, 0x0000, listOf(0x39, 0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_and_abs_y_zeros_and_ones_setting_negative_flag() {
        mpu.A = 0xFF
        mpu.Y = 0x03
        // $0000 AND $ABCD,X
        writeMem(memory, 0x0000, listOf(0x39, 0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0xAA
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xAA, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)
    }

    // AND Indirect, Indexed (X)

    @Test
    fun test_and_ind_indexed_x_all_zeros_setting_zero_flag() {

        mpu.A = 0xFF
        mpu.X = 0x03
        // $0000 AND ($0010,X)
        // $0013 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x21, 0x10))
        writeMem(memory, 0x0013, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_and_ind_indexed_x_zeros_and_ones_setting_negative_flag() {

        mpu.A = 0xFF
        mpu.X = 0x03
        // $0000 AND ($0010,X)
        // $0013 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x21, 0x10))
        writeMem(memory, 0x0013, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0xAA
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xAA, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // AND Indexed, Indirect (Y)

    }

    @Test
    fun test_and_indexed_ind_y_all_zeros_setting_zero_flag() {

        mpu.A = 0xFF
        mpu.Y = 0x03
        // $0000 AND ($0010),Y
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x31, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_and_indexed_ind_y_zeros_and_ones_setting_negative_flag() {

        mpu.A = 0xFF
        mpu.Y = 0x03
        // $0000 AND ($0010),Y
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x31, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0xAA
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xAA, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // AND Zero Page, X-Indexed

    }

    @Test
    fun test_and_zp_x_all_zeros_setting_zero_flag() {

        mpu.A = 0xFF
        mpu.X = 0x03
        // $0000 AND $0010,X
        writeMem(memory, 0x0000, listOf(0x35, 0x10))
        memory[0x0010 + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_and_zp_x_all_zeros_and_ones_setting_negative_flag() {

        mpu.A = 0xFF
        mpu.X = 0x03
        // $0000 AND $0010,X
        writeMem(memory, 0x0000, listOf(0x35, 0x10))
        memory[0x0010 + mpu.X] = 0xAA
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xAA, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // ASL Accumulator

    }

    @Test
    fun test_asl_accumulator_sets_z_flag() {

        mpu.A = 0x00
        // $0000 ASL A
        memory[0x0000] = 0x0A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_asl_accumulator_sets_n_flag() {

        mpu.A = 0x40
        // $0000 ASL A
        memory[0x0000] = 0x0A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_asl_accumulator_shifts_out_zero() {

        mpu.A = 0x7F
        // $0000 ASL A
        memory[0x0000] = 0x0A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xFE, mpu.A)
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_asl_accumulator_shifts_out_one() {

        mpu.A = 0xFF
        // $0000 ASL A
        memory[0x0000] = 0x0A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xFE, mpu.A)
        assertTrue(mpu.Status.C)

    }

    @Test
    fun test_asl_accumulator_80_sets_z_flag() {

        mpu.A = 0x80
        mpu.Status.Z = false
        // $0000 ASL A
        memory[0x0000] = 0x0A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)

        // ASL Absolute

    }

    @Test
    fun test_asl_absolute_sets_z_flag() {

        // $0000 ASL $ABCD
        writeMem(memory, 0x0000, listOf(0x0E, 0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_asl_absolute_sets_n_flag() {

        // $0000 ASL $ABCD
        writeMem(memory, 0x0000, listOf(0x0E, 0xCD, 0xAB))
        memory[0xABCD] = 0x40
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, memory[0xABCD])
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_asl_absolute_shifts_out_zero() {

        mpu.A = 0xAA
        // $0000 ASL $ABCD
        writeMem(memory, 0x0000, listOf(0x0E, 0xCD, 0xAB))
        memory[0xABCD] = 0x7F
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xAA, mpu.A)
        assertEquals(0xFE, memory[0xABCD])
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_asl_absolute_shifts_out_one() {

        mpu.A = 0xAA
        // $0000 ASL $ABCD
        writeMem(memory, 0x0000, listOf(0x0E, 0xCD, 0xAB))
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xAA, mpu.A)
        assertEquals(0xFE, memory[0xABCD])
        assertTrue(mpu.Status.C)

        // ASL Zero Page

    }

    @Test
    fun test_asl_zp_sets_z_flag() {

        // $0000 ASL $0010
        writeMem(memory, 0x0000, listOf(0x06, 0x10))
        memory[0x0010] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_asl_zp_sets_n_flag() {

        // $0000 ASL $0010
        writeMem(memory, 0x0000, listOf(0x06, 0x10))
        memory[0x0010] = 0x40
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, memory[0x0010])
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_asl_zp_shifts_out_zero() {

        mpu.A = 0xAA
        // $0000 ASL $0010
        writeMem(memory, 0x0000, listOf(0x06, 0x10))
        memory[0x0010] = 0x7F
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xAA, mpu.A)
        assertEquals(0xFE, memory[0x0010])
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_asl_zp_shifts_out_one() {

        mpu.A = 0xAA
        // $0000 ASL $0010
        writeMem(memory, 0x0000, listOf(0x06, 0x10))
        memory[0x0010] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xAA, mpu.A)
        assertEquals(0xFE, memory[0x0010])
        assertTrue(mpu.Status.C)

        // ASL Absolute, X-Indexed

    }

    @Test
    fun test_asl_abs_x_indexed_sets_z_flag() {

        mpu.X = 0x03
        // $0000 ASL $ABCD,X
        writeMem(memory, 0x0000, listOf(0x1E, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD + mpu.X])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_asl_abs_x_indexed_sets_n_flag() {

        mpu.X = 0x03
        // $0000 ASL $ABCD,X
        writeMem(memory, 0x0000, listOf(0x1E, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x40
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, memory[0xABCD + mpu.X])
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_asl_abs_x_indexed_shifts_out_zero() {

        mpu.A = 0xAA
        mpu.X = 0x03
        // $0000 ASL $ABCD,X
        writeMem(memory, 0x0000, listOf(0x1E, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x7F
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xAA, mpu.A)
        assertEquals(0xFE, memory[0xABCD + mpu.X])
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_asl_abs_x_indexed_shifts_out_one() {

        mpu.A = 0xAA
        mpu.X = 0x03
        // $0000 ASL $ABCD,X
        writeMem(memory, 0x0000, listOf(0x1E, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xAA, mpu.A)
        assertEquals(0xFE, memory[0xABCD + mpu.X])
        assertTrue(mpu.Status.C)

        // ASL Zero Page, X-Indexed

    }

    @Test
    fun test_asl_zp_x_indexed_sets_z_flag() {

        mpu.X = 0x03
        // $0000 ASL $0010,X
        writeMem(memory, 0x0000, listOf(0x16, 0x10))
        memory[0x0010 + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010 + mpu.X])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_asl_zp_x_indexed_sets_n_flag() {

        mpu.X = 0x03
        // $0000 ASL $0010,X
        writeMem(memory, 0x0000, listOf(0x16, 0x10))
        memory[0x0010 + mpu.X] = 0x40
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, memory[0x0010 + mpu.X])
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_asl_zp_x_indexed_shifts_out_zero() {

        mpu.X = 0x03
        mpu.A = 0xAA
        // $0000 ASL $0010,X
        writeMem(memory, 0x0000, listOf(0x16, 0x10))
        memory[0x0010 + mpu.X] = 0x7F
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xAA, mpu.A)
        assertEquals(0xFE, memory[0x0010 + mpu.X])
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_asl_zp_x_indexed_shifts_out_one() {

        mpu.X = 0x03
        mpu.A = 0xAA
        // $0000 ASL $0010,X
        writeMem(memory, 0x0000, listOf(0x16, 0x10))
        memory[0x0010 + mpu.X] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xAA, mpu.A)
        assertEquals(0xFE, memory[0x0010 + mpu.X])
        assertTrue(mpu.Status.C)

        // BCC

    }

    @Test
    fun test_bcc_carry_clear_branches_relative_forward() {

        mpu.Status.C = false
        // $0000 BCC +6
        writeMem(memory, 0x0000, listOf(0x90, 0x06))
        mpu.step()
        assertEquals(0x0002 + 0x06, mpu.PC)

    }

    @Test
    fun test_bcc_carry_clear_branches_relative_backward() {

        mpu.Status.C = false
        mpu.PC = 0x0050
        val rel = 256 + (-6)  // two's complement of 6
        // $0000 BCC -6
        writeMem(memory, 0x0050, listOf(0x90, rel.toShort()))
        mpu.step()
        assertEquals(0x0052 - 6, mpu.PC)

    }

    @Test
    fun test_bcc_carry_set_does_not_branch() {

        mpu.Status.C = true
        // $0000 BCC +6
        writeMem(memory, 0x0000, listOf(0x90, 0x06))
        mpu.step()
        assertEquals(0x0002, mpu.PC)

        // BCS

    }

    @Test
    fun test_bcs_carry_set_branches_relative_forward() {

        mpu.Status.C = true
        // $0000 BCS +6
        writeMem(memory, 0x0000, listOf(0xB0, 0x06))
        mpu.step()
        assertEquals(0x0002 + 0x06, mpu.PC)

    }

    @Test
    fun test_bcs_carry_set_branches_relative_backward() {

        mpu.Status.C = true
        mpu.PC = 0x0050
        val rel = 256 + (-6)  // two's complement of 6
        // $0000 BCS -6
        writeMem(memory, 0x0050, listOf(0xB0, rel.toShort()))
        mpu.step()
        assertEquals(0x0052 - 6, mpu.PC)

    }

    @Test
    fun test_bcs_carry_clear_does_not_branch() {

        mpu.Status.C = false
        // $0000 BCS +6
        writeMem(memory, 0x0000, listOf(0xB0, 0x06))
        mpu.step()
        assertEquals(0x0002, mpu.PC)

        // BEQ

    }

    @Test
    fun test_beq_zero_set_branches_relative_forward() {

        mpu.Status.Z = true
        // $0000 BEQ +6
        writeMem(memory, 0x0000, listOf(0xF0, 0x06))
        mpu.step()
        assertEquals(0x0002 + 0x06, mpu.PC)

    }

    @Test
    fun test_beq_zero_set_branches_relative_backward() {

        mpu.Status.Z = true
        mpu.PC = 0x0050
        val rel = 256 + (-6)  // two's complement of 6
        // $0000 BEQ -6
        writeMem(memory, 0x0050, listOf(0xF0, rel.toShort()))
        mpu.step()
        assertEquals(0x0052 - 6, mpu.PC)

    }

    @Test
    fun test_beq_zero_clear_does_not_branch() {

        mpu.Status.Z = false
        // $0000 BEQ +6
        writeMem(memory, 0x0000, listOf(0xF0, 0x06))
        mpu.step()
        assertEquals(0x0002, mpu.PC)

        // BIT (Absolute)

    }

    @Test
    fun test_bit_abs_copies_bit_7_of_memory_to_n_flag_when_0() {

        mpu.Status.N = false
        // $0000 BIT $FEED
        writeMem(memory, 0x0000, listOf(0x2C, 0xED, 0xFE))
        memory[0xFEED] = 0xFF
        mpu.A = 0xFF
        mpu.step()
        assertTrue(mpu.Status.N)

    }

    @Test
    fun test_bit_abs_copies_bit_7_of_memory_to_n_flag_when_1() {

        mpu.Status.N = true
        // $0000 BIT $FEED
        writeMem(memory, 0x0000, listOf(0x2C, 0xED, 0xFE))
        memory[0xFEED] = 0x00
        mpu.A = 0xFF
        mpu.step()
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_bit_abs_copies_bit_6_of_memory_to_v_flag_when_0() {

        mpu.Status.V = false
        // $0000 BIT $FEED
        writeMem(memory, 0x0000, listOf(0x2C, 0xED, 0xFE))
        memory[0xFEED] = 0xFF
        mpu.A = 0xFF
        mpu.step()
        assertTrue(mpu.Status.V)

    }

    @Test
    fun test_bit_abs_copies_bit_6_of_memory_to_v_flag_when_1() {

        mpu.Status.V = true
        // $0000 BIT $FEED
        writeMem(memory, 0x0000, listOf(0x2C, 0xED, 0xFE))
        memory[0xFEED] = 0x00
        mpu.A = 0xFF
        mpu.step()
        assertFalse(mpu.Status.V)

    }

    @Test
    fun test_bit_abs_stores_result_of_and_in_z_preserves_a_when_1() {

        mpu.Status.Z = false
        // $0000 BIT $FEED
        writeMem(memory, 0x0000, listOf(0x2C, 0xED, 0xFE))
        memory[0xFEED] = 0x00
        mpu.A = 0x01
        mpu.step()
        assertTrue(mpu.Status.Z)
        assertEquals(0x01, mpu.A)
        assertEquals(0x00, memory[0xFEED])

    }

    @Test
    fun test_bit_abs_stores_result_of_and_when_nonzero_in_z_preserves_a() {

        mpu.Status.Z = true
        // $0000 BIT $FEED
        writeMem(memory, 0x0000, listOf(0x2C, 0xED, 0xFE))
        memory[0xFEED] = 0x01
        mpu.A = 0x01
        mpu.step()
        assertFalse(mpu.Status.Z)  // result of AND is non-zero
        assertEquals(0x01, mpu.A)
        assertEquals(0x01, memory[0xFEED])

    }

    @Test
    fun test_bit_abs_stores_result_of_and_when_zero_in_z_preserves_a() {

        mpu.Status.Z = false
        // $0000 BIT $FEED
        writeMem(memory, 0x0000, listOf(0x2C, 0xED, 0xFE))
        memory[0xFEED] = 0x00
        mpu.A = 0x01
        mpu.step()
        assertTrue(mpu.Status.Z)  // result of AND is zero
        assertEquals(0x01, mpu.A)
        assertEquals(0x00, memory[0xFEED])

        // BIT (Zero Page)

    }

    @Test
    fun test_bit_zp_copies_bit_7_of_memory_to_n_flag_when_0() {

        mpu.Status.N = false
        // $0000 BIT $0010
        writeMem(memory, 0x0000, listOf(0x24, 0x10))
        memory[0x0010] = 0xFF
        mpu.A = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(3+Cpu6502.resetCycles, mpu.totalCycles)
        assertTrue(mpu.Status.N)

    }

    @Test
    fun test_bit_zp_copies_bit_7_of_memory_to_n_flag_when_1() {

        mpu.Status.N = true
        // $0000 BIT $0010
        writeMem(memory, 0x0000, listOf(0x24, 0x10))
        memory[0x0010] = 0x00
        mpu.A = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(3+Cpu6502.resetCycles, mpu.totalCycles)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_bit_zp_copies_bit_6_of_memory_to_v_flag_when_0() {

        mpu.Status.V = false
        // $0000 BIT $0010
        writeMem(memory, 0x0000, listOf(0x24, 0x10))
        memory[0x0010] = 0xFF
        mpu.A = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(3+Cpu6502.resetCycles, mpu.totalCycles)
        assertTrue(mpu.Status.V)

    }

    @Test
    fun test_bit_zp_copies_bit_6_of_memory_to_v_flag_when_1() {

        mpu.Status.V = true
        // $0000 BIT $0010
        writeMem(memory, 0x0000, listOf(0x24, 0x10))
        memory[0x0010] = 0x00
        mpu.A = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(3+Cpu6502.resetCycles, mpu.totalCycles)
        assertFalse(mpu.Status.V)

    }

    @Test
    fun test_bit_zp_stores_result_of_and_in_z_preserves_a_when_1() {

        mpu.Status.Z = false
        // $0000 BIT $0010
        writeMem(memory, 0x0000, listOf(0x24, 0x10))
        memory[0x0010] = 0x00
        mpu.A = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(3+Cpu6502.resetCycles, mpu.totalCycles)
        assertTrue(mpu.Status.Z)
        assertEquals(0x01, mpu.A)
        assertEquals(0x00, memory[0x0010])

    }

    @Test
    fun test_bit_zp_stores_result_of_and_when_nonzero_in_z_preserves_a() {

        mpu.Status.Z = true
        // $0000 BIT $0010
        writeMem(memory, 0x0000, listOf(0x24, 0x10))
        memory[0x0010] = 0x01
        mpu.A = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(3+Cpu6502.resetCycles, mpu.totalCycles)
        assertFalse(mpu.Status.Z)  // result of AND is non-zero
        assertEquals(0x01, mpu.A)
        assertEquals(0x01, memory[0x0010])

    }

    @Test
    fun test_bit_zp_stores_result_of_and_when_zero_in_z_preserves_a() {

        mpu.Status.Z = false
        // $0000 BIT $0010
        writeMem(memory, 0x0000, listOf(0x24, 0x10))
        memory[0x0010] = 0x00
        mpu.A = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(3+Cpu6502.resetCycles, mpu.totalCycles)
        assertTrue(mpu.Status.Z)  // result of AND is zero
        assertEquals(0x01, mpu.A)
        assertEquals(0x00, memory[0x0010])

        // BMI

    }

    @Test
    fun test_bmi_negative_set_branches_relative_forward() {

        mpu.Status.N = true
        // $0000 BMI +06
        writeMem(memory, 0x0000, listOf(0x30, 0x06))
        mpu.step()
        assertEquals(0x0002 + 0x06, mpu.PC)

    }

    @Test
    fun test_bmi_negative_set_branches_relative_backward() {

        mpu.Status.N = true
        mpu.PC = 0x0050
        // $0000 BMI -6
        val rel = 256 + (-6)  // two's complement of 6
        writeMem(memory, 0x0050, listOf(0x30, rel.toShort()))
        mpu.step()
        assertEquals(0x0052 - 6, mpu.PC)

    }

    @Test
    fun test_bmi_negative_clear_does_not_branch() {

        mpu.Status.N = false
        // $0000 BEQ +6
        writeMem(memory, 0x0000, listOf(0x30, 0x06))
        mpu.step()
        assertEquals(0x0002, mpu.PC)

        // BNE

    }

    @Test
    fun test_bne_zero_clear_branches_relative_forward() {

        mpu.Status.Z = false
        // $0000 BNE +6
        writeMem(memory, 0x0000, listOf(0xD0, 0x06))
        mpu.step()
        assertEquals(0x0002 + 0x06, mpu.PC)

    }

    @Test
    fun test_bne_zero_clear_branches_relative_backward() {

        mpu.Status.Z = false
        mpu.PC = 0x0050
        // $0050 BNE -6
        val rel = 256 + (-6)  // two's complement of 6
        writeMem(memory, 0x0050, listOf(0xD0, rel.toShort()))
        mpu.step()
        assertEquals(0x0052 - 6, mpu.PC)

    }

    @Test
    fun test_bne_zero_set_does_not_branch() {

        mpu.Status.Z = true
        // $0000 BNE +6
        writeMem(memory, 0x0000, listOf(0xD0, 0x06))
        mpu.step()
        assertEquals(0x0002, mpu.PC)

        // BPL

    }

    @Test
    fun test_bpl_negative_clear_branches_relative_forward() {

        mpu.Status.N = false
        // $0000 BPL +06
        writeMem(memory, 0x0000, listOf(0x10, 0x06))
        mpu.step()
        assertEquals(0x0002 + 0x06, mpu.PC)

    }

    @Test
    fun test_bpl_negative_clear_branches_relative_backward() {

        mpu.Status.N = false
        mpu.PC = 0x0050
        // $0050 BPL -6
        val rel = 256 + (-6)  // two's complement of 6
        writeMem(memory, 0x0050, listOf(0x10, rel.toShort()))
        mpu.step()
        assertEquals(0x0052 - 6, mpu.PC)

    }

    @Test
    fun test_bpl_negative_set_does_not_branch() {

        mpu.Status.N = true
        // $0000 BPL +6
        writeMem(memory, 0x0000, listOf(0x10, 0x06))
        mpu.step()
        assertEquals(0x0002, mpu.PC)

        // BRK

    }

    @Test
    fun test_brk_pushes_pc_plus_2_and_status_then_sets_pc_to_irq_vector() {

        writeMem(memory, 0xFFFE, listOf(0xCD, 0xAB))
        mpu.SP = 0xff
        mpu.Status.I = false

        // $C000 BRK
        memory[0xC000] = 0x00
        mpu.PC = 0xC000
        mpu.step()
        assertEquals(0xABCD, mpu.PC)

        assertEquals(0xFC, mpu.SP)
        assertEquals(0xC0, memory[0x1FF])  // PCH
        assertEquals(0x02, memory[0x1FE])  // PCL
        assertEquals(fBREAK or fUNUSED, memory[0x1FD].toInt(), "Status on stack should have no I flag")
        assertEquals(fBREAK or fUNUSED or fINTERRUPT, mpu.Status.asByte().toInt())
    }

    // BVC

    @Test
    fun test_bvc_overflow_clear_branches_relative_forward() {
        mpu.Status.V = false
        // $0000 BVC +6
        writeMem(memory, 0x0000, listOf(0x50, 0x06))
        mpu.step()
        assertEquals(0x0002 + 0x06, mpu.PC)
    }

    @Test
    fun test_bvc_overflow_clear_branches_relative_backward() {
        mpu.Status.V = false
        mpu.PC = 0x0050
        val rel = 256 + (-6)  // two's complement of 6
        // $0050 BVC -6
        writeMem(memory, 0x0050, listOf(0x50, rel.toShort()))
        mpu.step()
        assertEquals(0x0052 - 6, mpu.PC)
    }

    @Test
    fun test_bvc_overflow_set_does_not_branch() {
        mpu.Status.V = true
        // $0000 BVC +6
        writeMem(memory, 0x0000, listOf(0x50, 0x06))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
    }

    // BVS

    @Test
    fun test_bvs_overflow_set_branches_relative_forward() {
        mpu.Status.V = true
        // $0000 BVS +6
        writeMem(memory, 0x0000, listOf(0x70, 0x06))
        mpu.step()
        assertEquals(0x0002 + 0x06, mpu.PC)
    }

    @Test
    fun test_bvs_overflow_set_branches_relative_backward() {

        mpu.Status.V = true
        mpu.PC = 0x0050
        val rel = 256 + (-6)  // two's complement of 6
        // $0050 BVS -6
        writeMem(memory, 0x0050, listOf(0x70, rel.toShort()))
        mpu.step()
        assertEquals(0x0052 - 6, mpu.PC)

    }

    @Test
    fun test_bvs_overflow_clear_does_not_branch() {
        mpu.Status.V = false
        // $0000 BVS +6
        writeMem(memory, 0x0000, listOf(0x70, 0x06))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
    }

    // CLC

    @Test
    fun test_clc_clears_carry_flag() {
        mpu.Status.C = true
        // $0000 CLC
        memory[0x0000] = 0x18
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertFalse(mpu.Status.C)
    }

    // CLD

    @Test
    fun test_cld_clears_decimal_flag() {
        mpu.Status.D = true
        // $0000 CLD
        memory[0x0000] = 0xD8
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertFalse(mpu.Status.D)
    }

    // CLI
    @Test
    fun test_cli_clears_interrupt_mask_flag() {
        mpu.Status.I = true
        // $0000 CLI
        memory[0x0000] = 0x58
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertFalse(mpu.Status.I)
    }

    // CLV
    @Test
    fun test_clv_clears_overflow_flag() {
        mpu.Status.V = true
        // $0000 CLV
        memory[0x0000] = 0xB8
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertFalse(mpu.Status.V)
    }

    // Compare instructions

    // See http://6502.org/tutorials/compare_instructions.html
    // and http://www.6502.org/tutorials/compare_beyond.html
    // Cheat sheet:
    //    - Comparison is actually subtraction "register - memory"
    //    - Z contains equality result (1 equal, 0 not equal)
    //    - C contains result of unsigned comparison (0 if A<m, 1 if A>=m)
    //    - N holds MSB of subtraction result (*NOT* of signed subtraction)
    //    - V is not affected by comparison
    //    - D has no effect on comparison

    // CMP Immediate
    @Test
    fun test_cmp_imm_sets_zero_carry_clears_neg_flags_if_equal() {
        // Comparison: A == m

        // $0000 CMP #10 , A will be 10
        writeMem(memory, 0x0000, listOf(0xC9, 10))
        mpu.A = 10
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.Z)
        assertTrue(mpu.Status.C)
    }

    @Test
    fun test_cmp_imm_clears_zero_carry_takes_neg_if_less_unsigned() {
        // Comparison: A < m (unsigned)

        // $0000 CMP #10 , A will be 1
        writeMem(memory, 0x0000, listOf(0xC9, 10))
        mpu.A = 1
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertTrue(mpu.Status.N) // 0x01-0x0A=0xF7
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_cmp_imm_clears_zero_sets_carry_takes_neg_if_less_signed() {
        // Comparison: A < #nn (signed), A negative

        // $0000 CMP #1, A will be -1 (0xFF)
        writeMem(memory, 0x0000, listOf(0xC9, 1))
        mpu.A = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertTrue(mpu.Status.N) // 0xFF-0x01=0xFE
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.C) // A>m unsigned

    }

    @Test
    fun test_cmp_imm_clears_zero_carry_takes_neg_if_less_signed_nega() {
        // Comparison: A < m (signed), A and m both negative

        // $0000 CMP #0xFF (-1), A will be -2 (0xFE)
        writeMem(memory, 0x0000, listOf(0xC9, 0xFF))
        mpu.A = 0xFE
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertTrue(mpu.Status.N) // 0xFE-0xFF=0xFF
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C) // A<m unsigned

    }

    @Test
    fun test_cmp_imm_clears_zero_sets_carry_takes_neg_if_more_unsigned() {
        // Comparison: A > m (unsigned)

        // $0000 CMP #1 , A will be 10
        writeMem(memory, 0x0000, listOf(0xC9, 1))
        mpu.A = 10
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertFalse(mpu.Status.N) // 0x0A-0x01 = 0x09
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.C) // A>m unsigned
    }

    @Test
    fun test_cmp_imm_clears_zero_carry_takes_neg_if_more_signed() {
        // Comparison: A > m (signed), memory negative

        // $0000 CMP #$FF (-1), A will be 2
        writeMem(memory, 0x0000, listOf(0xC9, 0xFF))
        mpu.A = 2
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertFalse(mpu.Status.N) // 0x02-0xFF=0x01
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C) // A<m unsigned
    }

    @Test
    fun test_cmp_imm_clears_zero_carry_takes_neg_if_more_signed_nega() {
        // Comparison: A > m (signed), A and m both negative

        // $0000 CMP #$FE (-2), A will be -1 (0xFF)
        writeMem(memory, 0x0000, listOf(0xC9, 0xFE))
        mpu.A = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertFalse(mpu.Status.N) // 0xFF-0xFE=0x01
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.C) // A>m unsigned
    }

    // CPX Immediate
    @Test
    fun test_cpx_imm_sets_zero_carry_clears_neg_flags_if_equal() {
        // Comparison: X == m

        // $0000 CPX #$20
        writeMem(memory, 0x0000, listOf(0xE0, 0x20))
        mpu.X = 0x20
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertTrue(mpu.Status.Z)
        assertTrue(mpu.Status.C)
        assertFalse(mpu.Status.N)
    }

    // CPY Immediate
    @Test
    fun test_cpy_imm_sets_zero_carry_clears_neg_flags_if_equal() {
        // Comparison: Y == m

        // $0000 CPY #$30
        writeMem(memory, 0x0000, listOf(0xC0, 0x30))
        mpu.Y = 0x30
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertTrue(mpu.Status.Z)
        assertTrue(mpu.Status.C)
        assertFalse(mpu.Status.N)
    }

    // DEC Absolute
    @Test
    fun test_dec_abs_decrements_memory() {

        // $0000 DEC 0xABCD
        writeMem(memory, 0x0000, listOf(0xCE, 0xCD, 0xAB))
        memory[0xABCD] = 0x10
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x0F, memory[0xABCD])
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
    }

    @Test
    fun test_dec_abs_below_00_rolls_over_and_sets_negative_flag() {

        // $0000 DEC 0xABCD
        writeMem(memory, 0x0000, listOf(0xCE, 0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xFF, memory[0xABCD])
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.N)
    }

    @Test
    fun test_dec_abs_sets_zero_flag_when_decrementing_to_zero() {

        // $0000 DEC 0xABCD
        writeMem(memory, 0x0000, listOf(0xCE, 0xCD, 0xAB))
        memory[0xABCD] = 0x01
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)
    }

    // DEC Zero Page
    @Test
    fun test_dec_zp_decrements_memory() {

        // $0000 DEC 0x0010
        writeMem(memory, 0x0000, listOf(0xC6, 0x10))
        memory[0x0010] = 0x10
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x0F, memory[0x0010])
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_dec_zp_below_00_rolls_over_and_sets_negative_flag() {

        // $0000 DEC 0x0010
        writeMem(memory, 0x0000, listOf(0xC6, 0x10))
        memory[0x0010] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, memory[0x0010])
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.N)
    }

    @Test
    fun test_dec_zp_sets_zero_flag_when_decrementing_to_zero() {

        // $0000 DEC 0x0010
        writeMem(memory, 0x0000, listOf(0xC6, 0x10))
        memory[0x0010] = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)
    }

    // DEC Absolute, X-Indexed
    @Test
    fun test_dec_abs_x_decrements_memory() {

        // $0000 DEC 0xABCD,X
        writeMem(memory, 0x0000, listOf(0xDE, 0xCD, 0xAB))
        mpu.X = 0x03
        memory[0xABCD + mpu.X] = 0x10
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x0F, memory[0xABCD + mpu.X])
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
    }

    @Test
    fun test_dec_abs_x_below_00_rolls_over_and_sets_negative_flag() {

        // $0000 DEC 0xABCD,X
        writeMem(memory, 0x0000, listOf(0xDE, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xFF, memory[0xABCD + mpu.X])
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.N)
    }

    @Test
    fun test_dec_abs_x_sets_zero_flag_when_decrementing_to_zero() {

        // $0000 DEC 0xABCD,X
        writeMem(memory, 0x0000, listOf(0xDE, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x01
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD + mpu.X])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)
    }

    // DEC Zero Page, X-Indexed
    @Test
    fun test_dec_zp_x_decrements_memory() {

        // $0000 DEC 0x0010,X
        writeMem(memory, 0x0000, listOf(0xD6, 0x10))
        mpu.X = 0x03
        memory[0x0010 + mpu.X] = 0x10
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x0F, memory[0x0010 + mpu.X])
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
    }

    @Test
    fun test_dec_zp_x_below_00_rolls_over_and_sets_negative_flag() {

        // $0000 DEC 0x0010,X
        writeMem(memory, 0x0000, listOf(0xD6, 0x10))
        mpu.X = 0x03
        memory[0x0010 + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, memory[0x0010 + mpu.X])
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.N)
    }

    @Test
    fun test_dec_zp_x_sets_zero_flag_when_decrementing_to_zero() {

        // $0000 DEC 0x0010,X
        writeMem(memory, 0x0000, listOf(0xD6, 0x10))
        mpu.X = 0x03
        memory[0x0010 + mpu.X] = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010 + mpu.X])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)
    }

    // DEX
    @Test
    fun test_dex_decrements_x() {
        mpu.X = 0x10
        // $0000 DEX
        memory[0x0000] = 0xCA
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x0F, mpu.X)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
    }

    @Test
    fun test_dex_below_00_rolls_over_and_sets_negative_flag() {
        mpu.X = 0x00
        // $0000 DEX
        memory[0x0000] = 0xCA
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xFF, mpu.X)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)
    }

    @Test
    fun test_dex_sets_zero_flag_when_decrementing_to_zero() {
        mpu.X = 0x01
        // $0000 DEX
        memory[0x0000] = 0xCA
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x00, mpu.X)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)
    }

    // DEY
    @Test
    fun test_dey_decrements_y() {
        mpu.Y = 0x10
        // $0000 DEY
        memory[0x0000] = 0x88
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x0F, mpu.Y)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
    }

    @Test
    fun test_dey_below_00_rolls_over_and_sets_negative_flag() {
        mpu.Y = 0x00
        // $0000 DEY
        memory[0x0000] = 0x88
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xFF, mpu.Y)
        assertTrue(mpu.Status.N)
    }

    @Test
    fun test_dey_sets_zero_flag_when_decrementing_to_zero() {

        mpu.Y = 0x01
        // $0000 DEY
        memory[0x0000] = 0x88
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x00, mpu.Y)
        assertTrue(mpu.Status.Z)
    }

    // EOR Absolute
    @Test
    fun test_eor_absolute_flips_bits_over_setting_z_flag() {

        mpu.A = 0xFF
        writeMem(memory, 0x0000, listOf(0x4D, 0xCD, 0xAB))
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertEquals(0xFF, memory[0xABCD])
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_eor_absolute_flips_bits_over_setting_n_flag() {

        mpu.A = 0x00
        writeMem(memory, 0x0000, listOf(0x4D, 0xCD, 0xAB))
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xFF, mpu.A)
        assertEquals(0xFF, memory[0xABCD])
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // EOR Zero Page

    }

    @Test
    fun test_eor_zp_flips_bits_over_setting_z_flag() {

        mpu.A = 0xFF
        writeMem(memory, 0x0000, listOf(0x45, 0x10))
        memory[0x0010] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertEquals(0xFF, memory[0x0010])
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_eor_zp_flips_bits_over_setting_n_flag() {

        mpu.A = 0x00
        writeMem(memory, 0x0000, listOf(0x45, 0x10))
        memory[0x0010] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, mpu.A)
        assertEquals(0xFF, memory[0x0010])
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // EOR Immediate

    }

    @Test
    fun test_eor_immediate_flips_bits_over_setting_z_flag() {

        mpu.A = 0xFF
        writeMem(memory, 0x0000, listOf(0x49, 0xFF))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_eor_immediate_flips_bits_over_setting_n_flag() {

        mpu.A = 0x00
        writeMem(memory, 0x0000, listOf(0x49, 0xFF))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // EOR Absolute, X-Indexed

    }

    @Test
    fun test_eor_abs_x_indexed_flips_bits_over_setting_z_flag() {

        mpu.A = 0xFF
        mpu.X = 0x03
        writeMem(memory, 0x0000, listOf(0x5D, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertEquals(0xFF, memory[0xABCD + mpu.X])
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_eor_abs_x_indexed_flips_bits_over_setting_n_flag() {

        mpu.A = 0x00
        mpu.X = 0x03
        writeMem(memory, 0x0000, listOf(0x5D, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xFF, mpu.A)
        assertEquals(0xFF, memory[0xABCD + mpu.X])
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // EOR Absolute, Y-Indexed

    }

    @Test
    fun test_eor_abs_y_indexed_flips_bits_over_setting_z_flag() {

        mpu.A = 0xFF
        mpu.Y = 0x03
        writeMem(memory, 0x0000, listOf(0x59, 0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertEquals(0xFF, memory[0xABCD + mpu.Y])
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_eor_abs_y_indexed_flips_bits_over_setting_n_flag() {

        mpu.A = 0x00
        mpu.Y = 0x03
        writeMem(memory, 0x0000, listOf(0x59, 0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xFF, mpu.A)
        assertEquals(0xFF, memory[0xABCD + mpu.Y])
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // EOR Indirect, Indexed (X)

    }

    @Test
    fun test_eor_ind_indexed_x_flips_bits_over_setting_z_flag() {

        mpu.A = 0xFF
        mpu.X = 0x03
        writeMem(memory, 0x0000, listOf(0x41, 0x10))  // => EOR listOf($0010,X)
        writeMem(memory, 0x0013, listOf(0xCD, 0xAB))  // => Vector to $ABCD
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertEquals(0xFF, memory[0xABCD])
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_eor_ind_indexed_x_flips_bits_over_setting_n_flag() {

        mpu.A = 0x00
        mpu.X = 0x03
        writeMem(memory, 0x0000, listOf(0x41, 0x10))  // => EOR listOf($0010,X)
        writeMem(memory, 0x0013, listOf(0xCD, 0xAB))  // => Vector to $ABCD
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, mpu.A)
        assertEquals(0xFF, memory[0xABCD])
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // EOR Indexed, Indirect (Y)

    }

    @Test
    fun test_eor_indexed_ind_y_flips_bits_over_setting_z_flag() {

        mpu.A = 0xFF
        mpu.Y = 0x03
        writeMem(memory, 0x0000, listOf(0x51, 0x10))  // => EOR listOf($0010),Y
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))  // => Vector to $ABCD
        memory[0xABCD + mpu.Y] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertEquals(0xFF, memory[0xABCD + mpu.Y])
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_eor_indexed_ind_y_flips_bits_over_setting_n_flag() {

        mpu.A = 0x00
        mpu.Y = 0x03
        writeMem(memory, 0x0000, listOf(0x51, 0x10))  // => EOR listOf($0010),Y
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))  // => Vector to $ABCD
        memory[0xABCD + mpu.Y] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, mpu.A)
        assertEquals(0xFF, memory[0xABCD + mpu.Y])
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // EOR Zero Page, X-Indexed

    }

    @Test
    fun test_eor_zp_x_indexed_flips_bits_over_setting_z_flag() {

        mpu.A = 0xFF
        mpu.X = 0x03
        writeMem(memory, 0x0000, listOf(0x55, 0x10))
        memory[0x0010 + mpu.X] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertEquals(0xFF, memory[0x0010 + mpu.X])
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_eor_zp_x_indexed_flips_bits_over_setting_n_flag() {

        mpu.A = 0x00
        mpu.X = 0x03
        writeMem(memory, 0x0000, listOf(0x55, 0x10))
        memory[0x0010 + mpu.X] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, mpu.A)
        assertEquals(0xFF, memory[0x0010 + mpu.X])
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // INC Absolute

    }

    @Test
    fun test_inc_abs_increments_memory() {

        writeMem(memory, 0x0000, listOf(0xEE, 0xCD, 0xAB))
        memory[0xABCD] = 0x09
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x0A, memory[0xABCD])
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_inc_abs_increments_memory_rolls_over_and_sets_zero_flag() {

        writeMem(memory, 0x0000, listOf(0xEE, 0xCD, 0xAB))
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_inc_abs_sets_negative_flag_when_incrementing_above_7F() {

        writeMem(memory, 0x0000, listOf(0xEE, 0xCD, 0xAB))
        memory[0xABCD] = 0x7F
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, memory[0xABCD])
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.N)

        // INC Zero Page

    }

    @Test
    fun test_inc_zp_increments_memory() {

        writeMem(memory, 0x0000, listOf(0xE6, 0x10))
        memory[0x0010] = 0x09
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x0A, memory[0x0010])
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_inc_zp_increments_memory_rolls_over_and_sets_zero_flag() {

        writeMem(memory, 0x0000, listOf(0xE6, 0x10))
        memory[0x0010] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_inc_zp_sets_negative_flag_when_incrementing_above_7F() {

        writeMem(memory, 0x0000, listOf(0xE6, 0x10))
        memory[0x0010] = 0x7F
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, memory[0x0010])
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.N)

        // INC Absolute, X-Indexed

    }

    @Test
    fun test_inc_abs_x_increments_memory() {

        writeMem(memory, 0x0000, listOf(0xFE, 0xCD, 0xAB))
        mpu.X = 0x03
        memory[0xABCD + mpu.X] = 0x09
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x0A, memory[0xABCD + mpu.X])
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_inc_abs_x_increments_memory_rolls_over_and_sets_zero_flag() {

        writeMem(memory, 0x0000, listOf(0xFE, 0xCD, 0xAB))
        mpu.X = 0x03
        memory[0xABCD + mpu.X] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD + mpu.X])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_inc_abs_x_sets_negative_flag_when_incrementing_above_7F() {

        writeMem(memory, 0x0000, listOf(0xFE, 0xCD, 0xAB))
        mpu.X = 0x03
        memory[0xABCD + mpu.X] = 0x7F
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, memory[0xABCD + mpu.X])
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.N)

        // INC Zero Page, X-Indexed

    }

    @Test
    fun test_inc_zp_x_increments_memory() {

        writeMem(memory, 0x0000, listOf(0xF6, 0x10))
        mpu.X = 0x03
        memory[0x0010 + mpu.X] = 0x09
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x0A, memory[0x0010 + mpu.X])
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_inc_zp_x_increments_memory_rolls_over_and_sets_zero_flag() {

        writeMem(memory, 0x0000, listOf(0xF6, 0x10))
        memory[0x0010 + mpu.X] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010 + mpu.X])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_inc_zp_x_sets_negative_flag_when_incrementing_above_7F() {

        writeMem(memory, 0x0000, listOf(0xF6, 0x10))
        memory[0x0010 + mpu.X] = 0x7F
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, memory[0x0010 + mpu.X])
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.N)

        // INX

    }

    @Test
    fun test_inx_increments_x() {

        mpu.X = 0x09
        memory[0x0000] = 0xE8  // => INX
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x0A, mpu.X)
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_inx_above_FF_rolls_over_and_sets_zero_flag() {

        mpu.X = 0xFF
        memory[0x0000] = 0xE8  // => INX
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x00, mpu.X)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_inx_sets_negative_flag_when_incrementing_above_7F() {
        mpu.X = 0x7f
        memory[0x0000] = 0xE8  // => INX
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x80, mpu.X)
        assertTrue(mpu.Status.N)
    }

    // INY

    @Test
    fun test_iny_increments_y() {

        mpu.Y = 0x09
        memory[0x0000] = 0xC8  // => INY
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x0A, mpu.Y)
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.N)
    }

    @Test
    fun test_iny_above_FF_rolls_over_and_sets_zero_flag() {

        mpu.Y = 0xFF
        memory[0x0000] = 0xC8  // => INY
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x00, mpu.Y)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_iny_sets_negative_flag_when_incrementing_above_7F() {

        mpu.Y = 0x7f
        memory[0x0000] = 0xC8  // => INY
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x80, mpu.Y)
        assertTrue(mpu.Status.N)

        // JMP Absolute

    }

    @Test
    fun test_jmp_abs_jumps_to_absolute_address() {

        // $0000 JMP $ABCD
        writeMem(memory, 0x0000, listOf(0x4C, 0xCD, 0xAB))
        mpu.step()
        assertEquals(0xABCD, mpu.PC)

        // JMP Indirect

    }

    @Test
    fun test_jmp_ind_jumps_to_indirect_address() {

        // $0000 JMP ($ABCD)
        writeMem(memory, 0x0000, listOf(0x6C, 0x00, 0x02))
        writeMem(memory, 0x0200, listOf(0xCD, 0xAB))
        mpu.step()
        assertEquals(0xABCD, mpu.PC)

        // JSR

    }

    @Test
    fun test_jsr_pushes_pc_plus_2_and_sets_pc() {

        // $C000 JSR $FFD2
        mpu.SP = 0xFF
        writeMem(memory, 0xC000, listOf(0x20, 0xD2, 0xFF))
        mpu.PC = 0xC000
        mpu.step()
        assertEquals(0xFFD2, mpu.PC)
        assertEquals(0xFD, mpu.SP)
        assertEquals(0xC0, memory[0x01FF])  // PCH
        assertEquals(0x02, memory[0x01FE])  // PCL+2
    }

    // LDA Absolute

    @Test
    fun test_lda_absolute_loads_a_sets_n_flag() {

        mpu.A = 0x00
        // $0000 LDA $ABCD
        writeMem(memory, 0x0000, listOf(0xAD, 0xCD, 0xAB))
        memory[0xABCD] = 0x80
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_lda_absolute_loads_a_sets_z_flag() {

        mpu.A = 0xFF
        // $0000 LDA $ABCD
        writeMem(memory, 0x0000, listOf(0xAD, 0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

        // LDA Zero Page

    }

    @Test
    fun test_lda_zp_loads_a_sets_n_flag() {

        mpu.A = 0x00
        // $0000 LDA $0010
        writeMem(memory, 0x0000, listOf(0xA5, 0x10))
        memory[0x0010] = 0x80
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_lda_zp_loads_a_sets_z_flag() {

        mpu.A = 0xFF
        // $0000 LDA $0010
        writeMem(memory, 0x0000, listOf(0xA5, 0x10))
        memory[0x0010] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

        // LDA Immediate

    }

    @Test
    fun test_lda_immediate_loads_a_sets_n_flag() {

        mpu.A = 0x00
        // $0000 LDA #$80
        writeMem(memory, 0x0000, listOf(0xA9, 0x80))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_lda_immediate_loads_a_sets_z_flag() {

        mpu.A = 0xFF
        // $0000 LDA #$00
        writeMem(memory, 0x0000, listOf(0xA9, 0x00))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

        // LDA Absolute, X-Indexed

    }

    @Test
    fun test_lda_abs_x_indexed_loads_a_sets_n_flag() {

        mpu.A = 0x00
        mpu.X = 0x03
        // $0000 LDA $ABCD,X
        writeMem(memory, 0x0000, listOf(0xBD, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x80
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_lda_abs_x_indexed_loads_a_sets_z_flag() {

        mpu.A = 0xFF
        mpu.X = 0x03
        // $0000 LDA $ABCD,X
        writeMem(memory, 0x0000, listOf(0xBD, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_lda_abs_x_indexed_does_not_page_wrap() {

        mpu.A = 0
        mpu.X = 0xFF
        // $0000 LDA $0080,X
        writeMem(memory, 0x0000, listOf(0xBD, 0x80, 0x00))
        memory[0x0080 + mpu.X] = 0x42
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x42, mpu.A)

        // LDA Absolute, Y-Indexed

    }

    @Test
    fun test_lda_abs_y_indexed_loads_a_sets_n_flag() {

        mpu.A = 0x00
        mpu.Y = 0x03
        // $0000 LDA $ABCD,Y
        writeMem(memory, 0x0000, listOf(0xB9, 0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x80
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_lda_abs_y_indexed_loads_a_sets_z_flag() {

        mpu.A = 0xFF
        mpu.Y = 0x03
        // $0000 LDA $ABCD,Y
        writeMem(memory, 0x0000, listOf(0xB9, 0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_lda_abs_y_indexed_does_not_page_wrap() {

        mpu.A = 0
        mpu.Y = 0xFF
        // $0000 LDA $0080,X
        writeMem(memory, 0x0000, listOf(0xB9, 0x80, 0x00))
        memory[0x0080 + mpu.Y] = 0x42
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x42, mpu.A)

        // LDA Indirect, Indexed (X)

    }

    @Test
    fun test_lda_ind_indexed_x_loads_a_sets_n_flag() {

        mpu.A = 0x00
        mpu.X = 0x03
        // $0000 LDA ($0010,X)
        // $0013 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0xA1, 0x10))
        writeMem(memory, 0x0013, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x80
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_lda_ind_indexed_x_loads_a_sets_z_flag() {

        mpu.A = 0x00
        mpu.X = 0x03
        // $0000 LDA ($0010,X)
        // $0013 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0xA1, 0x10))
        writeMem(memory, 0x0013, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

        // LDA Indexed, Indirect (Y)

    }

    @Test
    fun test_lda_indexed_ind_y_loads_a_sets_n_flag() {

        mpu.A = 0x00
        mpu.Y = 0x03
        // $0000 LDA ($0010),Y
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0xB1, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x80
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_lda_indexed_ind_y_loads_a_sets_z_flag() {

        mpu.A = 0x00
        mpu.Y = 0x03
        // $0000 LDA ($0010),Y
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0xB1, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

        // LDA Zero Page, X-Indexed

    }

    @Test
    fun test_lda_zp_x_indexed_loads_a_sets_n_flag() {

        mpu.A = 0x00
        mpu.X = 0x03
        // $0000 LDA $10,X
        writeMem(memory, 0x0000, listOf(0xB5, 0x10))
        memory[0x0010 + mpu.X] = 0x80
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_lda_zp_x_indexed_loads_a_sets_z_flag() {

        mpu.A = 0xFF
        mpu.X = 0x03
        // $0000 LDA $10,X
        writeMem(memory, 0x0000, listOf(0xB5, 0x10))
        memory[0x0010 + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

        // LDX Absolute

    }

    @Test
    fun test_ldx_absolute_loads_x_sets_n_flag() {

        mpu.X = 0x00
        // $0000 LDX $ABCD
        writeMem(memory, 0x0000, listOf(0xAE, 0xCD, 0xAB))
        memory[0xABCD] = 0x80
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, mpu.X)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_ldx_absolute_loads_x_sets_z_flag() {

        mpu.X = 0xFF
        // $0000 LDX $ABCD
        writeMem(memory, 0x0000, listOf(0xAE, 0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.X)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

        // LDX Zero Page

    }

    @Test
    fun test_ldx_zp_loads_x_sets_n_flag() {

        mpu.X = 0x00
        // $0000 LDX $0010
        writeMem(memory, 0x0000, listOf(0xA6, 0x10))
        memory[0x0010] = 0x80
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.X)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_ldx_zp_loads_x_sets_z_flag() {

        mpu.X = 0xFF
        // $0000 LDX $0010
        writeMem(memory, 0x0000, listOf(0xA6, 0x10))
        memory[0x0010] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.X)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

        // LDX Immediate

    }

    @Test
    fun test_ldx_immediate_loads_x_sets_n_flag() {

        mpu.X = 0x00
        // $0000 LDX #$80
        writeMem(memory, 0x0000, listOf(0xA2, 0x80))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.X)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_ldx_immediate_loads_x_sets_z_flag() {

        mpu.X = 0xFF
        // $0000 LDX #$00
        writeMem(memory, 0x0000, listOf(0xA2, 0x00))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.X)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

        // LDX Absolute, Y-Indexed

    }

    @Test
    fun test_ldx_abs_y_indexed_loads_x_sets_n_flag() {

        mpu.X = 0x00
        mpu.Y = 0x03
        // $0000 LDX $ABCD,Y
        writeMem(memory, 0x0000, listOf(0xBE, 0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x80
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, mpu.X)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_ldx_abs_y_indexed_loads_x_sets_z_flag() {

        mpu.X = 0xFF
        mpu.Y = 0x03
        // $0000 LDX $ABCD,Y
        writeMem(memory, 0x0000, listOf(0xBE, 0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.X)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

        // LDX Zero Page, Y-Indexed

    }

    @Test
    fun test_ldx_zp_y_indexed_loads_x_sets_n_flag() {

        mpu.X = 0x00
        mpu.Y = 0x03
        // $0000 LDX $0010,Y
        writeMem(memory, 0x0000, listOf(0xB6, 0x10))
        memory[0x0010 + mpu.Y] = 0x80
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.X)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_ldx_zp_y_indexed_loads_x_sets_z_flag() {

        mpu.X = 0xFF
        mpu.Y = 0x03
        // $0000 LDX $0010,Y
        writeMem(memory, 0x0000, listOf(0xB6, 0x10))
        memory[0x0010 + mpu.Y] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.X)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

        // LDY Absolute

    }

    @Test
    fun test_ldy_absolute_loads_y_sets_n_flag() {

        mpu.Y = 0x00
        // $0000 LDY $ABCD
        writeMem(memory, 0x0000, listOf(0xAC, 0xCD, 0xAB))
        memory[0xABCD] = 0x80
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, mpu.Y)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_ldy_absolute_loads_y_sets_z_flag() {

        mpu.Y = 0xFF
        // $0000 LDY $ABCD
        writeMem(memory, 0x0000, listOf(0xAC, 0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.Y)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

        // LDY Zero Page

    }

    @Test
    fun test_ldy_zp_loads_y_sets_n_flag() {

        mpu.Y = 0x00
        // $0000 LDY $0010
        writeMem(memory, 0x0000, listOf(0xA4, 0x10))
        memory[0x0010] = 0x80
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.Y)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_ldy_zp_loads_y_sets_z_flag() {

        mpu.Y = 0xFF
        // $0000 LDY $0010
        writeMem(memory, 0x0000, listOf(0xA4, 0x10))
        memory[0x0010] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.Y)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

        // LDY Immediate

    }

    @Test
    fun test_ldy_immediate_loads_y_sets_n_flag() {

        mpu.Y = 0x00
        // $0000 LDY #$80
        writeMem(memory, 0x0000, listOf(0xA0, 0x80))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.Y)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_ldy_immediate_loads_y_sets_z_flag() {

        mpu.Y = 0xFF
        // $0000 LDY #$00
        writeMem(memory, 0x0000, listOf(0xA0, 0x00))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.Y)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

        // LDY Absolute, X-Indexed

    }

    @Test
    fun test_ldy_abs_x_indexed_loads_x_sets_n_flag() {

        mpu.Y = 0x00
        mpu.X = 0x03
        // $0000 LDY $ABCD,X
        writeMem(memory, 0x0000, listOf(0xBC, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x80
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, mpu.Y)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_ldy_abs_x_indexed_loads_x_sets_z_flag() {

        mpu.Y = 0xFF
        mpu.X = 0x03
        // $0000 LDY $ABCD,X
        writeMem(memory, 0x0000, listOf(0xBC, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.Y)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

        // LDY Zero Page, X-Indexed

    }

    @Test
    fun test_ldy_zp_x_indexed_loads_x_sets_n_flag() {

        mpu.Y = 0x00
        mpu.X = 0x03
        // $0000 LDY $0010,X
        writeMem(memory, 0x0000, listOf(0xB4, 0x10))
        memory[0x0010 + mpu.X] = 0x80
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.Y)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_ldy_zp_x_indexed_loads_x_sets_z_flag() {

        mpu.Y = 0xFF
        mpu.X = 0x03
        // $0000 LDY $0010,X
        writeMem(memory, 0x0000, listOf(0xB4, 0x10))
        memory[0x0010 + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.Y)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

        // LSR Accumulator

    }

    @Test
    fun test_lsr_accumulator_rotates_in_zero_not_carry() {

        mpu.Status.C = true
        // $0000 LSR A
        memory[0x0000] = (0x4A)
        mpu.A = 0x00
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_lsr_accumulator_sets_carry_and_zero_flags_after_rotation() {

        mpu.Status.C = false
        // $0000 LSR A
        memory[0x0000] = (0x4A)
        mpu.A = 0x01
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertTrue(mpu.Status.C)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_lsr_accumulator_rotates_bits_right() {

        mpu.Status.C = true
        // $0000 LSR A
        memory[0x0000] = (0x4A)
        mpu.A = 0x04
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x02, mpu.A)
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)

        // LSR Absolute

    }

    @Test
    fun test_lsr_absolute_rotates_in_zero_not_carry() {

        mpu.Status.C = true
        // $0000 LSR $ABCD
        writeMem(memory, 0x0000, listOf(0x4E, 0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_lsr_absolute_sets_carry_and_zero_flags_after_rotation() {

        mpu.Status.C = false
        // $0000 LSR $ABCD
        writeMem(memory, 0x0000, listOf(0x4E, 0xCD, 0xAB))
        memory[0xABCD] = 0x01
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD])
        assertTrue(mpu.Status.Z)
        assertTrue(mpu.Status.C)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_lsr_absolute_rotates_bits_right() {

        mpu.Status.C = true
        // $0000 LSR $ABCD
        writeMem(memory, 0x0000, listOf(0x4E, 0xCD, 0xAB))
        memory[0xABCD] = 0x04
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x02, memory[0xABCD])
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)

        // LSR Zero Page

    }

    @Test
    fun test_lsr_zp_rotates_in_zero_not_carry() {

        mpu.Status.C = true
        // $0000 LSR $0010
        writeMem(memory, 0x0000, listOf(0x46, 0x10))
        memory[0x0010] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_lsr_zp_sets_carry_and_zero_flags_after_rotation() {

        mpu.Status.C = false
        // $0000 LSR $0010
        writeMem(memory, 0x0000, listOf(0x46, 0x10))
        memory[0x0010] = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010])
        assertTrue(mpu.Status.Z)
        assertTrue(mpu.Status.C)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_lsr_zp_rotates_bits_right() {

        mpu.Status.C = true
        // $0000 LSR $0010
        writeMem(memory, 0x0000, listOf(0x46, 0x10))
        memory[0x0010] = 0x04
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x02, memory[0x0010])
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)

        // LSR Absolute, X-Indexed

    }

    @Test
    fun test_lsr_abs_x_indexed_rotates_in_zero_not_carry() {

        mpu.Status.C = true
        mpu.X = 0x03
        // $0000 LSR $ABCD,X
        writeMem(memory, 0x0000, listOf(0x5E, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD + mpu.X])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_lsr_abs_x_indexed_sets_c_and_z_flags_after_rotation() {

        mpu.Status.C = false
        mpu.X = 0x03
        // $0000 LSR $ABCD,X
        writeMem(memory, 0x0000, listOf(0x5E, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x01
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD + mpu.X])
        assertTrue(mpu.Status.Z)
        assertTrue(mpu.Status.C)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_lsr_abs_x_indexed_rotates_bits_right() {

        mpu.Status.C = true
        // $0000 LSR $ABCD,X
        writeMem(memory, 0x0000, listOf(0x5E, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x04
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x02, memory[0xABCD + mpu.X])
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)

        // LSR Zero Page, X-Indexed

    }

    @Test
    fun test_lsr_zp_x_indexed_rotates_in_zero_not_carry() {

        mpu.Status.C = true
        mpu.X = 0x03
        // $0000 LSR $0010,X
        writeMem(memory, 0x0000, listOf(0x56, 0x10))
        memory[0x0010 + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010 + mpu.X])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_lsr_zp_x_indexed_sets_carry_and_zero_flags_after_rotation() {

        mpu.Status.C = false
        mpu.X = 0x03
        // $0000 LSR $0010,X
        writeMem(memory, 0x0000, listOf(0x56, 0x10))
        memory[0x0010 + mpu.X] = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010 + mpu.X])
        assertTrue(mpu.Status.Z)
        assertTrue(mpu.Status.C)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_lsr_zp_x_indexed_rotates_bits_right() {

        mpu.Status.C = true
        mpu.X = 0x03
        // $0000 LSR $0010,X
        writeMem(memory, 0x0000, listOf(0x56, 0x10))
        memory[0x0010 + mpu.X] = 0x04
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x02, memory[0x0010 + mpu.X])
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)

        // NOP

    }

    @Test
    fun test_nop_does_nothing() {

        // $0000 NOP
        memory[0x0000] = 0xEA
        mpu.step()
        assertEquals(0x0001, mpu.PC)

        // ORA Absolute

    }

    @Test
    fun test_ora_absolute_zeroes_or_zeros_sets_z_flag() {

        mpu.Status.Z = false
        mpu.A = 0x00
        // $0000 ORA $ABCD
        writeMem(memory, 0x0000, listOf(0x0D, 0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_ora_absolute_turns_bits_on_sets_n_flag() {

        mpu.Status.N = false
        mpu.A = 0x03
        // $0000 ORA $ABCD
        writeMem(memory, 0x0000, listOf(0x0D, 0xCD, 0xAB))
        memory[0xABCD] = 0x82
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x83, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // ORA Zero Page

    }

    @Test
    fun test_ora_zp_zeroes_or_zeros_sets_z_flag() {

        mpu.Status.Z = false
        mpu.A = 0x00
        // $0000 ORA $0010
        writeMem(memory, 0x0000, listOf(0x05, 0x10))
        memory[0x0010] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_ora_zp_turns_bits_on_sets_n_flag() {

        mpu.Status.N = false
        mpu.A = 0x03
        // $0000 ORA $0010
        writeMem(memory, 0x0000, listOf(0x05, 0x10))
        memory[0x0010] = 0x82
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x83, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // ORA Immediate

    }

    @Test
    fun test_ora_immediate_zeroes_or_zeros_sets_z_flag() {

        mpu.Status.Z = false
        mpu.A = 0x00
        // $0000 ORA #$00
        writeMem(memory, 0x0000, listOf(0x09, 0x00))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_ora_immediate_turns_bits_on_sets_n_flag() {

        mpu.Status.N = false
        mpu.A = 0x03
        // $0000 ORA #$82
        writeMem(memory, 0x0000, listOf(0x09, 0x82))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x83, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // ORA Absolute, X

    }

    @Test
    fun test_ora_abs_x_indexed_zeroes_or_zeros_sets_z_flag() {

        mpu.Status.Z = false
        mpu.A = 0x00
        mpu.X = 0x03
        // $0000 ORA $ABCD,X
        writeMem(memory, 0x0000, listOf(0x1D, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_ora_abs_x_indexed_turns_bits_on_sets_n_flag() {

        mpu.Status.N = false
        mpu.A = 0x03
        mpu.X = 0x03
        // $0000 ORA $ABCD,X
        writeMem(memory, 0x0000, listOf(0x1D, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x82
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x83, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // ORA Absolute, Y

    }

    @Test
    fun test_ora_abs_y_indexed_zeroes_or_zeros_sets_z_flag() {

        mpu.Status.Z = false
        mpu.A = 0x00
        mpu.Y = 0x03
        // $0000 ORA $ABCD,Y
        writeMem(memory, 0x0000, listOf(0x19, 0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_ora_abs_y_indexed_turns_bits_on_sets_n_flag() {

        mpu.Status.N = false
        mpu.A = 0x03
        mpu.Y = 0x03
        // $0000 ORA $ABCD,Y
        writeMem(memory, 0x0000, listOf(0x19, 0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x82
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x83, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // ORA Indirect, Indexed (X)

    }

    @Test
    fun test_ora_ind_indexed_x_zeroes_or_zeros_sets_z_flag() {

        mpu.Status.Z = false
        mpu.A = 0x00
        mpu.X = 0x03
        // $0000 ORA ($0010,X)
        // $0013 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x01, 0x10))
        writeMem(memory, 0x0013, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_ora_ind_indexed_x_turns_bits_on_sets_n_flag() {

        mpu.Status.N = false
        mpu.A = 0x03
        mpu.X = 0x03
        // $0000 ORA ($0010,X)
        // $0013 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x01, 0x10))
        writeMem(memory, 0x0013, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x82
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x83, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // ORA Indexed, Indirect (Y)

    }

    @Test
    fun test_ora_indexed_ind_y_zeroes_or_zeros_sets_z_flag() {

        mpu.Status.Z = false
        mpu.A = 0x00
        mpu.Y = 0x03
        // $0000 ORA ($0010),Y
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x11, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_ora_indexed_ind_y_turns_bits_on_sets_n_flag() {

        mpu.Status.N = false
        mpu.A = 0x03
        mpu.Y = 0x03
        // $0000 ORA ($0010),Y
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x11, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x82
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x83, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // ORA Zero Page, X

    }

    @Test
    fun test_ora_zp_x_indexed_zeroes_or_zeros_sets_z_flag() {

        mpu.Status.Z = false
        mpu.A = 0x00
        mpu.X = 0x03
        // $0000 ORA $0010,X
        writeMem(memory, 0x0000, listOf(0x15, 0x10))
        memory[0x0010 + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_ora_zp_x_indexed_turns_bits_on_sets_n_flag() {

        mpu.Status.N = false
        mpu.A = 0x03
        mpu.X = 0x03
        // $0000 ORA $0010,X
        writeMem(memory, 0x0000, listOf(0x15, 0x10))
        memory[0x0010 + mpu.X] = 0x82
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x83, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

        // PHA

    }

    @Test
    fun test_pha_pushes_a_and_updates_sp() {

        mpu.A = 0xAB
        // $0000 PHA
        memory[0x0000] = 0x48
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xAB, mpu.A)
        assertEquals(0xFC, mpu.SP)
        assertEquals(0xAB, memory[0x01FD])
    }

    // PHP
    @Test
    fun test_php_pushes_processor_status_and_updates_sp() {
        for (flags in 0 until 0x100) {
            mpu.reset()
            mpu.Status.fromByte(flags or fBREAK or fUNUSED)
            // $0000 PHP
            memory[0x0000] = 0x08
            mpu.step()
            assertEquals(0x0001, mpu.PC)
            assertEquals(0xFC, mpu.SP)
            assertEquals((flags or fBREAK or fUNUSED), memory[0x1FD].toInt())
        }

    }

    // PLA
    @Test
    fun test_pla_pulls_top_byte_from_stack_into_a_and_updates_sp() {

        // $0000 PLA
        memory[0x0000] = 0x68
        memory[0x01FF] = 0xAB
        mpu.SP = 0xFE
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xAB, mpu.A)
        assertEquals(0xFF, mpu.SP)

        // PLP

    }

    @Test
    fun test_plp_pulls_top_byte_from_stack_into_flags_and_updates_sp() {

        // $0000 PLP
        memory[0x0000] = 0x28
        memory[0x01FF] = 0xBA  // must have BREAK and UNUSED set
        mpu.SP = 0xFE
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xBA, mpu.Status.asByte())
        assertEquals(0xFF, mpu.SP)

        // ROL Accumulator

    }

    @Test
    fun test_rol_accumulator_zero_and_carry_zero_sets_z_flag() {

        mpu.A = 0x00
        mpu.Status.C = false
        // $0000 ROL A
        memory[0x0000] = 0x2A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_rol_accumulator_80_and_carry_zero_sets_z_flag() {

        mpu.A = 0x80
        mpu.Status.C = false
        mpu.Status.Z = false
        // $0000 ROL A
        memory[0x0000] = 0x2A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)
    }

    @Test
    fun test_rol_accumulator_zero_and_carry_one_clears_z_flag() {

        mpu.A = 0x00
        mpu.Status.C = true
        // $0000 ROL A
        memory[0x0000] = 0x2A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x01, mpu.A)
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_rol_accumulator_sets_n_flag() {

        mpu.A = 0x40
        mpu.Status.C = true
        // $0000 ROL A
        memory[0x0000] = 0x2A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x81, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_rol_accumulator_shifts_out_zero() {

        mpu.A = 0x7F
        mpu.Status.C = false
        // $0000 ROL A
        memory[0x0000] = 0x2A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xFE, mpu.A)
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_rol_accumulator_shifts_out_one() {

        mpu.A = 0xFF
        mpu.Status.C = false
        // $0000 ROL A
        memory[0x0000] = 0x2A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xFE, mpu.A)
        assertTrue(mpu.Status.C)

        // ROL Absolute

    }

    @Test
    fun test_rol_absolute_zero_and_carry_zero_sets_z_flag() {

        mpu.Status.C = false
        // $0000 ROL $ABCD
        writeMem(memory, 0x0000, listOf(0x2E, 0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_rol_absolute_80_and_carry_zero_sets_z_flag() {

        mpu.Status.C = false
        mpu.Status.Z = false
        // $0000 ROL $ABCD
        writeMem(memory, 0x0000, listOf(0x2E, 0xCD, 0xAB))
        memory[0xABCD] = 0x80
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_rol_absolute_zero_and_carry_one_clears_z_flag() {

        mpu.A = 0x00
        mpu.Status.C = true
        // $0000 ROL $ABCD
        writeMem(memory, 0x0000, listOf(0x2E, 0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x01, memory[0xABCD])
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_rol_absolute_sets_n_flag() {

        mpu.Status.C = true
        // $0000 ROL $ABCD
        writeMem(memory, 0x0000, listOf(0x2E, 0xCD, 0xAB))
        memory[0xABCD] = 0x40
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x81, memory[0xABCD])
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_rol_absolute_shifts_out_zero() {

        mpu.Status.C = false
        // $0000 ROL $ABCD
        writeMem(memory, 0x0000, listOf(0x2E, 0xCD, 0xAB))
        memory[0xABCD] = 0x7F
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xFE, memory[0xABCD])
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_rol_absolute_shifts_out_one() {

        mpu.Status.C = false
        // $0000 ROL $ABCD
        writeMem(memory, 0x0000, listOf(0x2E, 0xCD, 0xAB))
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xFE, memory[0xABCD])
        assertTrue(mpu.Status.C)

        // ROL Zero Page

    }

    @Test
    fun test_rol_zp_zero_and_carry_zero_sets_z_flag() {

        mpu.Status.C = false
        // $0000 ROL $0010
        writeMem(memory, 0x0000, listOf(0x26, 0x10))
        memory[0x0010] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_rol_zp_80_and_carry_zero_sets_z_flag() {

        mpu.Status.C = false
        mpu.Status.Z = false
        // $0000 ROL $0010
        writeMem(memory, 0x0000, listOf(0x26, 0x10))
        memory[0x0010] = 0x80
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_rol_zp_zero_and_carry_one_clears_z_flag() {

        mpu.A = 0x00
        mpu.Status.C = true
        // $0000 ROL $0010
        writeMem(memory, 0x0000, listOf(0x26, 0x10))
        memory[0x0010] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x01, memory[0x0010])
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_rol_zp_sets_n_flag() {

        mpu.Status.C = true
        // $0000 ROL $0010
        writeMem(memory, 0x0000, listOf(0x26, 0x10))
        memory[0x0010] = 0x40
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x81, memory[0x0010])
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_rol_zp_shifts_out_zero() {

        mpu.Status.C = false
        // $0000 ROL $0010
        writeMem(memory, 0x0000, listOf(0x26, 0x10))
        memory[0x0010] = 0x7F
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFE, memory[0x0010])
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_rol_zp_shifts_out_one() {

        mpu.Status.C = false
        // $0000 ROL $0010
        writeMem(memory, 0x0000, listOf(0x26, 0x10))
        memory[0x0010] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFE, memory[0x0010])
        assertTrue(mpu.Status.C)

        // ROL Absolute, X-Indexed

    }

    @Test
    fun test_rol_abs_x_indexed_zero_and_carry_zero_sets_z_flag() {

        mpu.Status.C = false
        mpu.X = 0x03
        // $0000 ROL $ABCD,X
        writeMem(memory, 0x0000, listOf(0x3E, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD + mpu.X])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_rol_abs_x_indexed_80_and_carry_zero_sets_z_flag() {

        mpu.Status.C = false
        mpu.Status.Z = false
        mpu.X = 0x03
        // $0000 ROL $ABCD,X
        writeMem(memory, 0x0000, listOf(0x3E, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x80
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD + mpu.X])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_rol_abs_x_indexed_zero_and_carry_one_clears_z_flag() {

        mpu.X = 0x03
        mpu.Status.C = true
        // $0000 ROL $ABCD,X
        writeMem(memory, 0x0000, listOf(0x3E, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x01, memory[0xABCD + mpu.X])
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_rol_abs_x_indexed_sets_n_flag() {

        mpu.X = 0x03
        mpu.Status.C = true
        // $0000 ROL $ABCD,X
        writeMem(memory, 0x0000, listOf(0x3E, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x40
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x81, memory[0xABCD + mpu.X])
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_rol_abs_x_indexed_shifts_out_zero() {

        mpu.X = 0x03
        mpu.Status.C = false
        // $0000 ROL $ABCD,X
        writeMem(memory, 0x0000, listOf(0x3E, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x7F
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xFE, memory[0xABCD + mpu.X])
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_rol_abs_x_indexed_shifts_out_one() {

        mpu.X = 0x03
        mpu.Status.C = false
        // $0000 ROL $ABCD,X
        writeMem(memory, 0x0000, listOf(0x3E, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xFE, memory[0xABCD + mpu.X])
        assertTrue(mpu.Status.C)

        // ROL Zero Page, X-Indexed

    }

    @Test
    fun test_rol_zp_x_indexed_zero_and_carry_zero_sets_z_flag() {

        mpu.Status.C = false
        mpu.X = 0x03
        writeMem(memory, 0x0000, listOf(0x36, 0x10))
        // $0000 ROL $0010,X
        memory[0x0010 + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010 + mpu.X])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_rol_zp_x_indexed_80_and_carry_zero_sets_z_flag() {

        mpu.Status.C = false
        mpu.Status.Z = false
        mpu.X = 0x03
        writeMem(memory, 0x0000, listOf(0x36, 0x10))
        // $0000 ROL $0010,X
        memory[0x0010 + mpu.X] = 0x80
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010 + mpu.X])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_rol_zp_x_indexed_zero_and_carry_one_clears_z_flag() {

        mpu.X = 0x03
        mpu.Status.C = true
        writeMem(memory, 0x0000, listOf(0x36, 0x10))
        // $0000 ROL $0010,X
        memory[0x0010 + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x01, memory[0x0010 + mpu.X])
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_rol_zp_x_indexed_sets_n_flag() {

        mpu.X = 0x03
        mpu.Status.C = true
        // $0000 ROL $0010,X
        writeMem(memory, 0x0000, listOf(0x36, 0x10))
        memory[0x0010 + mpu.X] = 0x40
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x81, memory[0x0010 + mpu.X])
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)

    }

    @Test
    fun test_rol_zp_x_indexed_shifts_out_zero() {

        mpu.X = 0x03
        mpu.Status.C = false
        // $0000 ROL $0010,X
        writeMem(memory, 0x0000, listOf(0x36, 0x10))
        memory[0x0010 + mpu.X] = 0x7F
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFE, memory[0x0010 + mpu.X])
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_rol_zp_x_indexed_shifts_out_one() {

        mpu.X = 0x03
        mpu.Status.C = false
        // $0000 ROL $0010,X
        writeMem(memory, 0x0000, listOf(0x36, 0x10))
        memory[0x0010 + mpu.X] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFE, memory[0x0010 + mpu.X])
        assertTrue(mpu.Status.C)

        // ROR Accumulator

    }

    @Test
    fun test_ror_accumulator_zero_and_carry_zero_sets_z_flag() {

        mpu.A = 0x00
        mpu.Status.C = false
        // $0000 ROR A
        memory[0x0000] = 0x6A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_ror_accumulator_zero_and_carry_one_rotates_in_sets_n_flags() {

        mpu.A = 0x00
        mpu.Status.C = true
        // $0000 ROR A
        memory[0x0000] = 0x6A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.N)

    }

    @Test
    fun test_ror_accumulator_shifts_out_zero() {

        mpu.A = 0x02
        mpu.Status.C = true
        // $0000 ROR A
        memory[0x0000] = 0x6A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x81, mpu.A)
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_ror_accumulator_shifts_out_one() {

        mpu.A = 0x03
        mpu.Status.C = true
        // $0000 ROR A
        memory[0x0000] = 0x6A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x81, mpu.A)
        assertTrue(mpu.Status.C)

        // ROR Absolute

    }

    @Test
    fun test_ror_absolute_zero_and_carry_zero_sets_z_flag() {

        mpu.Status.C = false
        // $0000 ROR $ABCD
        writeMem(memory, 0x0000, listOf(0x6E, 0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_ror_absolute_zero_and_carry_one_rotates_in_sets_n_flags() {

        mpu.Status.C = true
        // $0000 ROR $ABCD
        writeMem(memory, 0x0000, listOf(0x6E, 0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, memory[0xABCD])
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.N)

    }

    @Test
    fun test_ror_absolute_shifts_out_zero() {

        mpu.Status.C = true
        // $0000 ROR $ABCD
        writeMem(memory, 0x0000, listOf(0x6E, 0xCD, 0xAB))
        memory[0xABCD] = 0x02
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x81, memory[0xABCD])
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_ror_absolute_shifts_out_one() {

        mpu.Status.C = true
        // $0000 ROR $ABCD
        writeMem(memory, 0x0000, listOf(0x6E, 0xCD, 0xAB))
        memory[0xABCD] = 0x03
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x81, memory[0xABCD])
        assertTrue(mpu.Status.C)

        // ROR Zero Page

    }

    @Test
    fun test_ror_zp_zero_and_carry_zero_sets_z_flag() {

        mpu.Status.C = false
        // $0000 ROR $0010
        writeMem(memory, 0x0000, listOf(0x66, 0x10))
        memory[0x0010] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_ror_zp_zero_and_carry_one_rotates_in_sets_n_flags() {

        mpu.Status.C = true
        // $0000 ROR $0010
        writeMem(memory, 0x0000, listOf(0x66, 0x10))
        memory[0x0010] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, memory[0x0010])
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.N)

    }

    @Test
    fun test_ror_zp_zero_absolute_shifts_out_zero() {

        mpu.Status.C = true
        // $0000 ROR $0010
        writeMem(memory, 0x0000, listOf(0x66, 0x10))
        memory[0x0010] = 0x02
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x81, memory[0x0010])
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_ror_zp_shifts_out_one() {

        mpu.Status.C = true
        // $0000 ROR $0010
        writeMem(memory, 0x0000, listOf(0x66, 0x10))
        memory[0x0010] = 0x03
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x81, memory[0x0010])
        assertTrue(mpu.Status.C)

        // ROR Absolute, X-Indexed

    }

    @Test
    fun test_ror_abs_x_indexed_zero_and_carry_zero_sets_z_flag() {

        mpu.X = 0x03
        mpu.Status.C = false
        // $0000 ROR $ABCD,X
        writeMem(memory, 0x0000, listOf(0x7E, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD + mpu.X])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_ror_abs_x_indexed_z_and_c_1_rotates_in_sets_n_flags() {

        mpu.X = 0x03
        mpu.Status.C = true
        // $0000 ROR $ABCD,X
        writeMem(memory, 0x0000, listOf(0x7E, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x80, memory[0xABCD + mpu.X])
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.N)

    }

    @Test
    fun test_ror_abs_x_indexed_shifts_out_zero() {

        mpu.X = 0x03
        mpu.Status.C = true
        // $0000 ROR $ABCD,X
        writeMem(memory, 0x0000, listOf(0x7E, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x02
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x81, memory[0xABCD + mpu.X])
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_ror_abs_x_indexed_shifts_out_one() {

        mpu.X = 0x03
        mpu.Status.C = true
        // $0000 ROR $ABCD,X
        writeMem(memory, 0x0000, listOf(0x7E, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x03
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x81, memory[0xABCD + mpu.X])
        assertTrue(mpu.Status.C)

        // ROR Zero Page, X-Indexed

    }

    @Test
    fun test_ror_zp_x_indexed_zero_and_carry_zero_sets_z_flag() {

        mpu.X = 0x03
        mpu.Status.C = false
        // $0000 ROR $0010,X
        writeMem(memory, 0x0000, listOf(0x76, 0x10))
        memory[0x0010 + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010 + mpu.X])
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)

    }

    @Test
    fun test_ror_zp_x_indexed_zero_and_carry_one_rotates_in_sets_n_flags() {

        mpu.X = 0x03
        mpu.Status.C = true
        // $0000 ROR $0010,X
        writeMem(memory, 0x0000, listOf(0x76, 0x10))
        memory[0x0010 + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, memory[0x0010 + mpu.X])
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.N)

    }

    @Test
    fun test_ror_zp_x_indexed_zero_absolute_shifts_out_zero() {

        mpu.X = 0x03
        mpu.Status.C = true
        // $0000 ROR $0010,X
        writeMem(memory, 0x0000, listOf(0x76, 0x10))
        memory[0x0010 + mpu.X] = 0x02
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x81, memory[0x0010 + mpu.X])
        assertFalse(mpu.Status.C)

    }

    @Test
    fun test_ror_zp_x_indexed_shifts_out_one() {

        mpu.X = 0x03
        mpu.Status.C = true
        // $0000 ROR $0010,X
        writeMem(memory, 0x0000, listOf(0x76, 0x10))
        memory[0x0010 + mpu.X] = 0x03
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x81, memory[0x0010 + mpu.X])
        assertTrue(mpu.Status.C)

        // RTI

    }

    @Test
    fun test_rti_restores_status_and_pc_and_updates_sp() {

        // $0000 RTI
        memory[0x0000] = 0x40
        writeMem(memory, 0x01FD, listOf(0xFC, 0x03, 0xC0))  // Status, PCL, PCH
        mpu.SP = 0xFC

        mpu.step()
        assertEquals(0xFF, mpu.SP)
        assertEquals(0xFC, mpu.Status.asByte())
        assertEquals(0xC003, mpu.PC)
    }

    @Test
    fun test_rti_forces_break_and_unused_flags_high() {

        // $0000 RTI
        memory[0x0000] = 0x40
        writeMem(memory, 0x01FD, listOf(0x00, 0x03, 0xC0))  // Status, PCL, PCH
        mpu.SP = 0xFC

        mpu.step()
        assertTrue(mpu.Status.B)
        // assertEquals(fUNUSED, mpu.Status.asByte().toInt() and fUNUSED)
    }

    // RTS
    @Test
    fun test_rts_restores_pc_and_increments_then_updates_sp() {

        // $0000 RTS
        memory[0x0000] = 0x60
        writeMem(memory, 0x01FE, listOf(0x03, 0xC0))  // PCL, PCH
        mpu.PC = 0x0000
        mpu.SP = 0xFD

        mpu.step()
        assertEquals(0xC004, mpu.PC)
        assertEquals(0xFF, mpu.SP)

    }

    @Test
    fun test_rts_wraps_around_top_of_memory() {

        // $1000 RTS
        memory[0x1000] = 0x60
        writeMem(memory, 0x01FE, listOf(0xFF, 0xFF))  // PCL, PCH
        mpu.PC = 0x1000
        mpu.SP = 0xFD

        mpu.step()
        assertEquals(0x0000, mpu.PC)
        assertEquals(0xFF, mpu.SP)
    }

    // SBC Absolute
    @Test
    fun test_sbc_abs_all_zeros_and_no_borrow_is_zero() {

        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x00
        // $0000 SBC $ABCD
        writeMem(memory, 0x0000, listOf(0xED, 0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_abs_downto_zero_no_borrow_sets_z_clears_n() {
        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x01
        // $0000 SBC $ABCD
        writeMem(memory, 0x0000, listOf(0xED, 0xCD, 0xAB))
        memory[0xABCD] = 0x01
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_abs_downto_zero_with_borrow_sets_z_clears_n() {
        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x01
        // $0000 SBC $ABCD
        writeMem(memory, 0x0000, listOf(0xED, 0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_abs_downto_four_with_borrow_clears_z_n() {
        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x07
        // $0000 SBC $ABCD
        writeMem(memory, 0x0000, listOf(0xED, 0xCD, 0xAB))
        memory[0xABCD] = 0x02
        mpu.step()
        assertEquals(0x04, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.C)
    }

    // SBC Zero Page
    @Test
    fun test_sbc_zp_all_zeros_and_no_borrow_is_zero() {
        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x00
        // $0000 SBC $10
        writeMem(memory, 0x0000, listOf(0xE5, 0x10))
        memory[0x0010] = 0x00
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_zp_downto_zero_no_borrow_sets_z_clears_n() {
        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x01
        // $0000 SBC $10
        writeMem(memory, 0x0000, listOf(0xE5, 0x10))
        memory[0x0010] = 0x01
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_zp_downto_zero_with_borrow_sets_z_clears_n() {
        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x01
        // => SBC $10
        writeMem(memory, 0x0000, listOf(0xE5, 0x10))
        memory[0x0010] = 0x00
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_zp_downto_four_with_borrow_clears_z_n() {
        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x07
        // => SBC $10
        writeMem(memory, 0x0000, listOf(0xE5, 0x10))
        memory[0x0010] = 0x02
        mpu.step()
        assertEquals(0x04, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.C)
    }

    // SBC Immediate

    @Test
    fun test_sbc_imm_all_zeros_and_no_borrow_is_zero() {
        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x00
        // $0000 SBC #$00
        writeMem(memory, 0x0000, listOf(0xE9, 0x00))
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_imm_downto_zero_no_borrow_sets_z_clears_n() {
        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x01
        // $0000 SBC #$01
        writeMem(memory, 0x0000, listOf(0xE9, 0x01))
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_imm_downto_zero_with_borrow_sets_z_clears_n() {
        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x01
        // $0000 SBC #$00
        writeMem(memory, 0x0000, listOf(0xE9, 0x00))
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_imm_downto_four_with_borrow_clears_z_n() {
        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x07
        // $0000 SBC #$02
        writeMem(memory, 0x0000, listOf(0xE9, 0x02))
        mpu.step()
        assertEquals(0x04, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.C)
    }

    @Test
    fun test_sbc_bcd_on_immediate_0a_minus_00_carry_set() {
        mpu.Status.D = true
        mpu.Status.C = true
        mpu.A = 0x0a
        // $0000 SBC #$00
        writeMem(memory, 0x0000, listOf(0xe9, 0x00))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x0a, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.V)
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.C)
    }

    @Test
    fun test_sbc_bcd_on_immediate_9a_minus_00_carry_set() {
        mpu.Status.D = true
        mpu.Status.C = true
        mpu.A = 0x9a
        // $0000 SBC #$00
        writeMem(memory, 0x0000, listOf(0xe9, 0x00))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x9a, mpu.A)
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.C)
        assertFalse(mpu.Status.V)
        assertTrue(mpu.Status.N)
    }

    @Test
    fun test_sbc_bcd_on_immediate_00_minus_01_carry_set() {
        mpu.Status.D = true
        mpu.Status.V = true
        mpu.Status.Z = true
        mpu.Status.C = true
        mpu.Status.N = false
        mpu.A = 0x00
        // => $0000 SBC #$00
        writeMem(memory, 0x0000, listOf(0xe9, 0x01))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x99, mpu.A)
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.V)
        assertTrue(mpu.Status.N)
    }

    @Test
    fun test_sbc_bcd_on_immediate_20_minus_0a_carry_unset() {
        mpu.Status.D = true
        mpu.Status.C = false
        mpu.A = 0x20
        // $0000 SBC #$0a
        writeMem(memory, 0x0000, listOf(0xe9, 0x0a))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x1f, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.V)
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.C)
    }

    // SBC Absolute, X-Indexed

    @Test
    fun test_sbc_abs_x_all_zeros_and_no_borrow_is_zero() {
        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x00
        // $0000 SBC $FEE0,X
        writeMem(memory, 0x0000, listOf(0xFD, 0xE0, 0xFE))
        mpu.X = 0x0D
        memory[0xFEED] = 0x00
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_abs_x_downto_zero_no_borrow_sets_z_clears_n() {
        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x01
        // $0000 SBC $FEE0,X
        writeMem(memory, 0x0000, listOf(0xFD, 0xE0, 0xFE))
        mpu.X = 0x0D
        memory[0xFEED] = 0x01
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_abs_x_downto_zero_with_borrow_sets_z_clears_n() {
        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x01
        // $0000 SBC $FEE0,X
        writeMem(memory, 0x0000, listOf(0xFD, 0xE0, 0xFE))
        mpu.X = 0x0D
        memory[0xFEED] = 0x00
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_abs_x_downto_four_with_borrow_clears_z_n() {
        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x07
        // $0000 SBC $FEE0,X
        writeMem(memory, 0x0000, listOf(0xFD, 0xE0, 0xFE))
        mpu.X = 0x0D
        memory[0xFEED] = 0x02
        mpu.step()
        assertEquals(0x04, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.C)
    }

    // SBC Absolute, Y-Indexed

    @Test
    fun test_sbc_abs_y_all_zeros_and_no_borrow_is_zero() {
        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x00
        // $0000 SBC $FEE0,Y
        writeMem(memory, 0x0000, listOf(0xF9, 0xE0, 0xFE))
        mpu.Y = 0x0D
        memory[0xFEED] = 0x00
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_abs_y_downto_zero_no_borrow_sets_z_clears_n() {
        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x01
        // $0000 SBC $FEE0,Y
        writeMem(memory, 0x0000, listOf(0xF9, 0xE0, 0xFE))
        mpu.Y = 0x0D
        memory[0xFEED] = 0x01
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_abs_y_downto_zero_with_borrow_sets_z_clears_n() {
        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x01
        // $0000 SBC $FEE0,Y
        writeMem(memory, 0x0000, listOf(0xF9, 0xE0, 0xFE))
        mpu.Y = 0x0D
        memory[0xFEED] = 0x00
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_abs_y_downto_four_with_borrow_clears_z_n() {
        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x07
        // $0000 SBC $FEE0,Y
        writeMem(memory, 0x0000, listOf(0xF9, 0xE0, 0xFE))
        mpu.Y = 0x0D
        memory[0xFEED] = 0x02
        mpu.step()
        assertEquals(0x04, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.C)
    }

    // SBC Indirect, Indexed (X)

    @Test
    fun test_sbc_ind_x_all_zeros_and_no_borrow_is_zero() {
        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x00
        // $0000 SBC ($10,X)
        // $0013 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0xE1, 0x10))
        writeMem(memory, 0x0013, listOf(0xED, 0xFE))
        mpu.X = 0x03
        memory[0xFEED] = 0x00
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_ind_x_downto_zero_no_borrow_sets_z_clears_n() {
        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x01
        // $0000 SBC ($10,X)
        // $0013 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0xE1, 0x10))
        writeMem(memory, 0x0013, listOf(0xED, 0xFE))
        mpu.X = 0x03
        memory[0xFEED] = 0x01
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_ind_x_downto_zero_with_borrow_sets_z_clears_n() {
        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x01
        // $0000 SBC ($10,X)
        // $0013 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0xE1, 0x10))
        writeMem(memory, 0x0013, listOf(0xED, 0xFE))
        mpu.X = 0x03
        memory[0xFEED] = 0x00
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_ind_x_downto_four_with_borrow_clears_z_n() {
        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x07
        // $0000 SBC ($10,X)
        // $0013 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0xE1, 0x10))
        writeMem(memory, 0x0013, listOf(0xED, 0xFE))
        mpu.X = 0x03
        memory[0xFEED] = 0x02
        mpu.step()
        assertEquals(0x04, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.C)
    }

    // SBC Indexed, Indirect (Y)

    @Test
    fun test_sbc_ind_y_all_zeros_and_no_borrow_is_zero() {
        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x00
        mpu.Y = 0x03
        // $0000 SBC ($10),Y
        // $0010 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0xF1, 0x10))
        writeMem(memory, 0x0010, listOf(0xED, 0xFE))
        memory[0xFEED + mpu.Y] = 0x00
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_ind_y_downto_zero_no_borrow_sets_z_clears_n() {

        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x01
        // $0000 SBC ($10),Y
        // $0010 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0xF1, 0x10))
        writeMem(memory, 0x0010, listOf(0xED, 0xFE))
        memory[0xFEED + mpu.Y] = 0x01
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_sbc_ind_y_downto_zero_with_borrow_sets_z_clears_n() {

        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x01
        // $0000 SBC ($10),Y
        // $0010 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0xF1, 0x10))
        writeMem(memory, 0x0010, listOf(0xED, 0xFE))
        memory[0xFEED + mpu.Y] = 0x00
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_sbc_ind_y_downto_four_with_borrow_clears_z_n() {

        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x07
        // $0000 SBC ($10),Y
        // $0010 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0xF1, 0x10))
        writeMem(memory, 0x0010, listOf(0xED, 0xFE))
        memory[0xFEED + mpu.Y] = 0x02
        mpu.step()
        assertEquals(0x04, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.C)

        // SBC Zero Page, X-Indexed

    }

    @Test
    fun test_sbc_zp_x_all_zeros_and_no_borrow_is_zero() {

        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x00
        // $0000 SBC $10,X
        writeMem(memory, 0x0000, listOf(0xF5, 0x10))
        mpu.X = 0x0D
        memory[0x001D] = 0x00
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_sbc_zp_x_downto_zero_no_borrow_sets_z_clears_n() {

        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x01
        // $0000 SBC $10,X
        writeMem(memory, 0x0000, listOf(0xF5, 0x10))
        mpu.X = 0x0D
        memory[0x001D] = 0x01
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_sbc_zp_x_downto_zero_with_borrow_sets_z_clears_n() {

        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x01
        // $0000 SBC $10,X
        writeMem(memory, 0x0000, listOf(0xF5, 0x10))
        mpu.X = 0x0D
        memory[0x001D] = 0x00
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)

    }

    @Test
    fun test_sbc_zp_x_downto_four_with_borrow_clears_z_n() {

        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x07
        // $0000 SBC $10,X
        writeMem(memory, 0x0000, listOf(0xF5, 0x10))
        mpu.X = 0x0D
        memory[0x001D] = 0x02
        mpu.step()
        assertEquals(0x04, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.C)

        // SEC

    }

    @Test
    fun test_sec_sets_carry_flag() {

        mpu.Status.C = false
        // $0000 SEC
        memory[0x0000] = 0x038
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertTrue(mpu.Status.C)

        // SED

    }

    @Test
    fun test_sed_sets_decimal_mode_flag() {

        mpu.Status.D = false
        // $0000 SED
        memory[0x0000] = 0xF8
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertTrue(mpu.Status.D)
    }

    // SEI

    @Test
    fun test_sei_sets_interrupt_disable_flag() {
        mpu.Status.I = false
        // $0000 SEI
        memory[0x0000] = 0x78
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertTrue(mpu.Status.I)
    }

    // STA Absolute

    @Test
    fun test_sta_absolute_stores_a_leaves_a_and_n_flag_unchanged() {

        val flags = 0xFF and fNEGATIVE.inv()
        mpu.Status.fromByte(flags)
        mpu.A = 0xFF
        // $0000 STA $ABCD
        writeMem(memory, 0x0000, listOf(0x8D, 0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xFF, memory[0xABCD])
        assertEquals(0xFF, mpu.A)
        assertEquals(flags, mpu.Status.asByte().toInt())

    }

    @Test
    fun test_sta_absolute_stores_a_leaves_a_and_z_flag_unchanged() {

        val flags = 0xFF and fZERO.inv()
        mpu.Status.fromByte(flags)
        mpu.A = 0x00
        // $0000 STA $ABCD
        writeMem(memory, 0x0000, listOf(0x8D, 0xCD, 0xAB))
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD])
        assertEquals(0x00, mpu.A)
        assertEquals(flags, mpu.Status.asByte().toInt())

        // STA Zero Page

    }

    @Test
    fun test_sta_zp_stores_a_leaves_a_and_n_flag_unchanged() {

        val flags = 0xFF and fNEGATIVE.inv()
        mpu.Status.fromByte(flags)
        mpu.A = 0xFF
        // $0000 STA $0010
        writeMem(memory, 0x0000, listOf(0x85, 0x10))
        memory[0x0010] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, memory[0x0010])
        assertEquals(0xFF, mpu.A)
        assertEquals(flags, mpu.Status.asByte().toInt())

    }

    @Test
    fun test_sta_zp_stores_a_leaves_a_and_z_flag_unchanged() {

        val flags = 0xFF and fZERO.inv()
        mpu.Status.fromByte(flags)
        mpu.A = 0x00
        // $0000 STA $0010
        writeMem(memory, 0x0000, listOf(0x85, 0x10))
        memory[0x0010] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010])
        assertEquals(0x00, mpu.A)
        assertEquals(flags, mpu.Status.asByte().toInt())

        // STA Absolute, X-Indexed

    }

    @Test
    fun test_sta_abs_x_indexed_stores_a_leaves_a_and_n_flag_unchanged() {

        val flags = 0xFF and fNEGATIVE.inv()
        mpu.Status.fromByte(flags)
        mpu.A = 0xFF
        mpu.X = 0x03
        // $0000 STA $ABCD,X
        writeMem(memory, 0x0000, listOf(0x9D, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xFF, memory[0xABCD + mpu.X])
        assertEquals(0xFF, mpu.A)
        assertEquals(flags, mpu.Status.asByte().toInt())

    }

    @Test
    fun test_sta_abs_x_indexed_stores_a_leaves_a_and_z_flag_unchanged() {

        val flags = 0xFF and fZERO.inv()
        mpu.Status.fromByte(flags)
        mpu.A = 0x00
        mpu.X = 0x03
        // $0000 STA $ABCD,X
        writeMem(memory, 0x0000, listOf(0x9D, 0xCD, 0xAB))
        memory[0xABCD + mpu.X] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD + mpu.X])
        assertEquals(0x00, mpu.A)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    // STA Absolute, Y-Indexed

    @Test
    fun test_sta_abs_y_indexed_stores_a_leaves_a_and_n_flag_unchanged() {

        val flags = 0xFF and fNEGATIVE.inv()
        mpu.Status.fromByte(flags)
        mpu.A = 0xFF
        mpu.Y = 0x03
        // $0000 STA $ABCD,Y
        writeMem(memory, 0x0000, listOf(0x99, 0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xFF, memory[0xABCD + mpu.Y])
        assertEquals(0xFF, mpu.A)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    @Test
    fun test_sta_abs_y_indexed_stores_a_leaves_a_and_z_flag_unchanged() {
        val flags = 0xFF and fZERO.inv()
        mpu.Status.fromByte(flags)
        mpu.A = 0x00
        mpu.Y = 0x03
        // $0000 STA $ABCD,Y
        writeMem(memory, 0x0000, listOf(0x99, 0xCD, 0xAB))
        memory[0xABCD + mpu.Y] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD + mpu.Y])
        assertEquals(0x00, mpu.A)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    // STA Indirect, Indexed (X)

    @Test
    fun test_sta_ind_indexed_x_stores_a_leaves_a_and_n_flag_unchanged() {
        val flags = 0xFF and fNEGATIVE.inv()
        mpu.Status.fromByte(flags)
        mpu.A = 0xFF
        mpu.X = 0x03
        // $0000 STA ($0010,X)
        // $0013 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0x81, 0x10))
        writeMem(memory, 0x0013, listOf(0xED, 0xFE))
        memory[0xFEED] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, memory[0xFEED])
        assertEquals(0xFF, mpu.A)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    @Test
    fun test_sta_ind_indexed_x_stores_a_leaves_a_and_z_flag_unchanged() {
        val flags = 0xFF and fZERO.inv()
        mpu.Status.fromByte(flags)
        mpu.A = 0x00
        mpu.X = 0x03
        // $0000 STA ($0010,X)
        // $0013 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0x81, 0x10))
        writeMem(memory, 0x0013, listOf(0xED, 0xFE))
        memory[0xFEED] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0xFEED])
        assertEquals(0x00, mpu.A)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    // STA Indexed, Indirect (Y)

    @Test
    fun test_sta_indexed_ind_y_stores_a_leaves_a_and_n_flag_unchanged() {
        val flags = 0xFF and fNEGATIVE.inv()
        mpu.Status.fromByte(flags)
        mpu.A = 0xFF
        mpu.Y = 0x03
        // $0000 STA ($0010),Y
        // $0010 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0x91, 0x10))
        writeMem(memory, 0x0010, listOf(0xED, 0xFE))
        memory[0xFEED + mpu.Y] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, memory[0xFEED + mpu.Y])
        assertEquals(0xFF, mpu.A)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    @Test
    fun test_sta_indexed_ind_y_stores_a_leaves_a_and_z_flag_unchanged() {
        val flags = 0xFF and fZERO.inv()
        mpu.Status.fromByte(flags)
        mpu.A = 0x00
        mpu.Y = 0x03
        // $0000 STA ($0010),Y
        // $0010 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0x91, 0x10))
        writeMem(memory, 0x0010, listOf(0xED, 0xFE))
        memory[0xFEED + mpu.Y] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0xFEED + mpu.Y])
        assertEquals(0x00, mpu.A)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    // STA Zero Page, X-Indexed

    @Test
    fun test_sta_zp_x_indexed_stores_a_leaves_a_and_n_flag_unchanged() {
        val flags = 0xFF and fNEGATIVE.inv()
        mpu.Status.fromByte(flags)
        mpu.A = 0xFF
        mpu.X = 0x03
        // $0000 STA $0010,X
        writeMem(memory, 0x0000, listOf(0x95, 0x10))
        memory[0x0010 + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, memory[0x0010 + mpu.X])
        assertEquals(0xFF, mpu.A)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    @Test
    fun test_sta_zp_x_indexed_stores_a_leaves_a_and_z_flag_unchanged() {
        val flags = 0xFF and fZERO.inv()
        mpu.Status.fromByte(flags)
        mpu.A = 0x00
        mpu.X = 0x03
        // $0000 STA $0010,X
        writeMem(memory, 0x0000, listOf(0x95, 0x10))
        memory[0x0010 + mpu.X] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010 + mpu.X])
        assertEquals(0x00, mpu.A)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    // STX Absolute

    @Test
    fun test_stx_absolute_stores_x_leaves_x_and_n_flag_unchanged() {
        val flags = 0xFF and fNEGATIVE.inv()
        mpu.Status.fromByte(flags)
        mpu.X = 0xFF
        // $0000 STX $ABCD
        writeMem(memory, 0x0000, listOf(0x8E, 0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xFF, memory[0xABCD])
        assertEquals(0xFF, mpu.X)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    @Test
    fun test_stx_absolute_stores_x_leaves_x_and_z_flag_unchanged() {
        val flags = 0xFF and fZERO.inv()
        mpu.Status.fromByte(flags)
        mpu.X = 0x00
        // $0000 STX $ABCD
        writeMem(memory, 0x0000, listOf(0x8E, 0xCD, 0xAB))
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD])
        assertEquals(0x00, mpu.X)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    // STX Zero Page

    @Test
    fun test_stx_zp_stores_x_leaves_x_and_n_flag_unchanged() {
        val flags = 0xFF and fNEGATIVE.inv()
        mpu.Status.fromByte(flags)
        mpu.X = 0xFF
        // $0000 STX $0010
        writeMem(memory, 0x0000, listOf(0x86, 0x10))
        memory[0x0010] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, memory[0x0010])
        assertEquals(0xFF, mpu.X)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    @Test
    fun test_stx_zp_stores_x_leaves_x_and_z_flag_unchanged() {
        val flags = 0xFF and fZERO.inv()
        mpu.Status.fromByte(flags)
        mpu.X = 0x00
        // $0000 STX $0010
        writeMem(memory, 0x0000, listOf(0x86, 0x10))
        memory[0x0010] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010])
        assertEquals(0x00, mpu.X)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    // STX Zero Page, Y-Indexed

    @Test
    fun test_stx_zp_y_indexed_stores_x_leaves_x_and_n_flag_unchanged() {
        val flags = 0xFF and fNEGATIVE.inv()
        mpu.Status.fromByte(flags)
        mpu.X = 0xFF
        mpu.Y = 0x03
        // $0000 STX $0010,Y
        writeMem(memory, 0x0000, listOf(0x96, 0x10))
        memory[0x0010 + mpu.Y] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, memory[0x0010 + mpu.Y])
        assertEquals(0xFF, mpu.X)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    @Test
    fun test_stx_zp_y_indexed_stores_x_leaves_x_and_z_flag_unchanged() {
        val flags = 0xFF and fZERO.inv()
        mpu.Status.fromByte(flags)
        mpu.X = 0x00
        mpu.Y = 0x03
        // $0000 STX $0010,Y
        writeMem(memory, 0x0000, listOf(0x96, 0x10))
        memory[0x0010 + mpu.Y] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010 + mpu.Y])
        assertEquals(0x00, mpu.X)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    // STY Absolute

    @Test
    fun test_sty_absolute_stores_y_leaves_y_and_n_flag_unchanged() {
        val flags = 0xFF and fNEGATIVE.inv()
        mpu.Status.fromByte(flags)
        mpu.Y = 0xFF
        // $0000 STY $ABCD
        writeMem(memory, 0x0000, listOf(0x8C, 0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0xFF, memory[0xABCD])
        assertEquals(0xFF, mpu.Y)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    @Test
    fun test_sty_absolute_stores_y_leaves_y_and_z_flag_unchanged() {
        val flags = 0xFF and fZERO.inv()
        mpu.Status.fromByte(flags)
        mpu.Y = 0x00
        // $0000 STY $ABCD
        writeMem(memory, 0x0000, listOf(0x8C, 0xCD, 0xAB))
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0003, mpu.PC)
        assertEquals(0x00, memory[0xABCD])
        assertEquals(0x00, mpu.Y)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    // STY Zero Page

    @Test
    fun test_sty_zp_stores_y_leaves_y_and_n_flag_unchanged() {
        val flags = 0xFF and fNEGATIVE.inv()
        mpu.Status.fromByte(flags)
        mpu.Y = 0xFF
        // $0000 STY $0010
        writeMem(memory, 0x0000, listOf(0x84, 0x10))
        memory[0x0010] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, memory[0x0010])
        assertEquals(0xFF, mpu.Y)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    @Test
    fun test_sty_zp_stores_y_leaves_y_and_z_flag_unchanged() {
        val flags = 0xFF and fZERO.inv()
        mpu.Status.fromByte(flags)
        mpu.Y = 0x00
        // $0000 STY $0010
        writeMem(memory, 0x0000, listOf(0x84, 0x10))
        memory[0x0010] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010])
        assertEquals(0x00, mpu.Y)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    // STY Zero Page, X-Indexed

    @Test
    fun test_sty_zp_x_indexed_stores_y_leaves_y_and_n_flag_unchanged() {
        val flags = 0xFF and fNEGATIVE.inv()
        mpu.Status.fromByte(flags)
        mpu.Y = 0xFF
        mpu.X = 0x03
        // $0000 STY $0010,X
        writeMem(memory, 0x0000, listOf(0x94, 0x10))
        memory[0x0010 + mpu.X] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0xFF, memory[0x0010 + mpu.X])
        assertEquals(0xFF, mpu.Y)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    @Test
    fun test_sty_zp_x_indexed_stores_y_leaves_y_and_z_flag_unchanged() {
        val flags = 0xFF and fZERO.inv()
        mpu.Status.fromByte(flags)
        mpu.Y = 0x00
        mpu.X = 0x03
        // $0000 STY $0010,X
        writeMem(memory, 0x0000, listOf(0x94, 0x10))
        memory[0x0010 + mpu.X] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, memory[0x0010 + mpu.X])
        assertEquals(0x00, mpu.Y)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    // TAX

    @Test
    fun test_tax_transfers_accumulator_into_x() {
        mpu.A = 0xAB
        mpu.X = 0x00
        // $0000 TAX
        memory[0x0000] = 0xAA
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xAB, mpu.A)
        assertEquals(0xAB, mpu.X)
    }

    @Test
    fun test_tax_sets_negative_flag() {
        mpu.Status.N = false
        mpu.A = 0x80
        mpu.X = 0x00
        // $0000 TAX
        memory[0x0000] = 0xAA
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertEquals(0x80, mpu.X)
        assertTrue(mpu.Status.N)
    }

    @Test
    fun test_tax_sets_zero_flag() {
        mpu.Status.Z = false
        mpu.A = 0x00
        mpu.X = 0xFF
        // $0000 TAX
        memory[0x0000] = 0xAA
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertEquals(0x00, mpu.X)
        assertTrue(mpu.Status.Z)
    }

    // TAY

    @Test
    fun test_tay_transfers_accumulator_into_y() {
        mpu.A = 0xAB
        mpu.Y = 0x00
        // $0000 TAY
        memory[0x0000] = 0xA8
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xAB, mpu.A)
        assertEquals(0xAB, mpu.Y)
    }

    @Test
    fun test_tay_sets_negative_flag() {
        mpu.Status.N = false
        mpu.A = 0x80
        mpu.Y = 0x00
        // $0000 TAY
        memory[0x0000] = 0xA8
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertEquals(0x80, mpu.Y)
        assertTrue(mpu.Status.N)
    }

    @Test
    fun test_tay_sets_zero_flag() {
        mpu.Status.Z = false
        mpu.A = 0x00
        mpu.Y = 0xFF
        // $0000 TAY
        memory[0x0000] = 0xA8
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertEquals(0x00, mpu.Y)
        assertTrue(mpu.Status.Z)
    }

    // TSX

    @Test
    fun test_tsx_transfers_stack_pointer_into_x() {
        mpu.SP = 0xAB
        mpu.X = 0x00
        // $0000 TSX
        memory[0x0000] = 0xBA
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xAB, mpu.SP)
        assertEquals(0xAB, mpu.X)
    }

    @Test
    fun test_tsx_sets_negative_flag() {
        mpu.Status.N = false
        mpu.SP = 0x80
        mpu.X = 0x00
        // $0000 TSX
        memory[0x0000] = 0xBA
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x80, mpu.SP)
        assertEquals(0x80, mpu.X)
        assertTrue(mpu.Status.N)
    }

    @Test
    fun test_tsx_sets_zero_flag() {
        mpu.Status.Z = false
        mpu.SP = 0x00
        mpu.Y = 0xFF
        // $0000 TSX
        memory[0x0000] = 0xBA
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x00, mpu.SP)
        assertEquals(0x00, mpu.X)
        assertTrue(mpu.Status.Z)
    }

    // TXA

    @Test
    fun test_txa_transfers_x_into_a() {
        mpu.X = 0xAB
        mpu.A = 0x00
        // $0000 TXA
        memory[0x0000] = 0x8A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xAB, mpu.A)
        assertEquals(0xAB, mpu.X)
    }

    @Test
    fun test_txa_sets_negative_flag() {
        mpu.Status.N = false
        mpu.X = 0x80
        mpu.A = 0x00
        // $0000 TXA
        memory[0x0000] = 0x8A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertEquals(0x80, mpu.X)
        assertTrue(mpu.Status.N)
    }

    @Test
    fun test_txa_sets_zero_flag() {
        mpu.Status.Z = false
        mpu.X = 0x00
        mpu.A = 0xFF
        // $0000 TXA
        memory[0x0000] = 0x8A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertEquals(0x00, mpu.X)
        assertTrue(mpu.Status.Z)
    }

    // TXS

    @Test
    fun test_txs_transfers_x_into_stack_pointer() {
        mpu.X = 0xAB
        // $0000 TXS
        memory[0x0000] = 0x9A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xAB, mpu.SP)
        assertEquals(0xAB, mpu.X)
    }

    @Test
    fun test_txs_does_not_set_negative_flag() {
        mpu.Status.N = false
        mpu.X = 0x80
        // $0000 TXS
        memory[0x0000] = 0x9A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x80, mpu.SP)
        assertEquals(0x80, mpu.X)
        assertFalse(mpu.Status.N)
    }

    @Test
    fun test_txs_does_not_set_zero_flag() {
        mpu.Status.Z = false
        mpu.X = 0x00
        // $0000 TXS
        memory[0x0000] = 0x9A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x00, mpu.SP)
        assertEquals(0x00, mpu.X)
        assertFalse(mpu.Status.Z)
    }

    // TYA

    @Test
    fun test_tya_transfers_y_into_a() {
        mpu.Y = 0xAB
        mpu.A = 0x00
        // $0000 TYA
        memory[0x0000] = 0x98
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xAB, mpu.A)
        assertEquals(0xAB, mpu.Y)
    }

    @Test
    fun test_tya_sets_negative_flag() {
        mpu.Status.N = false
        mpu.Y = 0x80
        mpu.A = 0x00
        // $0000 TYA
        memory[0x0000] = 0x98
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertEquals(0x80, mpu.Y)
        assertTrue(mpu.Status.N)
    }

    @Test
    fun test_tya_sets_zero_flag() {
        mpu.Status.Z = false
        mpu.Y = 0x00
        mpu.A = 0xFF
        // $0000 TYA
        memory[0x0000] = 0x98
        mpu.step()
        assertEquals(0x00, mpu.A)
        assertEquals(0x00, mpu.Y)
        assertTrue(mpu.Status.Z)
        assertEquals(0x0001, mpu.PC)
    }

    @Test
    fun test_brk_interrupt() {
        writeMem(memory, 0xFFFE, listOf(0x00, 0x04))

        writeMem(memory, 0x0000, listOf(0xA9, 0x01,   // LDA #$01
                0x00, 0xEA,   // BRK + skipped byte
                0xEA, 0xEA,   // NOP, NOP
                0xA9, 0x03))  // LDA #$03

        writeMem(memory, 0x0400, listOf(0xA9, 0x02,   // LDA #$02
                0x40))        // RTI

        mpu.step()  // LDA #$01
        assertEquals(0x01, mpu.A)
        assertEquals(0x0002, mpu.PC)
        mpu.step()  // BRK
        assertEquals(0x0400, mpu.PC)
        mpu.step()  // LDA #$02
        assertEquals(0x02, mpu.A)
        assertEquals(0x0402, mpu.PC)
        mpu.step()  // RTI

        assertEquals(0x0004, mpu.PC)
        mpu.step()  // A NOP
        mpu.step()  // The second NOP

        mpu.step()  // LDA #$03
        assertEquals(0x03, mpu.A)
        assertEquals(0x0008, mpu.PC)
    }
}
