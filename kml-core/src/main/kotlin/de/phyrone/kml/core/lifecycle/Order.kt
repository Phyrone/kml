package de.phyrone.kml.core.lifecycle

enum class Order {
    /**
     * You have to run your actions before you dependencies
     */
    ASCENDING,

    /**
     * Depednencies have to run there actions before you
     */
    DESCENDING,

    /**
     * no order, no more need to explain
     */
    UNORDERED
}