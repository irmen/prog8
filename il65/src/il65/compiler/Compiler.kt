package il65.compiler

import il65.ast.INameScope
import il65.ast.Module
import kotlin.system.exitProcess


class Compiler(val options: CompilationOptions, val namespace: INameScope) {
    init {
        val zeropage = Zeropage(options)
    }

    fun compile(module: Module) : IntermediateForm {
        println("......@TODO compile Ast into Intermediate result......")       // todo
        return IntermediateForm(module.name)
    }
}


class IntermediateForm(val name: String) {
    fun optimize() {
        println("......@TODO optimize intermediate result......")       // todo
    }

    fun compileToAssembly(): AssemblyResult {
        println("......@TODO compile intermediate result to assembly code......")       // todo
        return AssemblyResult(name)
    }

}

enum class OutputType {
    RAW,
    PRG
}

enum class LauncherType {
    BASIC,
    NONE
}

enum class ZeropageType {
    COMPATIBLE,
    FULL,
    FULL_RESTORE
}


data class CompilationOptions(val output: OutputType,
                              val launcher: LauncherType,
                              val zeropage: ZeropageType,
                              val floats: Boolean)


class AssemblyResult(val name: String) {
    fun assemble(options: CompilationOptions, inputfilename: String, outputfilename: String) {
        println("......@TODO assemble with 64tass......")       // todo

        val command = mutableListOf("64tass", "--ascii", "--case-sensitive", "-Wall", "-Wno-strict-bool",
            "--dump-labels", "--vice-labels", "-l", outputfilename+".vice-mon-list",
            "--no-monitor", "--output", outputfilename, inputfilename)

        when(options.output) {
            OutputType.PRG -> {
                command.add("--cbm-prg")
                println("\nCreating C-64 prg.")
            }
            OutputType.RAW -> {
                command.add("--nostart")
                println("\nCreating raw binary.")
            }
        }

        val proc = ProcessBuilder(command).inheritIO().start()
        val result = proc.waitFor()
        if(result!=0) {
            System.err.println("assembler failed with returncode $result")
            exitProcess(result)
        }
    }

    fun genereateBreakpointList(): String {
/*
    def generate_breakpoint_list(self, program_filename: str) -> str:
        breakpoints = []
        vice_mon_file = program_filename + ".vice-mon-list"
        with open(vice_mon_file, "rU") as f:
            for line in f:
                match = re.fullmatch(r"al (?P<address>\w+) \S+_il65_breakpoint_\d+.?", line, re.DOTALL)
                if match:
                    breakpoints.append("$" + match.group("address"))
        with open(vice_mon_file, "at") as f:
            print("; vice monitor breakpoint list now follows", file=f)
            print("; {:d} breakpoints have been defined here".format(len(breakpoints)), file=f)
            print("del", file=f)
            for b in breakpoints:
                print("break", b, file=f)
        return vice_mon_file
 */
        return "monitorfile.txt"
    }
}