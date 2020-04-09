package de.phyrone.kml.core.lifecycle

import de.phyrone.kml.core.ManagedModule
import de.phyrone.kml.core.ModuleContainer

interface ModuleContainerAction<T> : Action<T> {
    override suspend fun ManagedModule<T>.runAction() {
        container.runModuleContainerAction()
    }

    suspend fun ModuleContainer<T>.runModuleContainerAction()
}

interface ModuleAction<T> : ModuleContainerAction<T> {
    override suspend fun ModuleContainer<T>.runModuleContainerAction() {
        runModuleAction(this@ModuleAction)
    }

    suspend fun T.runModule()
}