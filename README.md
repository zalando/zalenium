# Appium-Zalenium

Zalenium project is deprecated, which can be easily replaced by Selenoid and Selenium 4. However our target is to set up a grid for Appium, none of them is compatible with it except Selenium Grid v3.

This project provide simple replacement of Selenium hub v3 based on the last released Zalenium

## Quick start
```bash
docker run --rm -ti --name appium-zalenium -p 4444:4444 --privileged iqalab/appium-zalenium:3.141.59za start --desiredContainers 0 --maxDockerSeleniumContainers 0 --sendAnonymousUsageInfo false
```

Notice `--desiredContainers` `--maxDockerSeleniumContainers` must set to 0, since we removed all docker auto-scale feature.
##


## Build docker
```bash
mvn clean package -Pbuild-docker-image -DskipTests=true
```