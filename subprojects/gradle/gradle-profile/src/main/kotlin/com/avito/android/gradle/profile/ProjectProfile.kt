package com.avito.android.gradle.profile

import org.gradle.util.CollectionUtils
import org.gradle.util.Path
import java.util.HashMap

class ProjectProfile(
    val path: String
) : Operation() {

    private val tasks = HashMap<String, TaskExecution>()

    /**
     * Returns the configuration time of this project.
     */
    val configurationOperation: ContinuousOperation = ContinuousOperation(path)

    override val description: String
        get() = path

    override val elapsedTime: Long
        get() = getTasks().elapsedTime

    /**
     * Gets the task profiling container for the specified task.
     */
    fun getTaskProfile(taskPath: String): TaskExecution {
        var result: TaskExecution? = tasks[taskPath]
        if (result == null) {
            result = TaskExecution(Path.path(taskPath))
            tasks[taskPath] = result
        }
        return result
    }

    /**
     * Returns the task executions for this project.
     */
    fun getTasks(): CompositeOperation<TaskExecution> {
        val taskExecutions = CollectionUtils.sort(tasks.values, Operation.slowestFirst())
        return CompositeOperation(taskExecutions)
    }

    override fun toString(): String {
        return path
    }
}
