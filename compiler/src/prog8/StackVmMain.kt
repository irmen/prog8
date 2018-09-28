package prog8

import prog8.stackvm.*
import java.awt.EventQueue
import javax.swing.Timer
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    println("\nProg8 StackVM by Irmen de Jong (irmen@razorvine.net)")
    // @todo software license string
    // println("This software is licensed under the GNU GPL 3.0, see https://www.gnu.org/licenses/gpl.html\n")
    println("**** This is a prerelease version. Please do not distribute! ****\n")

    if(args.size != 1) {
        System.err.println("requires one argument: name of stackvm sourcecode file")
        exitProcess(1)
    }

    val program = Program.load(args.first())
    val vm = StackVm(traceOutputFile = null)
    val dialog = ScreenDialog()
    vm.load(program, dialog.canvas)
    EventQueue.invokeLater {
        dialog.pack()
        dialog.isVisible = true
        dialog.start()

        val programTimer = Timer(10) { a ->
            try {
                vm.step()
            } catch(bp: VmBreakpointException) {
                println("Breakpoint: execution halted. Press enter to resume.")
                readLine()
            } catch (tx: VmTerminationException) {
                println("Execution halted: ${tx.message}")
                (a.source as Timer).stop()
            }
        }

        val irqTimer = Timer(1000/60) { a -> vm.irq(a.`when`) }

        programTimer.start()
        irqTimer.start()
    }
}
