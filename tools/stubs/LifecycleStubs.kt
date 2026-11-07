// Stubs mínimos de AndroidX Lifecycle/ViewModel para typecheck do estado da UI.
@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

val ViewModelStubScope: CoroutineScope = CoroutineScope(SupervisorJob())

abstract class ViewModel {
    protected open fun onCleared() {}
}

object ViewModelProvider {
    interface Factory {
        fun <T : ViewModel> create(modelClass: Class<T>): T
    }
}

abstract class ViewModelProviderFactory : ViewModelProvider.Factory


object ViewModelScopeHolder {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob())
}

/** `viewModelScope` é uma propriedade de extensão do AndroidX. */
val ViewModel.viewModelScope: CoroutineScope get() = ViewModelScopeHolder.scope
