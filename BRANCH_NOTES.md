# Notes for branch

## Need to be able to list schemas that are in cooldown or else we will have no handle to restore them
Decided to implement schemas that are in cooldown (inactive) as a separate resource, /restorableSchema, with the actual 
schema as an embedded resource. This way we can avoid having the properties active, setToCooldownAt and deleteAfter
as nullable properties on the Schema resource. These properties would always be null for active schemas. Since
restorableSchema is an own resource for all inactive schemas, setToCooldownAt and deleteAfter will always be set.

## Implement support for taking a schema out of cooldown 
This was implemented as a nonidempotent PATCH to /api/v1/restorableSchema/{id} with the body { "active": true }.
Decided to use this approach as it will be able to handle potential support for updating deleteAfter Date should the
need arise. Was unsure about the best http sematics for this use case, though.

## Final notes
Should probably add some integration tests for this. Would be nice to get back the spring rest doc support from earlier. 
This is missing for now.