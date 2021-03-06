package com.avito.impact.changes

import com.avito.android.Result
import java.io.File

class StubChangesDetector : ChangesDetector {

    lateinit var result: Result<List<ChangedFile>>

    override fun computeChanges(
        targetDirectory: File,
        excludedDirectories: Iterable<File>
    ): Result<List<ChangedFile>> = result
}
