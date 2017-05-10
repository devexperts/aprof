package com.devexperts.aprof.hotspot;

/**
 * Method tag from hotspot compilation log. For example:
 * "&ltmethod id='729' holder='646' name='indexOf' return='634' arguments='634 634' flags='1' bytes='70' iicount='3836'/&gt;"
 *
 * @author Roman Elizarov
 */
class Method extends IdNamedObject {
	int holder;
	int returns;
	int nArguments;
	int[] arguments = new int[4];

	public void addArgument(int arg) {
		if (nArguments >= arguments.length) {
			int[] newArguments = new int[nArguments * 2];
			System.arraycopy(arguments, 0, newArguments, 0, nArguments);
			arguments = newArguments;
		}
		arguments[nArguments++] = arg;
	}
}
