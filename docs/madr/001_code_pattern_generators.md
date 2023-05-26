# Code Pattern Generators

> _This MADR is based on research done by @solita-sabinaf_
  
## Context and Problem Statement

Java isn't the greatest language to deal with data as-is due to its verbosity and lack of dynamic support for data 
structures. While [JEP 395: Records](https://openjdk.org/jeps/395) improved on this significantly, there is still 
several gaps in object instantiation, data modification, immutability and mapping which need to be solved in some way.

There are roughly two categories of interest for choosing libraries:

- **Builders**
- **Mappers**

In addition the following were set as quality metrics:

 - Non-surprising APIs, e.g. no magical annotations or superfluous features
 - "One library to rule them all"; simple is better, not too many dependencies
 - Works nicely with Spring Boot 3 and Jackson

## Considered Options

 - [Immutables](https://immutables.github.io/)
 - [AutoValue](https://github.com/google/auto/tree/main/value)
 - [Lombok](https://projectlombok.org/)
 - [Record Builder](https://github.com/Randgalt/record-builder)
 - [MapStruct](https://mapstruct.org/)

## Decision Outcome

Libraries' features were compared and some test code was also written on top of the existing service code to see how 
well the libraries behave within the framework and with others. 

 - **Immutables** was chosen because of its extensive support and feature set around working with immutable objects.
 - **MapStruct** had already been in use in project and during research it was found to work seamlessly with 
   Immutables, so it was kept.

## Consequences

 - Good: Less manually handled boilerplate for accessors/mutators/toString etc.
 - Good: Immutable data objects everywhere; defining application's internal model is now fast and easy
 - Neutral: Project may need manual rebuilding from time to time when developing
