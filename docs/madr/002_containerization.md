# Service Containerization

...or "how to package the service into a container".

## Context and Problem Statement

All services within TIS are to be containerized for runtime. There are a few wildly different ways to containerize a 
Spring Boot application and since each option has a different backing company and varying reasons, there's no one 
clear winner on which option is the best. Some time was taken to research the options and choose an 
implementation which should suit our needs the best.

## Considered Options

### Spring Boot's native methods

Boot has two main methods for containerization,

 1. Optimized image with layer indexing and `Dockerfile`
 2. Cloud Native Buildpacks 

While both of these were interesting, the needed work and suspected maintenance overhead, especially with Buildpacks 
was considered to be relatively high. While the plain `Dockerfile` approach is usually the best, the project scope 
in this case limits our ability to control every single aspect of the application development.

See also: [Spring Boot reference: Container Images](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#container-images)

### Jib

Jib is a tool made for building optimized images specifically out of Java projects, with enterprisey security 
features on top such as supporting repeatable builds. Additional benefit is that Jib doesn't require a container 
daemon making it a lot easier to integrate to in CI pipelines and such.  Jib also has direct integration with Maven 
through an official plugin.

See: [Google Container Tools: Jib](https://github.com/GoogleContainerTools/jib)

### Distroless

As even more DIY approach, GCT also has Distroless images, which are optimized base images built specifically with 
security in mind. The obvious limitation is that these containers do not contain e.g. shells or any other additional 
tools, which also enforce certain development and maintenance practices wherein internal state control must either be 
built into the application directly and exposed on purpose or otherwise integrated to platform tools.

See: [Google Container Tools: Distroless](https://github.com/GoogleContainerTools/distroless)

## Decision Outcome

Jib was chosen because of the daemonless execution and neat integration to Maven. Distroless nonroot image was 
chosen as base image to be used. 

## Consequences

 - Good: Jib configuration is part of the build lifecycle, so it stays up-to-date.
 - Good: Daemonless building means we won't need to configure GitHub Actions much at all.
 - Good: Chosen combination should provide a secure base for the containerized application.
 - Neutral: Lack of Dockerfile means customizing the image is a bit harder, but not impossible. Jib's documentation 
   has more on this.
 - Bad: Jib Maven Plugin requires specific tools to exist in environment for publishing images. This is not a major 
   hindrance, but is a bit of a hassle which needs to be revisited once we'll start producing container images in 
   automatic fashion.
