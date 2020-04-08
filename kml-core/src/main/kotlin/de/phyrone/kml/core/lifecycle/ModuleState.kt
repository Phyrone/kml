package de.phyrone.kml.core.lifecycle

interface ModuleState<T> {
    val name: String
    val possibleFormerStates: List<String>
    val order: Order
    val actions: List<Action<T>>
}