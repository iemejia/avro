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

# Python build using the official Python image
FROM python:3.12-slim

WORKDIR /avro

# Install system dependencies required for compression libraries
RUN apt-get -qqy update \
 && apt-get -qqy install --no-install-recommends \
      build-essential \
      curl \
      git \
      libbz2-dev \
      libjansson-dev \
      liblzma-dev \
      libsnappy-dev \
      libzstd-dev \
 && apt-get -qqy clean \
 && rm -rf /var/lib/apt/lists/*

# Install uv (modern Python package manager used by build.sh)
RUN curl -LsSf https://astral.sh/uv/0.10.4/install.sh | sh
ENV PATH="/root/.local/bin:${PATH}"

# Copy the full project
COPY . .

WORKDIR /avro/lang/py

# Install Python dependencies
RUN uv sync --frozen

CMD ["./build.sh", "test"]
