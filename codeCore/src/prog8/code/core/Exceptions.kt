package prog8.code.core

class InternalCompilerException(message: String?) : Exception(message)

class AssemblyError(msg: String) : RuntimeException(msg)

class ErrorsReportedException(message: String?) : Exception(message)
