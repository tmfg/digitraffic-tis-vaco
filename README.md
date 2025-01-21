###### Digitraffic / Travel Information Services

# Validator-Conversion

Validator-Conversion service (VACO) handles its namesake tasks for the common transit standards such as 
[GTFS][gtfs] and [NeTEx Nordic profile][netex-nordic]. The functionality is exposed through a simple Queue API where
each VACO task is processed asynchronously and the results are fetched using the Queue API's ticket abstraction.

## Development

### Prerequisites

> See [Development Environment Setup](https://finrail.atlassian.net/wiki/spaces/VACO1/pages/2720825453/Development+Environment+Setup)
> on Confluence for initial common setup instructions.

 - Java 21 LTS+
 - Maven 3+
 - Docker with Docker Compose

### Local Development / Quick Start

_Profile: `src/main/resources/application-local.properties`_

 1. Create `.env` file at the root of this repository 
    - Ask a fellow team member for a copy of this file and its contents, as it contains secrets which should not be publicly shared
 2. Start up dev environment with 
    ```shell
    docker compose up
     ```
 3. Run `VacoApplication` from within your IDE of choice or with Maven
    ```shell
    mvn spring-boot:run
    ```

### Containerized Development in Compose

> **!! THIS IS NOT A DEVELOPMENT CONTAINER !!**

_Profile: `src/main/resources/application-compose.properties`_

This variant is meant for testing out alternative environment configurations, container packaging of the application 
itself and such and is not meant to be and will never be meant to be used as primary way to run the application 
during development.

 1. Package the application to container with
    ```shell
    mvn clean compile jib:dockerBuild -Djib-maven-plugin.image-name=vaco:latest
    ``` 
 2. Run the Compose environment with
    ```shell
    docker compose --profile containerized up
    ```

#### Known Issues

While mostly everything works as expected, S3 integration has some issues because of S3 local path incompatibility, so
it is expected behavior to see

```
software.amazon.awssdk.core.exception.SdkClientException:
        Failed to send the request:
        Host name was invalid for dns resolution.
```

in the logs.

### Build, Packaging etc.

See GitHub Actions workflows in [`.github/workflows`](.github/workflows)

### API schema documentation

Swagger documentation is available at path: `/api/swagger-ui/index.html`

---

Copyright Fintraffic 2023-2025. Licensed under the EUPL-1.2 or later.

[gtfs]: https://gtfs.org/
[netex-nordic]: https://enturas.atlassian.net/wiki/spaces/PUBLIC/pages/728891481/Nordic+NeTEx+Profile
