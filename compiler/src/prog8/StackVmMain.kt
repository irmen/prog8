package prog8

import prog8.astvm.ScreenDialog
import prog8.stackvm.*
import java.awt.EventQueue
import javax.swing.Timer
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    stackVmMain(args)
}

fun stackVmMain(args: Array<String>) {
    printSoftwareHeader("StackVM")

    if(args.size != 1) {
        System.err.println("requires one argument: name of stackvm sourcecode file")
        exitProcess(1)
    }

    val program = Program.load(args.first())
    val vm = StackVm(traceOutputFile = null)
    val dialog = ScreenDialog("StackVM")
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
