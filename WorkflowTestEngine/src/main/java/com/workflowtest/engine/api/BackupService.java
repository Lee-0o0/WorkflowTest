package com.workflowtest.engine.api;

import java.nio.file.Path;

/** Creates a transactionally consistent backup of the local metadata database. */
public interface BackupService {
    Path createBackup(Path destinationDirectory);
}
