# Notes for branch

## About deletion
* DatabaseHotelService should offer options to both delete and put a schema into cooldown

* Janitor should have two modes
    * Determine which schemas should be put into cooldown based on heuristics (unused, old, etc)
    * Determine which schemas in cooldown should be permanently deleted
    * pruneSchemasForDeletion needs to be renamed. This method should probably only put schemas into cooldown.
    * Create a scheduled task that will permanently delete schemas.

* Permanent deletion
    * Oracle schemas should not be deleted by us. They are deleted by the database team.
        * OracleDatabaseManager.deleteSchema should be noop
        * Existing Oracle delete code must be moved into utilities for tests to use
    * Postgres schemas should be deleted by us. PostgresDatabaseManager.deleteSchema must permanently delete schema.
        
* How do we handle ExternalSchema and cooldown?
    * Should ExternalSchemas be restorable via the cooldown mechanism?
    * Should ExternaSchemaManager and DatabaseManager interfaces be aligned to support this?    
