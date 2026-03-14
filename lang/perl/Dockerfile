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

# Perl build using the official Perl image
FROM perl:5.40-slim

WORKDIR /avro

# Install system dependencies (build tools for native XS modules)
RUN apt-get -qqy update \
 && apt-get -qqy install --no-install-recommends \
      build-essential \
 && apt-get -qqy clean \
 && rm -rf /var/lib/apt/lists/*

# Install all required Perl modules from CPAN
# (Debian perl lib packages target system perl, not the official image's perl)
RUN cpanm --notest \
      Compress::Zlib \
      Compress::Zstd \
      CPAN::Uploader \
      Encode \
      Error::Simple \
      IO::String \
      JSON::MaybeXS \
      Module::Install \
      Module::Install::ReadmeFromPod \
      Module::Install::Repository \
      Object::Tiny \
      Perl::Critic \
      Regexp::Common \
      Test::Exception \
      Test::Pod \
      Try::Tiny

# Copy the full project
COPY . .

WORKDIR /avro/lang/perl

CMD ["./build.sh", "test"]
