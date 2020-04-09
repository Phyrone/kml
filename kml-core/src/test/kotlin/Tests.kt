import de.phyrone.kml.core.DefaultModuleManager
import de.phyrone.kml.core.ModuleContainer
import de.phyrone.kml.core.ModuleLoaderConfig
import de.phyrone.kml.core.lifecycle.*
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Duration

class Tests {

    @Test
    fun justWork() = runBlocking(newFixedThreadPoolContext(4, "KML-Test")) {
        println("Starting...")
        val modules = listOf(
            TestModule("A", "F"),
            TestModule("B", "A"),
            TestModule("C"),
            TestModule("D", "B"),
            TestModule("E"),
            TestModule("F", "G"),
            TestModule("G"),
            * (1..30).map { TestModule("I$it") }.toTypedArray()
        )

        val moduleLoader = DefaultModuleManager(TestModuleConfig)
        moduleLoader.addModules(modules, true)
        moduleLoader.runState(TestEnabledState)
        moduleLoader.runState(TestDisabledState)

    }
}


object TestModuleConfig : ModuleLoaderConfig<TestModule> {
    override val lifecycleStates: Collection<ModuleState<TestModule>> = listOf(TestEnabledState, TestDisabledState)
    override val firstState: ModuleState<TestModule> = TestFirstState
    override val failedState: ModuleState<TestModule> = TestFailedState


}

object TestFirstState : ModuleState<TestModule> {
    override val name: String = "first"
    override val possibleFormerStates: List<String> = listOf()
    override val order: Order = Order.ASCENDING
    override val actions: List<Action<TestModule>> = listOf()
}

object TestFailedState : ModuleState<TestModule> {
    override val name: String = "failed"
    override val possibleFormerStates: List<String> = listOf()
    override val order: Order = Order.ASCENDING
    override val actions: List<Action<TestModule>> = listOf()
}

object TestEnabledState : ModuleState<TestModule> {
    override val name: String = "enabled"
    override val possibleFormerStates: List<String> = listOf("first")
    override val order: Order = Order.ASCENDING
    override val actions: List<Action<TestModule>> = listOf(EAction())

    class EAction : ModuleContainerAction<TestModule> {
        override suspend fun ModuleContainer<TestModule>.runModuleContainerAction() {
            (this as TestModule).workHard(Duration.ofSeconds(2))
            println("Enable: ${description.name} {thread=${Thread.currentThread().name}}")
        }
    }
}

object TestDisabledState : ModuleState<TestModule> {
    override val name: String = "disabled"
    override val possibleFormerStates: List<String> = listOf("enabled", "first")
    override val order: Order = Order.DESCENDING
    override val actions: List<Action<TestModule>> = listOf(DAction())

    class DAction : ModuleContainerAction<TestModule> {
        override suspend fun ModuleContainer<TestModule>.runModuleContainerAction() {
            (this as TestModule).workHard(Duration.ofSeconds(1))
            println("Disable: ${description.name} {thread=${Thread.currentThread().name}}")
        }
    }
}

class TestModule(override val name: String, override val dependencies: List<String>) :
    ModuleContainer<TestModule>,
    ModuleDescription {
    constructor(name: String, vararg dependencies: String) : this(name, dependencies.asList())

    override val description: ModuleDescription = this
    override val website: String?
        get() = null
    override val authors: List<String>?
        get() = null
    override val version: String?
        get() = null

    override suspend fun runModuleAction(moduleAction: ModuleAction<TestModule>) {
        with(moduleAction) {
            runModule()
        }
    }

    fun workHard(duration: Duration) {
        val endTime = System.currentTimeMillis() + duration.toMillis()
        while (System.currentTimeMillis() < endTime) {
            val res = Int.MAX_VALUE - Int.MAX_VALUE / 2
        }

    }

    override suspend fun destroy() {
        //does nothing
    }
}