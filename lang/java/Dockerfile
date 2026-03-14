# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Java build using the official Maven image with Eclipse Temurin JDK 21
FROM maven:3.9-eclipse-temurin-21

WORKDIR /avro

# The project requires JDK 11, 17, and 21 toolchains. The base image has JDK 21.
# Install Temurin JDK 11 and 17 alongside.
RUN apt-get -qqy update \
 && apt-get -qqy install --no-install-recommends wget apt-transport-https gnupg \
 && wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg \
 && echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" > /etc/apt/sources.list.d/adoptium.list \
 && apt-get -qqy update \
 && apt-get -qqy install --no-install-recommends temurin-11-jdk temurin-17-jdk \
 && apt-get -qqy clean \
 && rm -rf /var/lib/apt/lists/*

# Create toolchains.xml so Maven can find all three JDKs
RUN mkdir -p /root/.m2 \
 && echo '<?xml version="1.0" encoding="UTF-8"?>\n\
<toolchains>\n\
  <toolchain>\n\
    <type>jdk</type>\n\
    <provides>\n\
      <version>11</version>\n\
    </provides>\n\
    <configuration>\n\
      <jdkHome>/usr/lib/jvm/temurin-11-jdk-amd64</jdkHome>\n\
    </configuration>\n\
  </toolchain>\n\
  <toolchain>\n\
    <type>jdk</type>\n\
    <provides>\n\
      <version>17</version>\n\
    </provides>\n\
    <configuration>\n\
      <jdkHome>/usr/lib/jvm/temurin-17-jdk-amd64</jdkHome>\n\
    </configuration>\n\
  </toolchain>\n\
  <toolchain>\n\
    <type>jdk</type>\n\
    <provides>\n\
      <version>21</version>\n\
    </provides>\n\
    <configuration>\n\
      <jdkHome>/opt/java/openjdk</jdkHome>\n\
    </configuration>\n\
  </toolchain>\n\
</toolchains>' > /root/.m2/toolchains.xml

# Copy the full project (needed for top-level pom.xml and shared resources)
COPY . .

# Install the top-level Maven artifacts (needed by lang/java modules)
RUN mvn -B install -DskipTests -pl :avro-toplevel -N

WORKDIR /avro/lang/java

CMD ["./build.sh", "test"]
