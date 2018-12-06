#== Ubuntu xenial is 16.04, i.e. FROM ubuntu:16.04
# Find latest images at https://hub.docker.com/r/library/ubuntu/
# Layer size: big: ~130 MB

FROM ubuntu:xenial-20181113
ENV UBUNTU_FLAVOR="xenial" \
    UBUNTU_DATE="20181113"
ARG UBUNTU_MIRROR=http://archive.ubuntu.com/ubuntu

#== Ubuntu flavors - common
RUN  echo "deb ${UBUNTU_MIRROR} ${UBUNTU_FLAVOR} main universe\n" > /etc/apt/sources.list \
  && echo "deb ${UBUNTU_MIRROR} ${UBUNTU_FLAVOR}-updates main universe\n" >> /etc/apt/sources.list \
  && echo "deb ${UBUNTU_MIRROR} ${UBUNTU_FLAVOR}-security main universe\n" >> /etc/apt/sources.list

MAINTAINER Team EP <diemol+team-ep@gmail.com>
# https://github.com/docker/docker/pull/25466#discussion-diff-74622923R677
LABEL maintainer "Team EP <diemol+team-ep@gmail.com>"

# No interactive frontend during docker build
ENV DEBIAN_FRONTEND=noninteractive \
    DEBCONF_NONINTERACTIVE_SEEN=true

# http://askubuntu.com/a/235911/134645
# Remove with: sudo apt-key del 2EA8F35793D8809A
RUN apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 2EA8F35793D8809A \
  && apt-key update -qqy
# Remove with: sudo apt-key del 40976EAF437D05B5
RUN apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 40976EAF437D05B5 \
  && apt-key update -qqy
# Remove with: sudo apt-key del 3B4FE6ACC0B21F32
RUN apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 3B4FE6ACC0B21F32 \
  && apt-key update -qqy
# Remove with: sudo apt-key del A2F683C52980AECF
RUN apt-key adv --keyserver keyserver.ubuntu.com --recv-keys A2F683C52980AECF \
  && apt-key update -qqy

