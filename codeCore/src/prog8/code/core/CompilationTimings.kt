package prog8.code.core

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Optional sub-phase timings for the code generation stage, reported by ``-timings``.
 *
 * Collection is thread-local, so parallel compilations in one JVM (tests, daemon) never mix their
 * measurements, and code generators can instrument themselves without threading a collector
 * through every constructor and interface.
 */
object CompilationTimings {
    private val session = ThreadLocal<MutableList<Pair<String, Duration>>?>()

    /** Runs [block] while collecting sub-phase timings, if [enabled]. */
    fun <T> collect(enabled: Boolean, block: () -> T): Pair<T, List<Pair<String, Duration>>> {
        if(!enabled)
            return block() to emptyList()
        val previous = session.get()
        val collected = mutableListOf<Pair<String, Duration>>()
        session.set(collected)
        return try {
            block() to collected
        } finally {
            session.set(previous)
        }
    }

    /** Timed region for a sub-phase. A no-op when no collection is active. */
    fun <T> measure(label: String, block: () -> T): T {
        val mark = mark() ?: return block()
        return block().also { record(label, mark) }
    }

    /** Start of a sub-phase, or null when no collection is active. */
    fun mark(): TimeMark? = if(session.get()==null) null else TimeSource.Monotonic.markNow()

    /** End of the sub-phase started at [mark]. A no-op when [mark] is null. */
    fun record(label: String, mark: TimeMark?) {
        if(mark!=null)
            session.get()?.add(label to mark.elapsedNow())
    }
}
