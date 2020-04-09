package de.phyrone.kml.core.lifecycle

import de.phyrone.kml.core.ManagedModule
import de.phyrone.kml.core.ModuleContainer

interface Action<T> {
    suspend fun working(container: ModuleContainer<T>): Boolean = true
    suspend fun ManagedModule<T>.runAction()
}