#========================
# Miscellaneous packages
#========================
# libltdl7        0.3 MB
#   allows to run docker alongside docker
# netcat-openbsd  0.5 MB
#   inlcues `nc` an arbitrary TCP and UDP connections and listens
# pwgen           0.4 MB
#   generates random, meaningless but pronounceable passwords
# bc              0.5 MB
#   An arbitrary precision calculator language
# unzip           0.7 MB
#   uncompress zip files
# bzip2           1.29 MB
#   uncompress bzip files
# apt-utils       1.0 MB
#   commandline utilities related to package management with APT
# net-tools       0.8 MB
#   arp, hostname, ifconfig, netstat, route, plipconfig, iptunnel
# jq              1.1 MB
#   jq is like sed for JSON data, you can use it to slice and filter and map
# sudo            1.3 MB
#   sudo binary
# psmisc          1.445 MB
#   fuser – identifies what processes are using files.
#   killall – kills a process by its name, similar to a pkill Unices.
#   pstree – Shows currently running processes in a tree format.
#   peekfd – Peek at file descriptors of running processes.
# iproute2        2.971 MB
#   to use `ip` command
# iputils-ping    3.7 MB
#   ping, ping6 - send ICMP ECHO_REQUEST to network hosts
# dbus-x11        4.6 MB
#   is needed to avoid http://askubuntu.com/q/237893/134645
# wget            7.3 MB
#   The non-interactive network downloader
# curl             17 MB (real +diff when with wget: 7 MB)
#   transfer URL data using various Internet protocols
# apache2-utils
#   utility programs for webservers, needed to use htpasswd when the grid
#   should be protected
#
# Layer size: medium: 33.3 MB
# Layer size: medium: 31.4 MB (with --no-install-recommends)
RUN apt-get -qqy update \
  && apt-get -qqy install \
    libltdl7 \
    netcat-openbsd \
    pwgen \
    bc \
    unzip \
    bzip2 \
    apt-utils \
    net-tools \
    jq \
    sudo \
    psmisc \
    iproute2 \
    iputils-ping \
    dbus-x11 \
    wget \
    curl \
    apache2-utils \
  && apt-get -qyy autoremove \
  && rm -rf /var/lib/apt/lists/*

#######################
# Nginx reverse proxy #
#######################
# We need this to expose dynamic created ports
#  until https://github.com/docker/for-mac/issues/171
# Layer size: medium: 44.84 MB (with --no-install-recommends)
# Layer size: medium: 50.51 MB
RUN apt-get -qqy update \
  && apt-get -qqy --no-install-recommends install \
    nginx-extras \
  && apt-get -qyy autoremove \
  && rm -rf /var/lib/apt/lists/* \
  && nginx -v \
  && ln -s /usr/sbin/nginx /usr/bin/ \
  && /usr/bin/nginx -v \
  && chmod -R 777 /var/log/nginx \
  && chmod -R 777 /var/run/ \
  && chmod -R 777 /var/lib/nginx \
  && chmod -R 777 /usr/share/nginx/ \
  && chmod -R 777 /tmp

#==============================
# Locale and encoding settings
#==============================
# TODO: Allow to change instance language OS and Browser level
#  see if this helps: https://github.com/rogaha/docker-desktop/blob/68d7ca9df47b98f3ba58184c951e49098024dc24/Dockerfile#L57
ENV LANG_WHICH en
ENV LANG_WHERE US
ENV ENCODING UTF-8
ENV LANGUAGE ${LANG_WHICH}_${LANG_WHERE}.${ENCODING}
ENV LANG ${LANGUAGE}
# Layer size: small: ~9 MB
# Layer size: small: ~9 MB MB (with --no-install-recommends)
RUN apt-get -qqy update \
  && apt-get -qqy --no-install-recommends install \
    language-pack-en \
    tzdata \
    locales \
  && locale-gen ${LANGUAGE} \
  && dpkg-reconfigure --frontend noninteractive locales \
  && apt-get -qyy autoremove \
  && rm -rf /var/lib/apt/lists/*

#===================
# Timezone settings
#===================
# Full list at https://en.wikipedia.org/wiki/List_of_tz_database_time_zones
#  e.g. "US/Pacific" for Los Angeles, California, USA
# e.g. ENV TZ "US/Pacific"
ENV TZ "Europe/Berlin"
# Apply TimeZone
# Layer size: tiny: 1.339 MB
RUN echo "Setting time zone to '${TZ}'" \
  && echo "${TZ}" > /etc/timezone \
  && dpkg-reconfigure --frontend noninteractive tzdata

#========================================
# Add normal user with passwordless sudo
#========================================
# Layer size: tiny: 0.3 MB
RUN useradd seluser \
         --shell /bin/bash  \
         --create-home \
  && usermod -a -G sudo seluser \
  && gpasswd -a seluser video \
  && echo 'seluser:secret' | chpasswd \
  && useradd extrauser \
         --shell /bin/bash  \
  && usermod -a -G sudo extrauser \
  && gpasswd -a extrauser video \
  && gpasswd -a extrauser seluser \
  && echo 'extrauser:secret' | chpasswd \
  && echo 'ALL ALL = (ALL) NOPASSWD: ALL' >> /etc/sudoers

#==============================
# Java8 - OpenJDK JRE headless
# Minimal runtime used for executing non GUI Java programs
#==============================
# Regarding urandom see
#  http://stackoverflow.com/q/26021181/511069
#  https://github.com/SeleniumHQ/docker-selenium/issues/14#issuecomment-67414070
# Layer size: big: 132.2 MB
# Layer size: big: 132.2 MB (with --no-install-recommends)
RUN apt-get -qqy update \
  && apt-get -qqy install \
    openjdk-8-jre-headless \
  && sed -i 's/securerandom.source=file:\/dev\/urandom/securerandom.source=file:\/dev\/.\/urandom/g' \
       /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/security/java.security \
  && sed -i 's/securerandom.source=file:\/dev\/random/securerandom.source=file:\/dev\/.\/urandom/g' \
       /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/security/java.security \
  && apt-get -qyy autoremove \
  && rm -rf /var/lib/apt/lists/*

#==============================================
# Java blocks until kernel have enough entropy
# to generate the /dev/random seed
#==============================================
# See: SeleniumHQ/docker-selenium/issues/14
# Layer size: tiny: 0.8 MB
RUN apt-get -qqy update \
  && apt-key update -qqy \
  && apt-get -qqy install \
    haveged rng-tools \
  && service haveged start \
  && update-rc.d haveged defaults \
  && apt-get -qyy autoremove \
  && rm -rf /var/lib/apt/lists/*

#==============================================================
# Kubernetes doesn't need gosu or docker
# So if you set a build arg of kubernetesSlimVersion=true then
# the unneeded layers will be excluded
#==============================================================
ARG kubernetesSlimVersion=false

#==============================================================
# Simple Go-based setuid+setgid+setgroups+exec
# drop-in replacement to avoid using `su` as is buggy in docker
#==============================================================
# https://github.com/tianon/gosu
RUN if [ "${kubernetesSlimVersion}" = "false" ]; then \
        wget -nv -O /usr/bin/gosu \
            "https://github.com/tianon/gosu/releases/download/1.10/gosu-amd64" \
        && chmod +x /usr/bin/gosu \
        && gosu root bash -c 'whoami' | grep root; \
    else \
        echo "Skipping gosu because building in kubernetes slim mode"; \
    fi

#=====================
# Get docker binaries
#=====================
# Var `DOCKER` points to which binary to use at runtime
ENV DOCKER_HOST="unix:///var/run/docker.sock" \
    DOCKER_BUCKET="download.docker.com" \
    DOCKER="18.06.1-ce"

#----------------------------
# Docker 18.06.1-ce -- 15 MB
#----------------------------
# https://github.com/docker-library/docker/blob/master/17.09/Dockerfile
# Note: Starting with 1.13, newer CLIs can talk to older daemons
RUN if [ "${kubernetesSlimVersion}" = "false" ]; then \
        set -x \
        && DOCKER_VERSION="18.06.1-ce" \
        && curl -fSL "https://${DOCKER_BUCKET}/linux/static/stable/x86_64/docker-${DOCKER_VERSION}.tgz" \
                -o docker.tgz \
        && tar -xzvf docker.tgz \
        && mv docker/docker /usr/bin/docker-${DOCKER_VERSION} \
        && rm -rf docker/ && rm docker.tgz \
        && docker-${DOCKER_VERSION} --version | grep "${DOCKER_VERSION}"; \
    else \
        echo "Skipping adding Docker because of kubernetes slim mode"; \
    fi

#----------------------------
# Docker 18.09 -- 15 MB
#----------------------------
# https://github.com/docker-library/docker/blob/master/18.03/Dockerfile
# Note: Starting with 1.13, newer CLIs can talk to older daemons
RUN if [ "${kubernetesSlimVersion}" = "false" ]; then \
        set -x \
        && DOCKER_VERSION="18.09.0" \
        && curl -fSL "https://${DOCKER_BUCKET}/linux/static/stable/x86_64/docker-${DOCKER_VERSION}.tgz" \
                -o docker.tgz \
        && tar -xzvf docker.tgz \
        && mv docker/docker /usr/bin/docker-${DOCKER_VERSION} \
        && rm -rf docker/ && rm docker.tgz \
        && docker-${DOCKER_VERSION} --version | grep "${DOCKER_VERSION}"; \
    else \
        echo "Skipping adding Docker because of kubernetes slim mode"; \
    fi


# ------------------------#
# Sauce Connect Tunneling #
# ------------------------#
# https://docs.saucelabs.com/reference/sauce-connect/
# Layer size: medium: ~13 MB
ENV SAUCE_CONN_VER="sc-4.5.2-linux" \
    SAUCE_CONN_DOWN_URL="https://saucelabs.com/downloads"
RUN cd /tmp \
  && wget -nv "${SAUCE_CONN_DOWN_URL}/${SAUCE_CONN_VER}.tar.gz" \
  && tar -zxf "${SAUCE_CONN_VER}.tar.gz" \
  && rm -rf /usr/local/${SAUCE_CONN_VER} \
  && mv ${SAUCE_CONN_VER} /usr/local \
  && rm "${SAUCE_CONN_VER}.tar.gz" \
  && ln -sf /usr/local/${SAUCE_CONN_VER}/bin/sc /usr/local/bin/sc \
  && which sc

# -----------------------#
# BrowserStack Tunneling #
# -----------------------#
# https://www.browserstack.com/local-testing
# Layer size: medium: 16.02 MB
ENV BSTACK_TUNNEL_URL="https://www.browserstack.com/browserstack-local" \
    BSTACK_TUNNEL_ZIP="BrowserStackLocal-linux-x64.zip"
RUN cd /tmp \
  && wget -nv "${BSTACK_TUNNEL_URL}/${BSTACK_TUNNEL_ZIP}" \
  && unzip "${BSTACK_TUNNEL_ZIP}" \
  && chmod 755 BrowserStackLocal \
  && rm "${BSTACK_TUNNEL_ZIP}" \
  && mv BrowserStackLocal /usr/local/bin \
  && which BrowserStackLocal

# -----------------------#
# TestingBot Tunneling #
# -----------------------#
# https://testingbot.com/support/other/tunnel
ENV TB_TUNNEL_URL="https://testingbot.com/tunnel/testingbot-tunnel.jar"
# If you need to disable testingBot at build time set a build arg of testingBotEnabled=false
ARG testingBotEnabled=true
RUN if [ "${testingBotEnabled}" = "true" ]; then \
  cd /tmp \
  && wget -nv "${TB_TUNNEL_URL}" \
  && mv testingbot-tunnel.jar /usr/local/bin \
  && java -jar /usr/local/bin/testingbot-tunnel.jar --version; \
  else echo "Testing Bot Disabled"; \
  fi

#===================================================
# Run the following commands as non-privileged user
#===================================================
USER seluser
WORKDIR /home/seluser


#==========
# Zalenium
#==========
EXPOSE 4444 4445

ENV ZAL_VER="${project.build.finalName}" \
    BROWSER_STACK_TUNNEL_OPTS="-skipCheck -v -forcelocal" \
    BROWSER_STACK_TUNNEL_ID="zalenium" \
    BROWSER_STACK_TUNNEL="false" \
    BROWSER_STACK_WAIT_TIMEOUT="2m" \
    SAUCE_TUNNEL_MAX_RETRY_ATTEMPTS="1" \
    SAUCE_TUNNEL_READY_FILE="/tmp/sauce-connect-ready" \
    SAUCE_TUNNEL_ID="zalenium" \
    SAUCE_TUNNEL_DOCTOR_TEST="false" \
    SAUCE_WAIT_TIMEOUT="2m" \
    SAUCE_TUNNEL="false" \
    TESTINGBOT_TUNNEL_OPTS="--se-port 4447" \
    TESTINGBOT_TUNNEL="false" \
    TESTINGBOT_WAIT_TIMEOUT="2m" \
    SELENIUM_HUB_PARAMS="" \
    SELENIUM_NODE_PARAMS="" \
    SELENIUM_CONTAINER_LABELS="" \
    SELENIUM_WAIT_FOR_CONTAINER="true"

COPY entry.sh log warn error /usr/bin/
COPY nginx.conf /etc/nginx/
COPY css/ /home/seluser/css/
COPY js/ /home/seluser/js/
COPY zalenium.sh \
 LICENSE.md \
 Analytics.md \
 start-browserstack.sh \
 wait-browserstack.sh \
 start-saucelabs.sh \
 wait-saucelabs.sh \
 start-testingbot.sh \
 wait-testingbot.sh \
 list_template.html \
 dashboard_template.html \
 index.html \
 zalando.ico \
 logging_info.properties \
 logging_debug.properties \
 logback.xml \
 /home/seluser/
COPY scm-source.json /
COPY ${ZAL_VER}.jar /home/seluser/${ZAL_VER}.jar

#-----------------#
# Fix perms again #
#-----------------#
RUN sudo chmod +x /home/seluser/zalenium.sh \
  && sudo chmod +x /home/seluser/start-browserstack.sh \
  && sudo chmod +x /home/seluser/wait-browserstack.sh \
  && sudo chmod +x /home/seluser/start-saucelabs.sh \
  && sudo chmod +x /home/seluser/wait-saucelabs.sh \
  && sudo chmod +x /home/seluser/start-testingbot.sh \
  && sudo chmod +x /home/seluser/wait-testingbot.sh \
  && sudo chmod +x /usr/bin/log \
  && sudo chmod +x /usr/bin/warn \
  && sudo chmod +x /usr/bin/error \
  && sudo chmod +x /usr/bin/entry.sh \
  && sudo chmod 777 /etc/passwd \
  && sudo chown -R seluser:seluser /home/seluser

# Allows different operations in Openshift environments
# https://docs.openshift.com/container-platform/latest/creating_images/guidelines.html#openshift-specific-guidelines
RUN sudo chgrp -R 0 /home/seluser && \
    sudo chmod -R g=u /home/seluser && \
    sudo chmod -R u+x /home/seluser

# IMPORTANT: Using the string form `CMD "entry.sh"` without
# brackets [] causes Docker to run your process
# And using `bash` which doesn’t handle signals properly
ENTRYPOINT ["entry.sh"]
