#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# ===========================================================================
# Bash functions that can be used in this script or exported by using
# source build.sh

change_java_version() {
  local jdk=$1
  if ((jdk)) && [[ -d /usr/local/openjdk-${jdk} ]]; then
    export JAVA_HOME=/usr/local/openjdk-${jdk}
    export PATH=$JAVA_HOME/bin:$PATH
    echo "----------------------"
    echo "Java version switched:"
  else
    echo "Using the current Java version:"
  fi
  echo "  JAVA_HOME=$JAVA_HOME"
  echo "  PATH=$PATH"
  java -version
}

# Stop here if sourcing for functions
[[ "$0" == *"bash" ]] && return 0

# ===========================================================================

# This might not have been sourced if the entrypoint is not bash
[[ -f "$HOME/.cargo/env" ]] && . "$HOME/.cargo/env"

set -xe
cd "${0%/*}"

VERSION=$(<share/VERSION.txt)

# All supported language services in docker-compose.yml
ALL_LANG_SERVICES="java python js c cpp csharp ruby perl php"

usage() {
  echo "Usage: $0 {lint|test|dist|sign|clean|veryclean|rat|githooks|docker-build|docker-test|docker-lint}"
  echo ""
  echo "Docker per-language targets (using official Docker images):"
  echo "  docker-build [lang ...]   Build Docker images for specified languages (or all)"
  echo "  docker-test  [lang ...]   Run tests in Docker containers for specified languages (or all)"
  echo "  docker-lint  [lang ...]   Run linters in Docker containers for specified languages (or all)"
  echo ""
  echo "Available languages: $ALL_LANG_SERVICES"
  echo ""
  echo "Examples:"
  echo "  $0 docker-build              # Build all language Docker images"
  echo "  $0 docker-build java python  # Build only Java and Python images"
  echo "  $0 docker-test java          # Run Java tests in its official Docker image"
  echo "  $0 docker-test               # Run all language tests in parallel"
  echo "  $0 docker-lint python js     # Lint Python and JavaScript in Docker"
  exit 1
}

# Resolve language names to docker compose service names
resolve_services() {
  local services=""
  for lang in "$@"; do
    case "$lang" in
      java|python|js|c|cpp|csharp|ruby|perl|php)
        services="$services $lang"
        ;;
      c++)
        services="$services cpp"
        ;;
      py)
        services="$services python"
        ;;
      javascript|node)
        services="$services js"
        ;;
      *)
        echo "Unknown language: $lang"
        echo "Available: $ALL_LANG_SERVICES"
        exit 1
        ;;
    esac
  done
  echo "$services"
}

