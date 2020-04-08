package de.phyrone.kml.core

import InterfaceModuleDescription
import de.phyrone.kml.core.lifecycle.ModuleAction

interface ModuleContainer<T> {
    val description: InterfaceModuleDescription
    suspend fun runModuleAction(moduleAction: ModuleAction<T>)

}