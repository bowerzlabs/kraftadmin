package interceptors

import model.PulseContext

interface PulseContextProvider {
    /**
     * Returns the current pulse context from the execution environment.
     * In Reactive, this might return a Mono or be called within a context-aware block.
     */
    fun currentContext(): PulseContext?
}