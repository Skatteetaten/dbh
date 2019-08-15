# Notes for branch

* Need to be able to list schemas that are in cooldown or else we will have no handle to restore them
* Implement support for taking a schema out of cooldown. This should probably be implemented as a separate request resource, and not as a method on an existing resource.
* Should probably add some tests for this. Would be nice to get back the spring rest doc support from earlier. 