Aprof - Java Memory Allocation Profiler


What is it?
-------------

The Aprof project is a Java Memory Allocation Profiler with very
low performance impact on profiled application.

It acts as an agent which transforms class bytecode by inserting counter 
increments wherever memory allocation is done. 


Using Aprof
-------------

The profiled application should be run with "-javaagent:aprof.jar" JVM argument.
To get help on configuration parameters, run "java -jar aprof.jar".

Do not rename agent file "aprof.jar"!!!


The Latest Version
--------------------

The latest version can be found at <http://code.devexperts.com/>


Documentation
---------------

The documentation can be found at <http://code.devexperts.com/>


Feedback
----------

Feel free to submit feature requests and bug reports at <aprof@devexperts.com>. 


Licensing
-----------

This software is licensed under the terms found in the file named "LICENSE". 
The distribution also contains binaries from the ASM project, 
licensed under terms found in the file named "LICENSE.asm".


Thank you for using Aprof.
