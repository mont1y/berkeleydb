## Logical to Physical Mapping Implementation - Java Edition Berkeley DB

**Overview**
This repository contains an implementation of the middle layer for a database system that handles the logical-to-physical mapping of data. The system uses Berkeley DB Java Edition as the storage engine and includes an SQL parser for interpreting SQL commands. The middle layer takes the parsed SQL commands and performs the necessary actions to interact with Berkeley DB.

**Key Features**
SQL Parser: Parses SQL commands into a format that the middle layer can work with.
Middle Layer: Implements logical-to-physical mapping by translating parsed SQL queries into low-level operations.
Berkeley DB Java Edition: Serves as the embedded storage engine for data management.
