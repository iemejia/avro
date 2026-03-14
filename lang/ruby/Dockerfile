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

# Ruby build using the official Ruby image (minimum supported version from CI)
FROM ruby:2.7-slim

WORKDIR /avro

# Install system dependencies needed for native gem extensions
RUN apt-get -qqy update \
 && apt-get -qqy install --no-install-recommends \
      build-essential \
      git \
      libsnappy-dev \
      libyaml-dev \
      libzstd-dev \
 && apt-get -qqy clean \
 && rm -rf /var/lib/apt/lists/*

# Copy the full project
COPY . .

WORKDIR /avro/lang/ruby

# Install gem dependencies (Ruby 2.7 requires bundler < 2.5)
RUN gem install bundler -v '~> 2.4.0' --no-document && bundle install

CMD ["./build.sh", "test"]