(( $# == 0 )) && usage

while (( "$#" ))
do
  target="$1"
  shift

  # Change the JDK from the default for all targets that will eventually require Java (or maven).
  # This only occurs when the JAVA environment variable is set and a Java environment exists in
  # the "standard" location (defined by the openjdk docker images).  This will typically occur in CI
  # builds.  In all other cases, the Java version is taken from the current installation for the user.
  case "$target" in
    lint|test|dist|clean|veryclean|rat)
    change_java_version "$JAVA"
    ;;
  esac

  case "$target" in

    lint)
      for lang_dir in lang/*; do
        (cd "$lang_dir" && ./build.sh lint)
      done
      ;;

    test)
      # run lang-specific tests
      (cd lang/java; ./build.sh test)

      # create interop test data
      mkdir -p build/interop/data
      (cd lang/java/avro; mvn -B -P interop-data-generate generate-resources)

      # install java artifacts required by other builds and interop tests
      mvn -B install -DskipTests
      (cd lang/py && ./build.sh lint test)
      (cd lang/c; ./build.sh test)
      (cd lang/c++; ./build.sh lint test)
      (cd lang/csharp; ./build.sh test)
      (cd lang/js; ./build.sh lint test)
      (cd lang/ruby; ./build.sh lint test)
      (cd lang/php; ./build.sh lint test)
      (cd lang/perl; ./build.sh lint test)

      (cd lang/py; ./build.sh interop-data-generate)
      (cd lang/c; ./build.sh interop-data-generate)
      #(cd lang/c++; make interop-data-generate)
      (cd lang/csharp; ./build.sh interop-data-generate)
      (cd lang/js; ./build.sh interop-data-generate)
      (cd lang/ruby; ./build.sh interop-data-generate)
      (cd lang/php; ./build.sh interop-data-generate)
      (cd lang/perl; ./build.sh interop-data-generate)

      # run interop data tests
      (cd lang/java/ipc; mvn -B test -P interop-data-test)
      (cd lang/py; ./build.sh interop-data-test)
      (cd lang/c; ./build.sh interop-data-test)
      #(cd lang/c++; make interop-data-test)
      (cd lang/csharp; ./build.sh interop-data-test)
      (cd lang/js; ./build.sh interop-data-test)
      (cd lang/ruby; ./build.sh interop-data-test)
      (cd lang/php; ./build.sh test-interop)
      (cd lang/perl; ./build.sh interop-data-test)

      # java needs to package the jars for the interop rpc tests
      (cd lang/java/tools; mvn -B package -DskipTests)

      # run interop rpc test
      ./share/test/interop/bin/test_rpc_interop.sh
    ;;

    dist)
      # build source tarball
      mkdir -p build

      SRC_DIR=avro-src-$VERSION
      DOC_DIR=avro-doc-$VERSION

      rm -rf "build/${SRC_DIR}"
      if [ -d .svn ]; then
        svn export --force . "build/${SRC_DIR}"
      elif [ -d .git ]; then
        mkdir -p "build/${SRC_DIR}"
        git archive HEAD | tar -x -C "build/${SRC_DIR}"
      else
        echo "Not SVN and not GIT .. cannot continue"
        exit 255
      fi

      # runs RAT on artifacts
      mvn -N -P rat antrun:run verify

      # install java artifacts required by other builds and interop tests
      mvn -B install -DskipTests

      mkdir -p dist
      (cd build; tar czf "../dist/${SRC_DIR}.tar.gz" "${SRC_DIR}")

      # build lang-specific artifacts

      (cd lang/java;./build.sh dist; mvn install -pl tools -am -DskipTests)
      (cd lang/java/trevni/doc; mvn site)
      (mvn -N -P copy-artifacts antrun:run)

      (cd lang/py; ./build.sh dist)
      (cd lang/c; ./build.sh dist)
      (cd lang/c++; ./build.sh dist)
      (cd lang/csharp; ./build.sh dist)
      (cd lang/js; ./build.sh dist)
      (cd lang/ruby; ./build.sh dist)
      (cd lang/php; ./build.sh dist)

      mkdir -p dist/perl
      (cd lang/perl; ./build.sh dist)
      cp "lang/perl/Avro-$VERSION.tar.gz" dist/perl/

      # build docs
      cp -r doc/ build/staging-web/
      find build/staging-web/ -type f -print0 | xargs -0 sed -r -i "s#\+\+version\+\+#${VERSION,,}#g"
      mkdir -p build/staging-web/public/docs/
      mv build/staging-web/content/en/docs/++version++ build/staging-web/public/docs/"${VERSION,,}"
      (cd build/staging-web/ && npm install && hugo --gc --minify)
      cp -R build/staging-web/public/docs/"${VERSION,,}"/* "build/$DOC_DIR/"
      cp -R "build/$DOC_DIR/api" build/staging-web/public/docs/"${VERSION,,}"/
      ( cd build/staging-web/public/docs/; ln -s "${VERSION,,}" current )
      # add LICENSE and NOTICE for docs
      mkdir -p "build/$DOC_DIR"
      cp doc/LICENSE "build/$DOC_DIR"
      cp doc/NOTICE "build/$DOC_DIR"
      (cd build; tar czf "../dist/avro-doc-$VERSION.tar.gz" "$DOC_DIR")

      cp DIST_README.txt dist/README.txt
      ;;

    sign)
      set +x

      echo -n "Enter password: "
      stty -echo
      read -r password
      stty echo

      for f in $(find dist -type f \
        \! -name '*.md5' \! -name '*.sha1' \
        \! -name '*.sha512' \! -name '*.sha256' \
        \! -name '*.asc' \! -name '*.txt' );
      do
        (cd "${f%/*}" && shasum -a 512 "${f##*/}") > "$f.sha512"

        if [ -z "$GPG_LOCAL_USER" ]; then
          gpg --pinentry-mode loopback --passphrase "$password" --armor --output "$f.asc" --detach-sig "$f"
        else
          gpg --pinentry-mode loopback --local-user="$GPG_LOCAL_USER" --passphrase "$password" --armor --output "$f.asc" --detach-sig "$f"
        fi

      done

      set -x
      ;;

    clean)
      rm -rf build dist
      rm -rf doc/public/ doc/resources/ doc/node_modules/ doc/package-lock.json doc/.hugo_build.lock

      (mvn -B clean)
      rm -rf lang/java/*/userlogs/
      rm -rf lang/java/*/dependency-reduced-pom.xml

      (cd lang/py; ./build.sh clean)
      rm -rf lang/py/userlogs/

      (cd lang/c; ./build.sh clean)

      (cd lang/c++; ./build.sh clean)

      (cd lang/csharp; ./build.sh clean)

      (cd lang/js; ./build.sh clean)

      (cd lang/ruby; ./build.sh clean)

      (cd lang/php; ./build.sh clean)

      (cd lang/perl; ./build.sh clean)

      ;;

    veryclean)
      rm -rf build dist
      rm -rf doc/public/ doc/resources/ doc/node_modules/ doc/package-lock.json doc/.hugo_build.lock

      (mvn -B clean)
      rm -rf lang/java/*/userlogs/
      rm -rf lang/java/*/dependency-reduced-pom.xml

      (cd lang/py; ./build.sh clean)
      rm -rf lang/py/userlogs/

      (cd lang/c; ./build.sh clean)

      (cd lang/c++; ./build.sh clean)

      (cd lang/csharp; ./build.sh clean)

      (cd lang/js; ./build.sh clean)

      (cd lang/ruby; ./build.sh clean)

      (cd lang/php; ./build.sh clean)

      (cd lang/perl; ./build.sh clean)

      rm -rf lang/c++/build
      rm -rf lang/js/node_modules
      rm -rf lang/perl/inc/
      rm -rf lang/ruby/.gem/
      rm -rf lang/ruby/Gemfile.lock
      rm -rf lang/csharp/src/apache/ipc.test/bin/
      rm -rf lang/csharp/src/apache/ipc.test/obj

      # Clean up Docker images and volumes
      docker compose down --rmi local --volumes 2>/dev/null || true
      ;;

    rat)
      mvn test -Dmaven.main.skip=true -Dmaven.test.skip=true -DskipTests=true -P rat -pl :avro-toplevel
      ;;

    githooks)
      echo "Installing AVRO git hooks."
      cp share/githooks/* .git/hooks
      chmod +x .git/hooks/*
      chmod -x .git/hooks/*sample*
      ;;

    docker-build)
      # Build Docker images for per-language containers using official images.
      # Remaining arguments are treated as language names; if none, build all.
      services=""
      while (( "$#" )) && [[ "$1" != -* ]]; do
        services="$services $(resolve_services "$1")"
        shift
      done
      if [ -z "$services" ]; then
        services="$ALL_LANG_SERVICES"
      fi
      echo "Building Docker images for:$services"
      # shellcheck disable=SC2086
      docker compose build $services
      ;;

    docker-test)
      # Run tests inside per-language Docker containers using official images.
      # Remaining arguments are treated as language names; if none, test all.
      services=""
      while (( "$#" )) && [[ "$1" != -* ]]; do
        services="$services $(resolve_services "$1")"
        shift
      done
      if [ -z "$services" ]; then
        services="$ALL_LANG_SERVICES"
      fi
      echo "Running tests in Docker for:$services"
      for svc in $services; do
        echo "================================================================"
        echo "  Testing: $svc"
        echo "================================================================"
        docker compose run --rm "$svc" ./build.sh test
      done
      ;;

    docker-lint)
      # Run linters inside per-language Docker containers using official images.
      # Remaining arguments are treated as language names; if none, lint all.
      services=""
      while (( "$#" )) && [[ "$1" != -* ]]; do
        services="$services $(resolve_services "$1")"
        shift
      done
      if [ -z "$services" ]; then
        services="$ALL_LANG_SERVICES"
      fi
      echo "Running linters in Docker for:$services"
      for svc in $services; do
        echo "================================================================"
        echo "  Linting: $svc"
        echo "================================================================"
        docker compose run --rm "$svc" ./build.sh lint
      done
      ;;

    *)
      usage
      ;;
  esac
done
