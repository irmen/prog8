package prog8.compilerinterface

class InternalCompilerException(message: String?) : Exception(message)

class AbortCompilation(message: String?) : Exception(message)
