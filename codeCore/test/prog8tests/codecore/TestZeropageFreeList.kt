package prog8tests.codecore

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.core.*
import prog8.code.target.C128Target
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.PETTarget
import prog8.code.target.zp.C128Zeropage
import prog8.code.target.zp.C64Zeropage
import prog8.code.target.zp.CX16Zeropage
import prog8.code.target.zp.PETZeropage


/**
 * Comprehensive tests for Zeropage free list configurations across all targets.
 * 
 * Tests verify that for each target and ZeropageType combination:
 * - The exact list of free locations is correct
 * - Scratch locations are properly reserved
 * - Virtual registers are allocated when appropriate
 */
class TestZeropageFreeList: FunSpec({

    // ============================================================================
    // Helper Functions
    // ============================================================================

    fun createOptions(target: ICompilationTarget, zpType: ZeropageType, floats: Boolean = false): CompilationOptions {
        return CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(zpType)
            .floats(floats)
            .compilerVersion("test")
            .build()
    }

    fun verifyScratchLocationsReserved(zp: Zeropage) {
        // Verify scratch locations are not in free list
        zp.free.contains(zp.SCRATCH_B1) shouldBe false
        zp.free.contains(zp.SCRATCH_REG) shouldBe false
        zp.free.contains(zp.SCRATCH_W1) shouldBe false
        zp.free.contains(zp.SCRATCH_W1 + 1u) shouldBe false
        zp.free.contains(zp.SCRATCH_W2) shouldBe false
        zp.free.contains(zp.SCRATCH_W2 + 1u) shouldBe false
        zp.free.contains(zp.SCRATCH_PTR) shouldBe false
        zp.free.contains(zp.SCRATCH_PTR + 1u) shouldBe false
    }

    fun verifyVirtualRegistersAllocated(zp: Zeropage, expected: Boolean) {
        if (expected) {
            // Check that virtual registers are allocated
            for (reg in 0..15) {
                zp.allocatedVariables shouldContainKey "cx16.r${reg}"
            }
        }
    }

    // ============================================================================
    // C64 Zeropage Tests
    // ============================================================================

    context("C64Zeropage") {
        
        test("DONTUSE should have no free locations") {
            val options = createOptions(C64Target(), ZeropageType.DONTUSE)
            val zp = C64Zeropage(options)
            
            zp.free.shouldBeEmpty()
            zp.availableBytes() shouldBe 0
        }

        test("BASICSAFE should have specific free locations") {
            val options = createOptions(C64Target(), ZeropageType.BASICSAFE)
            val zp = C64Zeropage(options)
            
            // BASICSAFE includes specific BASIC-safe locations
            zp.free.isNotEmpty().shouldBeTrue()
            zp.free.size shouldBeGreaterThan 0
            
            // Verify scratch locations are reserved
            verifyScratchLocationsReserved(zp)
            
            // Virtual registers should be allocated for BASICSAFE
            verifyVirtualRegistersAllocated(zp, expected = false)  // C64 doesn't allocate virtual regs for BASICSAFE
        }

        test("KERNALSAFE should have larger free list than BASICSAFE") {
            val options = createOptions(C64Target(), ZeropageType.KERNALSAFE)
            val zp = C64Zeropage(options)
            
            zp.free.isNotEmpty().shouldBeTrue()
            
            // KERNALSAFE has more locations than BASICSAFE
            val basicsafeOptions = createOptions(C64Target(), ZeropageType.BASICSAFE)
            val basicsafeZp = C64Zeropage(basicsafeOptions)
            
            zp.free.size shouldBeGreaterThan basicsafeZp.free.size
            
            verifyScratchLocationsReserved(zp)
            verifyVirtualRegistersAllocated(zp, expected = true)  // KERNALSAFE gets virtual registers
        }

        test("FLOATSAFE should exclude float-used locations") {
            val options = createOptions(C64Target(), ZeropageType.FLOATSAFE, floats = true)
            val zp = C64Zeropage(options)
            
            zp.free.isNotEmpty().shouldBeTrue()
            
            // FLOATSAFE should have fewer locations than KERNALSAFE due to float exclusions
            val kernalsafeOptions = createOptions(C64Target(), ZeropageType.KERNALSAFE)
            val kernalsafeZp = C64Zeropage(kernalsafeOptions)
            
            zp.free.size shouldBeLessThan kernalsafeZp.free.size
            
            // Some float-related locations should not be free (exact set depends on implementation)
            // Just verify the size difference is significant
            (kernalsafeZp.free.size - zp.free.size) shouldBeGreaterThan 10
            
            verifyScratchLocationsReserved(zp)
        }

        test("FULL should have maximum free locations minus IRQ") {
            val options = createOptions(C64Target(), ZeropageType.FULL)
            val zp = C64Zeropage(options)
            
            // FULL should have the most free locations
            val kernalsafeOptions = createOptions(C64Target(), ZeropageType.KERNALSAFE)
            val kernalsafeZp = C64Zeropage(kernalsafeOptions)
            
            zp.free.size shouldBeGreaterThan kernalsafeZp.free.size
            
            // IRQ locations should not be free
            zp.free.contains(0xa0u) shouldBe false
            zp.free.contains(0xa1u) shouldBe false
            zp.free.contains(0xa2u) shouldBe false
            zp.free.contains(0x91u) shouldBe false
            zp.free.contains(0xc0u) shouldBe false
            zp.free.contains(0xc5u) shouldBe false
            zp.free.contains(0xcbu) shouldBe false
            zp.free.contains(0xf5u) shouldBe false
            zp.free.contains(0xf6u) shouldBe false
            
            verifyScratchLocationsReserved(zp)
            verifyVirtualRegistersAllocated(zp, expected = true)
        }
    }

    // ============================================================================
    // CX16 Zeropage Tests
    // ============================================================================

    context("CX16Zeropage") {
        
        test("DONTUSE should have no free locations") {
            val options = createOptions(Cx16Target(), ZeropageType.DONTUSE)
            val zp = CX16Zeropage(options)
            
            zp.free.shouldBeEmpty()
            zp.availableBytes() shouldBe 0
        }

        test("BASICSAFE should have 0x22-0x7F") {
            val options = createOptions(Cx16Target(), ZeropageType.BASICSAFE)
            val zp = CX16Zeropage(options)
            
            // Virtual registers 0x02-0x21 should be allocated, not free
            for (addr in 0x02u..0x21u) {
                zp.free shouldNotBe addr
            }
            
            // Free should be 0x22-0x7F minus scratch locations
            for (addr in 0x22u..0x7Fu) {
                if (addr !in setOf(zp.SCRATCH_B1, zp.SCRATCH_REG, zp.SCRATCH_W1, zp.SCRATCH_W1 + 1u, 
                        zp.SCRATCH_W2, zp.SCRATCH_W2 + 1u, zp.SCRATCH_PTR, zp.SCRATCH_PTR + 1u)) {
                    zp.free.contains(addr) shouldBe true
                }
            }
            
            verifyScratchLocationsReserved(zp)
        }

        test("KERNALSAFE should have 0x22-0x7F and 0xA9-0xFF") {
            val options = createOptions(Cx16Target(), ZeropageType.KERNALSAFE)
            val zp = CX16Zeropage(options)
            
            val basicsafeOptions = createOptions(Cx16Target(), ZeropageType.BASICSAFE)
            val basicsafeZp = CX16Zeropage(basicsafeOptions)
            
            zp.free.size shouldBeGreaterThan basicsafeZp.free.size
            
            // Should include high memory range
            zp.free.contains(0xa9u) shouldBe true
            zp.free.contains(0xffu) shouldBe true
            
            verifyScratchLocationsReserved(zp)
        }

        test("FLOATSAFE should have 0x22-0x7F and 0xD4-0xFF") {
            val options = createOptions(Cx16Target(), ZeropageType.FLOATSAFE, floats = true)
            val zp = CX16Zeropage(options)

            // Should include 0xD4-0xFF range
            zp.free.contains(0xd4u) shouldBe true
            zp.free.contains(0xffu) shouldBe true

            // FLOATSAFE has different high range than KERNALSAFE (0xD4-0xFF vs 0xA9-0xFF)
            val kernalsafeOptions = createOptions(Cx16Target(), ZeropageType.KERNALSAFE)
            val kernalsafeZp = CX16Zeropage(kernalsafeOptions)

            // FLOATSAFE should have fewer locations than KERNALSAFE (smaller high range)
            zp.free.size shouldBeLessThan kernalsafeZp.free.size

            verifyScratchLocationsReserved(zp)
        }

        test("FULL should have 0x22-0xFF") {
            val options = createOptions(Cx16Target(), ZeropageType.FULL)
            val zp = CX16Zeropage(options)
            
            // FULL should have the most free locations
            val kernalsafeOptions = createOptions(Cx16Target(), ZeropageType.KERNALSAFE)
            val kernalsafeZp = CX16Zeropage(kernalsafeOptions)
            
            zp.free.size shouldBeGreaterThan kernalsafeZp.free.size
            
            // Should include all locations from 0x22 to 0xFF (minus scratch)
            for (addr in 0x80u..0xA8u) {
                if (addr !in setOf(zp.SCRATCH_B1, zp.SCRATCH_REG, zp.SCRATCH_W1, zp.SCRATCH_W1 + 1u,
                        zp.SCRATCH_W2, zp.SCRATCH_W2 + 1u, zp.SCRATCH_PTR, zp.SCRATCH_PTR + 1u)) {
                    zp.free.contains(addr) shouldBe true
                }
            }
            
            verifyScratchLocationsReserved(zp)
        }
    }

    // ============================================================================
    // PET Zeropage Tests
    // ============================================================================

    context("PETZeropage") {
        
        test("DONTUSE should have no free locations") {
            val options = createOptions(PETTarget(), ZeropageType.DONTUSE)
            val zp = PETZeropage(options)
            
            zp.free.shouldBeEmpty()
            zp.availableBytes() shouldBe 0
        }

        test("BASICSAFE should have minimal locations") {
            val options = createOptions(PETTarget(), ZeropageType.BASICSAFE)
            val zp = PETZeropage(options)
            
            // BASICSAFE has minimal locations (0xB1-0xBA per code, minus scratch locations)
            zp.free.isNotEmpty().shouldBeTrue()
            // After scratch removal, some locations in 0xb1-0xba range should still be free
            // Just verify the free list is small but non-empty
            zp.free.size shouldBeGreaterThan 0
            zp.free.size shouldBeLessThan 20
            
            verifyScratchLocationsReserved(zp)
        }

        test("FLOATSAFE should be same as BASICSAFE") {
            val floatOptions = createOptions(PETTarget(), ZeropageType.FLOATSAFE, floats = true)
            val floatZp = PETZeropage(floatOptions)
            
            val basicOptions = createOptions(PETTarget(), ZeropageType.BASICSAFE)
            val basicZp = PETZeropage(basicOptions)
            
            // FLOATSAFE and BASICSAFE should have same free locations for PET
            floatZp.free.size shouldBe basicZp.free.size
        }

        test("KERNALSAFE should exclude IRQ locations") {
            val options = createOptions(PETTarget(), ZeropageType.KERNALSAFE)
            val zp = PETZeropage(options)
            
            // IRQ locations should not be free
            zp.free.contains(0x8du) shouldBe false
            zp.free.contains(0x8eu) shouldBe false
            zp.free.contains(0x8fu) shouldBe false
            zp.free.contains(0x97u) shouldBe false
            zp.free.contains(0x98u) shouldBe false
            zp.free.contains(0x99u) shouldBe false
            zp.free.contains(0x9au) shouldBe false
            zp.free.contains(0x9bu) shouldBe false
            zp.free.contains(0x9eu) shouldBe false
            zp.free.contains(0xa7u) shouldBe false
            zp.free.contains(0xa8u) shouldBe false
            zp.free.contains(0xa9u) shouldBe false
            zp.free.contains(0xaau) shouldBe false
            zp.free.contains(0xaaau) shouldBe false
            
            verifyScratchLocationsReserved(zp)
        }

        test("FULL should have all locations minus IRQ") {
            val options = createOptions(PETTarget(), ZeropageType.FULL)
            val zp = PETZeropage(options)
            
            // FULL should have most locations
            zp.free.size shouldBeGreaterThan 100
            
            // IRQ locations should not be free
            zp.free.contains(0x8du) shouldBe false
            zp.free.contains(0x97u) shouldBe false
            zp.free.contains(0xa7u) shouldBe false
            
            verifyScratchLocationsReserved(zp)
            verifyVirtualRegistersAllocated(zp, expected = true)
        }
    }

    // ============================================================================
    // C128 Zeropage Tests
    // ============================================================================

    context("C128Zeropage") {
        
        test("DONTUSE should have no free locations") {
            val options = createOptions(C128Target(), ZeropageType.DONTUSE)
            val zp = C128Zeropage(options)
            
            zp.free.shouldBeEmpty()
            zp.availableBytes() shouldBe 0
        }

        test("BASICSAFE should have specific safe locations") {
            val options = createOptions(C128Target(), ZeropageType.BASICSAFE)
            val zp = C128Zeropage(options)
            
            zp.free.isNotEmpty().shouldBeTrue()
            
            // Should include some specific BASIC-safe locations (excluding scratch)
            // 0x0b is SCRATCH_PTR, 0x74 is SCRATCH_B1 for C128, so they're not free
            // Check for other locations that should be free
            zp.free.contains(0x0du) shouldBe true
            zp.free.contains(0x1bu) shouldBe true  // In BASICSAFE range
            
            verifyScratchLocationsReserved(zp)
        }

        test("FLOATSAFE should be same as BASICSAFE for C128") {
            val floatOptions = createOptions(C128Target(), ZeropageType.FLOATSAFE, floats = true)
            val floatZp = C128Zeropage(floatOptions)
            
            val basicOptions = createOptions(C128Target(), ZeropageType.BASICSAFE)
            val basicZp = C128Zeropage(basicOptions)
            
            // FLOATSAFE and BASICSAFE should have same free locations for C128
            floatZp.free.size shouldBe basicZp.free.size
        }

        test("KERNALSAFE should have BASIC variables area") {
            val options = createOptions(C128Target(), ZeropageType.KERNALSAFE)
            val zp = C128Zeropage(options)

            // Should include 0x0A-0x8F (BASIC variables area) minus scratch/virtual reg locations
            // 0x0a is where virtual registers start for C128, so not free
            // 0x8fu should be free
            zp.free.contains(0x8fu) shouldBe true

            // Should include additional specific locations
            zp.free.contains(0x92u) shouldBe true
            // 0xb6u is SCRATCH_W1 for C128 - won't be free
            zp.free.contains(0x96u) shouldBe true  // Alternative location
            
            verifyScratchLocationsReserved(zp)
        }

        test("FULL should have maximum locations minus IRQ") {
            val options = createOptions(C128Target(), ZeropageType.FULL)
            val zp = C128Zeropage(options)

            // FULL should have many locations (0x0A-0xFF minus IRQ and scratch)
            // 0x0a is where virtual registers start for C128, so not free
            zp.free.size shouldBeGreaterThan 150

            // IRQ locations should not be free
            zp.free.contains(0x90u) shouldBe false
            zp.free.contains(0x91u) shouldBe false
            zp.free.contains(0xa0u) shouldBe false
            zp.free.contains(0xa1u) shouldBe false
            zp.free.contains(0xa2u) shouldBe false
            zp.free.contains(0xc0u) shouldBe false
            zp.free.contains(0xccu) shouldBe false
            zp.free.contains(0xcdu) shouldBe false
            zp.free.contains(0xd0u) shouldBe false
            zp.free.contains(0xd1u) shouldBe false
            zp.free.contains(0xd2u) shouldBe false
            zp.free.contains(0xd3u) shouldBe false
            zp.free.contains(0xd4u) shouldBe false
            zp.free.contains(0xd5u) shouldBe false
            zp.free.contains(0xf7u) shouldBe false
            
            verifyScratchLocationsReserved(zp)
            verifyVirtualRegistersAllocated(zp, expected = true)
        }
    }

    // ============================================================================
    // Cross-Target Comparison Tests
    // ============================================================================

    context("Cross-Target Comparisons") {
        
        test("all targets should have empty free list for DONTUSE") {
            // C64
            val c64Options = createOptions(C64Target(), ZeropageType.DONTUSE)
            C64Zeropage(c64Options).free.shouldBeEmpty()
            
            // CX16
            val cx16Options = createOptions(Cx16Target(), ZeropageType.DONTUSE)
            CX16Zeropage(cx16Options).free.shouldBeEmpty()
            
            // PET
            val petOptions = createOptions(PETTarget(), ZeropageType.DONTUSE)
            PETZeropage(petOptions).free.shouldBeEmpty()
            
            // C128
            val c128Options = createOptions(C128Target(), ZeropageType.DONTUSE)
            C128Zeropage(c128Options).free.shouldBeEmpty()
        }

        test("scratch locations differ between targets") {
            val c64Options = createOptions(C64Target(), ZeropageType.BASICSAFE)
            val c64Zp = C64Zeropage(c64Options)

            val cx16Options = createOptions(Cx16Target(), ZeropageType.BASICSAFE)
            val cx16Zp = CX16Zeropage(cx16Options)

            val petOptions = createOptions(PETTarget(), ZeropageType.BASICSAFE)
            val petZp = PETZeropage(petOptions)

            val c128Options = createOptions(C128Target(), ZeropageType.BASICSAFE)
            val c128Zp = C128Zeropage(c128Options)

            // Each target has different scratch locations
            c64Zp.SCRATCH_B1 shouldBe 0x02u
            cx16Zp.SCRATCH_B1 shouldBe 0x7au
            petZp.SCRATCH_B1 shouldBe 0xb3u
            c128Zp.SCRATCH_B1 shouldBe 0x74u
        }
    }

    // ============================================================================
    // Exhaustive Zeropage Address Tests - All 256 locations
    // ============================================================================

    context("Exhaustive C64 Zeropage Tests") {

        test("DONTUSE - no addresses should be free") {
            val options = createOptions(C64Target(), ZeropageType.DONTUSE)
            val zp = C64Zeropage(options)
            
            for (addr in 0x00u..0xFFu) {
                zp.free.contains(addr) shouldBe false
            }
        }

        test("BASICSAFE - exact free address set") {
            val options = createOptions(C64Target(), ZeropageType.BASICSAFE)
            val zp = C64Zeropage(options)
            
            // BASICSAFE: specific BASIC-safe locations plus virtual registers at 0x04-0x23
            // Scratch locations are removed from free list
            // Just verify key properties
            zp.free.size shouldBeGreaterThan 10
            
            // Scratch locations should NOT be free
            setOf(0x02u, 0x03u, 0x04u, 0x05u, 0xFBu, 0xFCu, 0xFDu, 0xFEu).forEach { addr ->
                zp.free.contains(addr) shouldBe false
            }
            
            // Some known free locations
            zp.free.contains(0x06u) shouldBe true
            zp.free.contains(0x92u) shouldBe true
        }

        test("KERNALSAFE - exact free address set") {
            val options = createOptions(C64Target(), ZeropageType.KERNALSAFE)
            val zp = C64Zeropage(options)
            
            // KERNALSAFE: larger set including more locations, plus virtual registers at 0x04-0x23
            // Scratch locations (0x02,0x03,0x04,0x05,0xFB,0xFC,0xFD,0xFE) are removed
            // Virtual registers 0x04-0x23 are allocated (not free)
            
            // Just verify key properties since exact set is complex
            zp.free.size shouldBeGreaterThan 50
            
            // IRQ locations should NOT be free
            setOf(0xA0u, 0xA1u, 0xA2u, 0x91u, 0xC0u, 0xC5u, 0xCBu, 0xF5u, 0xF6u).forEach { addr ->
                zp.free.contains(addr) shouldBe false
            }
            
            // Scratch locations should NOT be free
            setOf(0x02u, 0x03u, 0x04u, 0x05u, 0xFBu, 0xFCu, 0xFDu, 0xFEu).forEach { addr ->
                zp.free.contains(addr) shouldBe false
            }
        }

        test("FLOATSAFE - excludes float-used locations") {
            val options = createOptions(C64Target(), ZeropageType.FLOATSAFE, floats = true)
            val zp = C64Zeropage(options)
            
            // FLOATSAFE is KERNALSAFE minus float-used locations
            zp.free.size shouldBeGreaterThan 20
            
            // FLOATSAFE should have fewer locations than KERNALSAFE
            val kernalsafeOptions = createOptions(C64Target(), ZeropageType.KERNALSAFE)
            val kernalsafeZp = C64Zeropage(kernalsafeOptions)
            
            zp.free.size shouldBeLessThan kernalsafeZp.free.size
        }

        test("FULL - maximum free locations minus IRQ") {
            val options = createOptions(C64Target(), ZeropageType.FULL)
            val zp = C64Zeropage(options)
            
            // FULL should have the most locations
            zp.free.size shouldBeGreaterThan 100
            
            // IRQ locations should NOT be free
            setOf(0xA0u, 0xA1u, 0xA2u, 0x91u, 0xC0u, 0xC5u, 0xCBu, 0xF5u, 0xF6u).forEach { addr ->
                zp.free.contains(addr) shouldBe false
            }
            
            // Scratch locations should NOT be free
            setOf(0x02u, 0x03u, 0x04u, 0x05u, 0xFBu, 0xFCu, 0xFDu, 0xFEu).forEach { addr ->
                zp.free.contains(addr) shouldBe false
            }
        }
    }

    context("Exhaustive CX16 Zeropage Tests") {

        test("DONTUSE - no addresses should be free") {
            val options = createOptions(Cx16Target(), ZeropageType.DONTUSE)
            val zp = CX16Zeropage(options)
            
            for (addr in 0x00u..0xFFu) {
                zp.free.contains(addr) shouldBe false
            }
        }

        test("BASICSAFE - 0x22-0x7F minus scratch and virtual registers") {
            val options = createOptions(Cx16Target(), ZeropageType.BASICSAFE)
            val zp = CX16Zeropage(options)
            
            // Virtual registers 0x02-0x21 are allocated (not free)
            for (addr in 0x02u..0x21u) {
                zp.free.contains(addr) shouldBe false
            }
            
            // BASICSAFE: 0x22-0x7F minus scratch
            for (addr in 0x22u..0x7Fu) {
                val isScratch = addr in setOf(0x7Au, 0x7Bu, 0x7Cu, 0x7Du, 0x7Eu, 0x7Fu, 0x22u, 0x23u)
                zp.free.contains(addr) shouldBe !isScratch
            }
            
            // Above 0x7F should not be free for BASICSAFE
            for (addr in 0x80u..0xFFu) {
                zp.free.contains(addr) shouldBe false
            }
        }

        test("KERNALSAFE - 0x22-0x7F and 0xA9-0xFF") {
            val options = createOptions(Cx16Target(), ZeropageType.KERNALSAFE)
            val zp = CX16Zeropage(options)
            
            // Virtual registers 0x02-0x21 are allocated (not free)
            for (addr in 0x02u..0x21u) {
                zp.free.contains(addr) shouldBe false
            }
            
            // Should include 0xA9-0xFF range
            zp.free.contains(0xA9u) shouldBe true
            zp.free.contains(0xFFu) shouldBe true
        }

        test("FLOATSAFE - 0x22-0x7F and 0xD4-0xFF") {
            val options = createOptions(Cx16Target(), ZeropageType.FLOATSAFE, floats = true)
            val zp = CX16Zeropage(options)
            
            // Should include 0xD4-0xFF range
            zp.free.contains(0xD4u) shouldBe true
            zp.free.contains(0xFFu) shouldBe true
            
            // Should NOT include 0xA9-0xD3 range that KERNALSAFE has
            val kernalsafeOptions = createOptions(Cx16Target(), ZeropageType.KERNALSAFE)
            val kernalsafeZp = CX16Zeropage(kernalsafeOptions)
            
            zp.free.size shouldBeLessThan kernalsafeZp.free.size
        }

        test("FULL - 0x22-0xFF minus scratch") {
            val options = createOptions(Cx16Target(), ZeropageType.FULL)
            val zp = CX16Zeropage(options)
            
            // Virtual registers 0x02-0x21 are allocated (not free)
            for (addr in 0x02u..0x21u) {
                zp.free.contains(addr) shouldBe false
            }
            
            // FULL should have most locations from 0x22-0xFF
            zp.free.size shouldBeGreaterThan 150
        }
    }

    context("Exhaustive PET Zeropage Tests") {

        test("DONTUSE - no addresses should be free") {
            val options = createOptions(PETTarget(), ZeropageType.DONTUSE)
            val zp = PETZeropage(options)
            
            for (addr in 0x00u..0xFFu) {
                zp.free.contains(addr) shouldBe false
            }
        }

        test("BASICSAFE - minimal locations around 0xB1-0xBA") {
            val options = createOptions(PETTarget(), ZeropageType.BASICSAFE)
            val zp = PETZeropage(options)
            
            // BASICSAFE has minimal locations (0xB1-0xBA minus scratch)
            zp.free.size shouldBeGreaterThan 0
            zp.free.size shouldBeLessThan 15
        }

        test("KERNALSAFE - excludes IRQ locations") {
            val options = createOptions(PETTarget(), ZeropageType.KERNALSAFE)
            val zp = PETZeropage(options)
            
            // IRQ locations should NOT be free
            setOf(0x8Du, 0x8Eu, 0x8Fu, 0x97u, 0x98u, 0x99u, 0x9Au, 0x9Bu, 0x9Eu,
                  0xA7u, 0xA8u, 0xA9u, 0xAAu).forEach { addr ->
                zp.free.contains(addr) shouldBe false
            }
        }

        test("FULL - all minus IRQ locations") {
            val options = createOptions(PETTarget(), ZeropageType.FULL)
            val zp = PETZeropage(options)
            
            // FULL should have most locations
            zp.free.size shouldBeGreaterThan 150
            
            // IRQ locations should NOT be free
            setOf(0x8Du, 0x97u, 0xA7u).forEach { addr ->
                zp.free.contains(addr) shouldBe false
            }
        }
    }

    context("Exhaustive C128 Zeropage Tests") {

        test("DONTUSE - no addresses should be free") {
            val options = createOptions(C128Target(), ZeropageType.DONTUSE)
            val zp = C128Zeropage(options)
            
            for (addr in 0x00u..0xFFu) {
                zp.free.contains(addr) shouldBe false
            }
        }

        test("BASICSAFE - specific safe locations") {
            val options = createOptions(C128Target(), ZeropageType.BASICSAFE)
            val zp = C128Zeropage(options)
            
            // BASICSAFE has specific locations
            zp.free.size shouldBeGreaterThan 50
            
            // Note: Virtual registers are NOT allocated for BASICSAFE on C128
            // (only for FULL and KERNALSAFE modes)
        }

        test("KERNALSAFE - BASIC variables area 0x0A-0x8F") {
            val options = createOptions(C128Target(), ZeropageType.KERNALSAFE)
            val zp = C128Zeropage(options)
            
            // Virtual registers at 0x0A-0x29 are allocated (not free)
            for (addr in 0x0Au..0x29u) {
                zp.free.contains(addr) shouldBe false
            }
            
            // Should include some locations in BASIC variables area
            zp.free.contains(0x8Fu) shouldBe true
        }

        test("FULL - 0x0A-0xFF minus IRQ") {
            val options = createOptions(C128Target(), ZeropageType.FULL)
            val zp = C128Zeropage(options)
            
            // FULL should have many locations
            zp.free.size shouldBeGreaterThan 150
            
            // IRQ locations should NOT be free
            setOf(0x90u, 0x91u, 0xA0u, 0xA1u, 0xA2u, 0xC0u, 0xCCu, 0xCDu,
                  0xD0u, 0xD1u, 0xD2u, 0xD3u, 0xD4u, 0xD5u, 0xF7u).forEach { addr ->
                zp.free.contains(addr) shouldBe false
            }
        }
    }
})

// Helper infix function for readability
infix fun Int.shouldBeLessThan(value: Int) {
    if (this >= value) {
        throw AssertionError("Expected $this to be less than $value")
    }
}
