package de.phyrone.kml.core.lifecycle

import de.phyrone.kml.core.ModuleContainer

interface ModuleAction<T> : Action<T> {
    override suspend fun ModuleContainer<T>.runAction() {
        runModuleAction(this@ModuleAction)
    }

    fun T.runModule()
}