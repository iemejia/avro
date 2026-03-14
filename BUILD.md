# Apache Avro Build Instructions

## Overview

Apache Avro is a polyglot project spanning 9 languages. Each language can be
built and tested independently using Docker containers based on official Docker
Hub images, so you do not need to install any language toolchains on your host
machine.

## Requirements

### Using Docker (Recommended)

The only host requirements for Docker-based builds are:

 - [Docker](https://docs.docker.com/get-docker/) (with BuildKit support)
 - [Docker Compose](https://docs.docker.com/compose/install/) v2+

Each language has its own Dockerfile in `lang/<language>/Dockerfile` using the
official Docker Hub image for that language. The `docker-compose.yml` at the
project root defines a service for each language.

### Native (Without Docker)

To build natively without Docker, you need the toolchains for each language
you want to build:

 - Java: JDK 11, 17, and 21 with Maven 3.9+
 - Python: 3.10 or greater
 - JavaScript: Node.js 20.x+, npm
 - C: gcc, cmake, asciidoc, source-highlight, Jansson, pkg-config
 - C++: cmake 3.7.2+, g++, flex, bison, libboost-dev, libfmt-dev
 - C#: .NET SDK 8.0
 - Ruby: Ruby 2.7+, bundler, snappy
 - Perl: Perl 5.32+, cpanm, and various CPAN modules (see `lang/perl/Dockerfile`)
 - PHP: PHP 8.1+, Composer, php-zstd, php-snappy

## Docker Build System

### Architecture

Each language is built in its own container using the official base image at
the minimum version supported by CI:

| Language   | Service  | Base Image                          | Dockerfile              |
|------------|----------|-------------------------------------|-------------------------|
| Java       | java     | `maven:3.9-eclipse-temurin-21`      | `lang/java/Dockerfile`  |
| Python     | python   | `python:3.10-slim`                  | `lang/py/Dockerfile`    |
| JavaScript | js       | `node:20-slim`                      | `lang/js/Dockerfile`    |
| C          | c        | `gcc:14`                            | `lang/c/Dockerfile`     |
| C++        | cpp      | `gcc:14`                            | `lang/c++/Dockerfile`   |
| C#         | csharp   | `mcr.microsoft.com/dotnet/sdk:8.0`  | `lang/csharp/Dockerfile`|
| Ruby       | ruby     | `ruby:2.7-slim`                     | `lang/ruby/Dockerfile`  |
| Perl       | perl     | `perl:5.32-slim`                    | `lang/perl/Dockerfile`  |
| PHP        | php      | `php:8.1-cli`                       | `lang/php/Dockerfile`   |

### Building Docker Images

Build all language images:

```bash
./build.sh docker-build
```

Build specific language images:

```bash
./build.sh docker-build java python
```

Or use `docker compose` directly:

```bash
docker compose build java
```

### Running Tests in Docker

Run tests for all languages:

```bash
./build.sh docker-test
```

Run tests for specific languages:

```bash
./build.sh docker-test java python
```

### Running Linters in Docker

Run linters for all languages:

```bash
./build.sh docker-lint
```

Run linters for specific languages:

```bash
./build.sh docker-lint python js
```

### Language Name Aliases

The following aliases are recognized when specifying languages:

 - `c++` maps to the `cpp` service
 - `py` maps to the `python` service
 - `javascript` or `node` maps to the `js` service

### Development Mode

For iterative development, use the development overlay to mount your local
source tree into the container. This lets you edit files locally and re-run
builds without rebuilding the Docker image:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml run --rm java ./build.sh test
```

### Cleaning Up Docker Resources

To remove all per-language Docker images and volumes:

```bash
./build.sh veryclean
```

Or manually:

```bash
docker compose down --rmi local --volumes
```

## Building Natively

Once the native requirements are installed, `build.sh` can be used as follows:

```bash
./build.sh test    # runs tests for all languages
./build.sh dist    # creates all release distribution files in dist/
./build.sh clean   # removes all generated artifacts
```

Each language also has its own `build.sh` in its directory:

```bash
cd lang/java && ./build.sh test
cd lang/py   && ./build.sh lint test
```

## Developing inside a Container (Visual Studio Code Devcontainer)

Requirement:
 - [Visual Studio Code](https://code.visualstudio.com/)
 - [Remote Development extension pack](https://aka.ms/vscode-remote/download/extension)
 - Docker
   - Windows: [Docker Desktop](https://www.docker.com/products/docker-desktop)
   - macOS: [Docker Desktop](https://www.docker.com/products/docker-desktop)
   - Linux: [Docker CE/EE](https://docs.docker.com/install/#supported-platforms) and [Docker Compose](https://docs.docker.com/compose/install)

Useful links:
 - [Developing inside a Container](https://code.visualstudio.com/docs/remote/containers)
 - [Going further with Dev Containers](https://microsoft.github.io/code-with-engineering-playbook/developer-experience/going-further/)
