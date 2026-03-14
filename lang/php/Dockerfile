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

# PHP build using the official PHP image (minimum supported version from CI)
FROM php:8.1-cli

WORKDIR /avro

# Install system dependencies and PHP extensions
RUN apt-get -qqy update \
 && apt-get -qqy install --no-install-recommends \
      git \
      libbz2-dev \
      libcurl4-openssl-dev \
      libgmp-dev \
      libxml2-dev \
      libzstd-dev \
      unzip \
 && docker-php-ext-install bz2 curl gmp pcntl \
 && apt-get -qqy clean \
 && rm -rf /var/lib/apt/lists/*

# Install snappy and zstd PHP extensions from source
RUN cd /tmp \
 && git clone --recursive --depth=1 https://github.com/kjdev/php-ext-zstd.git \
 && cd php-ext-zstd \
 && phpize && ./configure && make && make install \
 && docker-php-ext-enable zstd \
 && cd /tmp && rm -rf php-ext-zstd \
 && git clone --recursive --depth=1 https://github.com/kjdev/php-ext-snappy.git \
 && cd php-ext-snappy \
 && phpize && ./configure && make && make install \
 && docker-php-ext-enable snappy \
 && cd /tmp && rm -rf php-ext-snappy

# Install Composer
RUN curl -sS https://getcomposer.org/installer | php -- --install-dir=/usr/local/bin --filename=composer

# Copy the full project
COPY . .

# Install PHP dependencies via Composer (from project root, as composer.json is there)
RUN composer install

WORKDIR /avro/lang/php

CMD ["./build.sh", "test"]
