package prog8.compiler.astprocessing

import prog8.ast.Program
import prog8.ast.statements.StructDecl
import prog8.code.core.DataType

/**
 * Shared helpers for list-iteration feature (see ideas/list-iteration.md).
 * Structural contract:
 *  - List: Head, Tail, TailPred in order; Head/TailPred are typed pointers to same node type; Tail is plain pointer sentinel.
 *  - Node: Succ/Pred or Next/Prev at offset 0, both typed self-pointers, not mixed.
 * NDK reference: /mnt/nfs/biggie/Storage/Retro/Amiga/AmigaNDK_headers  Include_H/exec/lists.h, Include_I/exec/lists.i
 */
object ListIterationHelper {

    fun structDeclFor(dt: DataType, program: Program): StructDecl? {
        val sub = dt.subType as? StructDecl
        if (sub != null) return sub
        val nameList = dt.subTypeFromAntlr ?: return null
        val simpleName = nameList.last()
        // search recursively - structs can be inside blocks, subroutines, etc. (e.g. local MyNode inside main.start)
        fun findInContainer(container: prog8.ast.IStatementContainer): StructDecl? {
            for (stmt in container.statements) {
                if (stmt is StructDecl && stmt.name == simpleName) return stmt
                if (stmt is prog8.ast.statements.Block) {
                    findInContainer(stmt)?.let { return it }
                }
                if (stmt is prog8.ast.statements.Subroutine) {
                    // Subroutine is also a container via its statements
                    for (s in stmt.statements) if (s is StructDecl && s.name == simpleName) return s
                    // also check nested AnonymousScope inside subroutine via walk?
                }
                if (stmt is prog8.ast.statements.AnonymousScope) {
                    findInContainer(stmt)?.let { return it }
                }
            }
            return null
        }
        for (mod in program.modules) {
            findInContainer(mod)?.let { return it }
            for (stmt in mod.statements) {
                if (stmt is prog8.ast.statements.Block) {
                    findInContainer(stmt)?.let { return it }
                }
            }
        }
        // fallback: try fully qualified lookup via first block's scope
        return null
    }

    fun isListIterable(dt: DataType, program: Program): Boolean {
        val decl = structDeclFor(dt, program) ?: return false
        return decl.isListStruct()
    }
}
