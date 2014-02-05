package com.devexperts.aprof;

/**
 * @author Roman Elizarov
 */
interface IndexMapVisitor {
	public void acceptChild(IndexMap child);
}
