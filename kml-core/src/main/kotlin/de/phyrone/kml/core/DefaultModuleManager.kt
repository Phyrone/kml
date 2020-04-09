package de.phyrone.kml.core

import de.phyrone.kml.core.lifecycle.ModuleState
import de.phyrone.kml.core.lifecycle.Order
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.lang.IllegalStateException
import java.lang.ref.SoftReference
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class DefaultModuleManager<T>(config: ModuleLoaderConfig<T>) : ModuleManager<T> {

    val firstState = config.firstState
    val failedState = config.failedState
    private val moduleStates = mapOf(
        *config.lifecycleStates.map { state -> Pair(state.name, state) }.toTypedArray(),
        firstState.name to firstState,
        failedState.name to failedState
    ).also { states ->
        logger.debug("Loaded modulestates: ${states.keys.joinToString(",")}")
    }
    private val modules = mutableMapOf<String, ModuleWrapper>()
    private val statePathCache =
        HashMap<Pair<ModuleState<T>, ModuleState<T>>, SoftReference<Optional<List<ModuleState<T>>>>>()

    override fun get(name: String): ManagedModule<T>? = modules[name]

    override suspend fun runState(moduleState: ModuleState<T>): Unit = coroutineScope {
        modules.values.map { launch { it.letReachState(moduleState) } }.forEach { it.join() }
    }

    override suspend fun addModules(modules: List<ModuleContainer<T>>, reloadDependencies: Boolean) {
        modules.forEach { module -> addModule(module, false) }
        if (reloadDependencies) reloadDependencies()
    }

    override suspend fun addModule(container: ModuleContainer<T>, reloadDependencies: Boolean): ManagedModule<T> {
        val wrapper = ModuleWrapper(container)
        modules[container.description.name] = wrapper
        if (reloadDependencies) reloadDependencies()
        return wrapper
    }

    private suspend infix fun ModuleState<T>.pathTo(newModuleState: ModuleState<T>): List<ModuleState<T>> {
        val pair = Pair(this, newModuleState)
        val cached = statePathCache[pair]?.get()
            ?: Optional.ofNullable(StatePathFindTask(this, newModuleState).findPath()).also {
                statePathCache[pair] = SoftReference(it)
            }

        if (cached.isPresent)
            throw IllegalStateException("no path found between ${this.name} and ${newModuleState.name}")
        else return cached.get()
    }


    override suspend fun reloadDependencies() = coroutineScope {
        logger.debug("Start reloading dependencies...")
        modules.values.map { launch { it.clearDependencies() } }.forEach { it.join() }
        modules.values.map { launch { it.buildDependencies() } }.forEach { it.join() }
        logger.debug("Dependencies reloaded...")
    }

    private suspend infix fun ModuleState<T>.reached(target: ModuleState<T>) = (this == target ||
            pathTo(target).contains(this)).also { res ->
        logger.debug("Reached result of ${this.name} to ${target.name}: $res")
    }

    inner class StatePathFindTask(private val oldState: ModuleState<T>, private val newModuleState: ModuleState<T>) {

        private var currentShordestPossiblePath: List<ModuleState<T>>? = null
        private val addLock = Mutex()
        private val discoverJobs = mutableListOf<Job>()

        private suspend fun discoverFormerState(currend: ModuleState<T>, discoverdPath: List<ModuleState<T>>): Unit =
            coroutineScope {
                for (formerStateString in currend.possibleFormerStates) {
                    val formerState: ModuleState<T> = moduleStates[formerStateString] ?: continue
                    if (discoverdPath.contains(formerState)) continue
                    if (formerState == oldState) {
                        maybeAdd(discoverdPath)
                    } else {
                        discoverJobs.add(launch {
                            discoverFormerState(formerState, discoverdPath)
                        })
                    }
                }
            }

        private suspend fun maybeAdd(newPath: List<ModuleState<T>>) = addLock.withLock {
            val oldPath = currentShordestPossiblePath
            if (oldPath == null || oldPath.size > newPath.size) {
                currentShordestPossiblePath = newPath
            }
        }

        suspend fun findPath(): List<ModuleState<T>>? =
            coroutineScope {
                logger.debug("Find path: ${oldState.name} -> ${newModuleState.name}")
                if (oldState == newModuleState) return@coroutineScope listOf<ModuleState<T>>()
                val pathStart: List<ModuleState<T>> = listOf()
                discoverFormerState(newModuleState, pathStart)
                discoverJobs.forEach { job -> job.join() }
                val result = currentShordestPossiblePath?.reversed()?.plus(newModuleState)

                return@coroutineScope result?.also { list ->
                    logger.debug("Path found: ${oldState.name} -> " + list.joinToString(" -> ") { moduleState -> moduleState.name })
                } //?: throw IllegalStateException("no path found between ${oldState.name} and ${newModuleState.name}")
            }
    }


    enum class DependencyPriority(val prefix: String? = null) {
        REQUIRED,
        RECOMMENDED("?!"),
        OPTIONAL("?"),
        INCOMPATIBLE("!!");

        companion object {

            fun findByPrefix(name: String) =
                values().find { it.prefix != null && name.startsWith(it.prefix, true) } ?: REQUIRED
        }


    }


    private inner class ModuleWrapper(
        /* and yes a container in a container */
        override val container: ModuleContainer<T>
    ) : ManagedModule<T> {
        val name = container.description.name
        var firstTimeDepedencyBuilded = false

        val stateLock = Mutex()
        var currentStateField: ModuleState<T> = firstState
        suspend fun getcurrentState() = stateLock.withLock { currentStateField }
        suspend fun setCurrentState(newModuleState: ModuleState<T>) =
            stateLock.withLock { currentStateField = newModuleState }

        private suspend fun ModuleState<T>.jumpIn() {
            actions.forEach { action ->
                if (action.working(container)) {
                    with(action) {
                        runAction()
                    }
                }
            }
        }

        fun clearDependencies() {
            dependsOnYouWrappers.clear()
            youDependsOnWrappers.clear()
        }

        suspend fun buildDependencies() {
            container.description.dependencies?.forEach { depString ->
                firstTimeDepedencyBuilded = true
                try {
                    handleDependencyString(depString)
                } catch (e: Exception) {
                    e.printStackTrace()
                    //TODO("handle depedency resolve failture")
                    iSetState(failedState)
                    return
                }

            }
        }

        private fun handleDependencyString(dependency: String) {
            val reversed: Boolean
            val temp1Dep = if (dependency.startsWith("<")) {
                reversed = true
                dependency.substring(1)
            } else {
                reversed = false
                dependency
            }
            val priority = DependencyPriority.findByPrefix(temp1Dep)
            val temp2Dep = temp1Dep.substring(priority.prefix?.length ?: 0)
            //TODO("version check")
            val target = modules[temp2Dep]
            when (priority) {
                DependencyPriority.REQUIRED -> require(target != null) //TODO("replace with own logic")
                DependencyPriority.INCOMPATIBLE -> require(target == null) //TODO("replace with own logic")
                DependencyPriority.OPTIONAL -> {
                }
                DependencyPriority.RECOMMENDED -> {
                    if (target == null) {
                        TODO("implement")
                    }
                }
            }
            if (target != null) {
                if (reversed) {
                    target.addDependency(this)
                } else {
                    addDependency(target)
                }
            }
        }

        private fun addDependency(wrapper: ModuleWrapper) {
            if (wrapper == this) return
            logger.debug("Module $name: add dependency ${wrapper.name}")
            youDependsOnWrappers.add(wrapper)
            wrapper.dependsOnYouWrappers.add(this)
        }

        val dependsOnYouWrappers = HashSet<ModuleWrapper>()
        val youDependsOnWrappers = HashSet<ModuleWrapper>()
        override val dependencies: List<ManagedModule<T>>
            get() = youDependsOnWrappers.toList()
        override val dependsOnYou: List<ManagedModule<T>>
            get() = dependsOnYou.toList()

        val stateUpdateListeners = mutableListOf<Mutex>()
        suspend fun awaitStateUpdate() {
            val listener = Mutex(true)
            stateUpdateListeners.add(listener)
            listener.lock()
            stateUpdateListeners.remove(listener)
        }

        private val setStateLock = Mutex()


        suspend fun awaitStateReached(targetState: ModuleState<T>) {
            while (!(getcurrentState() reached targetState)) {
                //TODO("repair event")
                //awaitStateUpdate()
                delay(10)
            }

        }

        private suspend fun iSetState(targetState: ModuleState<T>) = setStateLock.withLock {
            targetState.jumpIn()
            logger.debug("Module $name: change state to $targetState")
            setCurrentState(targetState)
            stateUpdateListeners.forEach { it.unlock() }
        }

        private suspend fun iSetStateOrdered(targetState: ModuleState<T>) {
            when (targetState.order) {
                Order.UNORDERED -> {
                }
                Order.ASCENDING -> youDependsOnWrappers.forEach {
                    logger.debug("Module $name: await ${it.name} reached state ${targetState.name}")
                    it.awaitStateReached(targetState)
                }
                Order.DESCENDING -> dependsOnYouWrappers.forEach {
                    logger.debug("Module $name: await ${it.name} reached state ${targetState.name}")
                    it.awaitStateReached(targetState)
                }
            }
            iSetState(targetState)
        }


        suspend fun letReachState(targetState: ModuleState<T>) {
            if (targetState == getcurrentState()) return
            val path = getcurrentState() pathTo targetState
            path.forEach {
                iSetStateOrdered(it)
            }
        }

    }

    companion object Static {
        private val logger = LoggerFactory.getLogger(DefaultModuleManager::class.java)
    }


}