package com.devexperts.aprof.hotspot;

/**
 * Type or Klass tag from hotspot compilation log. For example:
 * "&lt;type id='636' name='void'/&gt;" or
 * "&lt;klass id='646' name='java/lang/String' flags='17'/&gt;"
 *
 * @author Roman Elizarov
 */
class Type extends IdNamedObject {
	boolean klass; // true for tag 'klass', false for tag 'type'
}
