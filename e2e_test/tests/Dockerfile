FROM node

RUN apt-get update && apt-get install jq

WORKDIR /opt/app
COPY package.json /opt/app/
RUN npm install --loglevel=warn

COPY . /opt/app

WORKDIR /opt/app/tests
