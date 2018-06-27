FROM node:latest
MAINTAINER brian.maher@zalando.de

RUN apt-get update && \
  apt-get install -y jq=1.4-2.1+deb8u1 --no-install-recommends && \
  apt-get clean && \
  rm -rf /var/lib/apt/lists/*

WORKDIR /opt/app
COPY package.json /opt/app/
RUN npm install --loglevel=warn

COPY . /opt/app

WORKDIR /opt/app/tests
