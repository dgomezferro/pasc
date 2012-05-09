PASC
====

PASC is a Java library that automates the detection of Arbitrary State Corruptions in processes of a distributed system.

Fault-tolerant distributed systems need to tolerate not only crashes but also random data corruptions due to disk failures, bit-flips in memory, or CPU/bus errors. These random errors can propagate and have unpredictable effects. However, manually adding error detection checks is cumbersome and difficult. Which checks need to be added? Where to place them? What if the variables used for the checks are themselves corrupted?

The motivation behind PASC is that developers should not care about these problems. They should focus on making their distributed system crash-tolerant. The PASC library wraps the processes of the system and takes care of executing all the checks that are necessary to transform arbitrary state corruptions into crashes and dropped messages. All this is transparent to the application. If a faulty process wrapped with PASC sends a corrupted messages, this corruption is exposed to the receiver, which discards the message. 

If you have any question, please contact us at pasc-library@yahoogroups.com

How to use PASC?
----------------

A message-passing distributed systems consists of processes. Processes can be modeled as collections of message handlers, each processing a different type of messages. All message handlers modify the state of the process.

A PASC process is implemented by specifying classes describing the **process state**, **event handlers** and the **messages** using the templates provided by the library.


Architecture
------------

The PASC library uses five techniques to handle ASC faults: state replication, redundant message handling, updates comparison, control-flow checks, and message verification. The main entry point to the library is the PascRuntime, which is responsible for dispachting messages and executing the message handlers guaranteeing ASC invariants. The user defined state is replicated and the message handlers are executed twice, checking that the result is always the same.

### Including PASC in a distributed system

The PASC runtime is initialized when the process is started. 

final PascRuntime runtime = new PascRuntime(protection);

The protection flag allows to turn off PASC if needed.

The initialization procedure must pass the process state, message handler and message classes used to the runtime. During the normal operations, all is needed to process an input message is to tell the runtime to handle the message. 

List<Message> outputMessages = runtime.handleMessage(inputMessage)

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
