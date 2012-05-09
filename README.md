PASC
====

This project automates the hardening of distributed protocols making them tolerant to Arbitrary State Corruptions (ASC), thus its name, Practial ASC. 

If you have any question, please contact us at pasc-library@yahoogroups.com

Library
-------

If you want to use PASC, you only need to provide the library with a specification of the **state**, the **event handlers** and the **messages** your protocol is going to use. Each of these specifications is a class that implements the appropriate
interface. 

Compilation
-----------

The library is implemented in Java and depends on several other projects:
* Javassist
* asm
* StringTemplate (ANTLR)
* Objenesis
* Cloning

Pasc uses maven as its buildsystem. Compile with
	
    $ mvn install
	
Tests should run cleanly.
