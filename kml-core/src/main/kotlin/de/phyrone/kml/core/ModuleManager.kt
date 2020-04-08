package de.phyrone.kml.core

import de.phyrone.kml.core.lifecycle.ModuleState

interface ModuleManager<T> {

    operator fun get(name: String): ManagedModule<T>?
    suspend fun addModule(container: ModuleContainer<T>, reloadDependencies: Boolean = true): ManagedModule<T>
    suspend fun addModules(modules: List<ModuleContainer<T>>, reloadDependencies: Boolean = true)
    suspend fun reloadDependencies()
    suspend fun runState(moduleState: ModuleState<T>)
}

interface ModuleLoaderConfig<T> {
    val lifecycleStates: Collection<ModuleState<T>>
    val firstState: ModuleState<T>
    val failedState: ModuleState<T>
}

interface ManagedModule<T> {
    val container: ModuleContainer<T>
}