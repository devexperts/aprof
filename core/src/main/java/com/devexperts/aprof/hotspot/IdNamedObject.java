package com.devexperts.aprof.hotspot;

/**
 * Type, Klass and Method descriptions that are written to hotspot compilation log as a part of each compilation task.
 *
 * @author Roman Elizarov
 */
class IdNamedObject {
	int id;
	StringBuilder name = new StringBuilder();
}
