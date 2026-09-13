package prog8.compiler

import com.github.ajalt.mordant.rendering.*
import com.github.ajalt.mordant.table.ColumnWidth
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal

internal data class MetricsRow(val metric: String, val newValue: String, val baseline: String, val change: String)

internal interface Presenter {
    fun printlnLine(message: String)
    fun printErrorLine(message: String)
    fun styleOk(message: String): String
    fun styleFail(message: String): String
    fun clearScreen()
    fun printMetricsTable(rows: List<MetricsRow>)
    fun printHitsTable(hits: List<Triple<String, Int, String>>)
}

internal fun createPresenter(plainText: Boolean): Presenter {
    // Only construct a real Terminal on an interactive console. Probing
    // Mordant's terminal providers loads JNI (JNA's dispatch library) as a
    // side effect, which normal piped compiles, tests and daemons must never
    // pay. Non-interactive output therefore stays plain println output.
    if(plainText || System.console() == null)
        return PlainPresenter()
    return try {
        val terminal = Terminal()
        // A zero (or unknown) width means wrapping and tables would collapse,
        // so fall back to plain output instead of rendering garbage.
        if(terminal.size.width <= 0)
            PlainPresenter()
        else
            MordantPresenter(terminal)
    } catch(_: Throwable) {
        PlainPresenter()
    }
}

private class PlainPresenter : Presenter {
    override fun printlnLine(message: String) = println(message)
    override fun printErrorLine(message: String) = System.err.println(message)
    override fun styleOk(message: String) = message
    override fun styleFail(message: String) = message
    override fun clearScreen() = println()

    override fun printMetricsTable(rows: List<MetricsRow>) {
        val header = listOf("Metric", "New", "Baseline", "Change")
        val table = listOf(header) + rows.map { listOf(it.metric, it.newValue, it.baseline, it.change) }
        val widths = IntArray(4) { col -> table.maxOf { it[col].length } }
        table.forEach { row ->
            println(row.mapIndexed { col, cell -> cell.padEnd(widths[col]) }.joinToString("  ").trimEnd())
        }
    }

    override fun printHitsTable(hits: List<Triple<String, Int, String>>) {
        hits.forEach { (file, line, content) -> println("$file:$line: $content") }
    }
}

private class MordantPresenter(val terminal: Terminal) : Presenter {
    override fun printlnLine(message: String) {
        terminal.println(message)
    }

    override fun printErrorLine(message: String) {
        // Styled red when the terminal supports color, plain otherwise.
        // Written to stderr explicitly to preserve the stream contract.
        // (TextStyle.invoke always emits codes, so it is gated on the
        // detected ANSI level instead of going through render().)
        val out = if(terminal.terminalInfo.ansiLevel == AnsiLevel.NONE)
            message
        else
            TextColors.brightRed(message)
        System.err.println(out)
    }

    private fun styled(message: String, color: TextColors): String =
        if(terminal.terminalInfo.ansiLevel == AnsiLevel.NONE)
            message
        else
            color(message)

    override fun styleOk(message: String) = styled(message, TextColors.green)
    override fun styleFail(message: String) = styled(message, TextColors.brightRed)

    override fun clearScreen() {
        if(terminal.terminalInfo.outputInteractive && terminal.terminalInfo.supportsAnsiCursor)
            terminal.cursor.move { clearScreen() }
        else
            terminal.println()
    }

    override fun printMetricsTable(rows: List<MetricsRow>) {
        terminal.println(table {
            borderStyle = TextColors.gray
            if(terminal.terminalInfo.ansiLevel == AnsiLevel.NONE)
                borderType = BorderType.ASCII
            header { row("Metric", "New", "Baseline", "Change") }
            body {
                rows.forEach { row(it.metric, it.newValue, it.baseline, it.change) }
            }
        })
    }

    override fun printHitsTable(hits: List<Triple<String, Int, String>>) {
        // File and Line get exactly the width they need and are never
        // truncated; Content takes the remaining width and wraps.
        val fileWidth = maxOf("File".length, hits.maxOfOrNull { it.first.length } ?: 0)
        val lineWidth = maxOf("Line".length, hits.maxOfOrNull { it.second.toString().length } ?: 0)
        terminal.println(table {
            borderStyle = TextColors.gray
            if(terminal.terminalInfo.ansiLevel == AnsiLevel.NONE)
                borderType = BorderType.ASCII
            column(0) { width = ColumnWidth.Fixed(fileWidth) }
            column(1) { width = ColumnWidth.Fixed(lineWidth) }
            column(2) { width = ColumnWidth.Expand() }
            header { row("File", "Line", "Content") }
            body {
                hits.forEach { (file, line, content) ->
                    row {
                        cell(file)
                        cell(line)
                        cell(content) {
                            whitespace = Whitespace.PRE_WRAP
                            overflowWrap = OverflowWrap.NORMAL
                        }
                    }
                }
            }
        })
    }
}
