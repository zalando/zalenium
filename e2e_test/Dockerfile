FROM node:latest
MAINTAINER brian.maher@zalando.de

WORKDIR /opt/app
COPY package.json /opt/app/
RUN npm install --loglevel=warn

COPY . /opt/app

EXPOSE 3000

ENTRYPOINT ["npm", "start"]
