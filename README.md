Aprof - Java Memory Allocation Profiler
=======================================

What is it?
-----------

The Aprof project is a Java Memory Allocation Profiler with very
low performance impact on profiled application.
It acts as an agent which transforms class bytecode by inserting counter 
increments wherever memory allocation is done. 

Download
--------

Download binaries of the latest release 24 here: 

http://svn-code.devexperts.com/code/aprof/releases/com/devexperts/aprof/agent/24/agent-24-bin.zip

Using Aprof
-----------

The profiled application should be run with additional JVM argument:

    java -javaagent:aprof.jar <your-application>

To get help on configuration parameters, run 

    java -jar aprof.jar

Do not rename agent file "aprof.jar"!

Documentation
-------------

Documentation can be found at the project's homepage:
https://code.devexperts.com/display/AProf/

Feedback
--------

Feel free to submit feature requests and bug reports at aprof@devexperts.com

Licensing
---------

This software is licensed under the terms of GPL 3.0 found in the file named "LICENSE". 
The distribution also contains binaries from the ASM project, 
licensed under terms found in the file named "LICENSE.asm".
