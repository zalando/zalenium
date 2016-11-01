#== Ubuntu xenial is 16.04, i.e. FROM ubuntu:16.04
# Find latest images at https://hub.docker.com/r/library/ubuntu/
FROM ubuntu:xenial-20161010
ENV UBUNTU_FLAVOR="xenial" \
    UBUNTU_DATE="20161010"

#== Ubuntu flavors - common
RUN  echo "deb http://archive.ubuntu.com/ubuntu ${UBUNTU_FLAVOR} main universe\n" > /etc/apt/sources.list \
  && echo "deb http://archive.ubuntu.com/ubuntu ${UBUNTU_FLAVOR}-updates main universe\n" >> /etc/apt/sources.list \
  && echo "deb http://archive.ubuntu.com/ubuntu ${UBUNTU_FLAVOR}-security main universe\n" >> /etc/apt/sources.list

MAINTAINER Team TIP <diemol+team-tip@gmail.com>

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
# wget - The non-interactive network downloader
# curl - transfer a URL
# libltdl7 - to run docker alongside docker
RUN apt-get -qqy update \
  && apt-get -qqy install \
    sudo \
    wget \
    curl \
    libltdl7 \
  && rm -rf /var/lib/apt/lists/*

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
RUN locale-gen ${LANGUAGE} \
  && dpkg-reconfigure --frontend noninteractive locales \
  && apt-get -qqy update \
  && apt-get -qqy install \
    language-pack-en \
  && rm -rf /var/lib/apt/lists/*

#===================
# Timezone settings
#===================
# Full list at https://en.wikipedia.org/wiki/List_of_tz_database_time_zones
#  e.g. "US/Pacific" for Los Angeles, California, USA
# e.g. ENV TZ "US/Pacific"
ENV TZ "Europe/Berlin"
# Apply TimeZone
RUN echo "Setting time zone to '${TZ}'" \
  && echo ${TZ} > /etc/timezone \
  && dpkg-reconfigure --frontend noninteractive tzdata

#================
# Java9 - Oracle
#================
# Fails! is giving error java.lang.IllegalArgumentException:
#  Errors were discovered while reifying SystemDescriptor(
#    implementation=org.glassfish.jersey.message.internal.DataSourceProvider

#==================
# Java8 - Oracle
#==================
# Regarding urandom see
#  http://stackoverflow.com/q/26021181/511069
#  https://github.com/SeleniumHQ/docker-selenium/issues/14#issuecomment-67414070
RUN apt-get -qqy update \
  && apt-get -qqy install \
    software-properties-common \
  && echo debconf shared/accepted-oracle-license-v1-1 \
      select true | debconf-set-selections \
  && echo debconf shared/accepted-oracle-license-v1-1 \
      seen true | debconf-set-selections \
  && add-apt-repository ppa:webupd8team/java \
  && apt-get -qqy update \
  && apt-get -qqy install \
    oracle-java8-installer \
  && sed -i 's/securerandom.source=file:\/dev\/urandom/securerandom.source=file:\/dev\/.\/urandom/g' \
       /usr/lib/jvm/java-8-oracle/jre/lib/security/java.security \
  && sed -i 's/securerandom.source=file:\/dev\/random/securerandom.source=file:\/dev\/.\/urandom/g' \
       /usr/lib/jvm/java-8-oracle/jre/lib/security/java.security \
  && rm -rf /var/lib/apt/lists/*

#===================
# Get docker binary
#===================
# https://github.com/docker-library/docker/blob/master/1.12/Dockerfile#L1
ENV DOCKER_BUCKET get.docker.com
ENV DOCKER_VERSION 1.12.3
ENV DOCKER_SHA256 626601deb41d9706ac98da23f673af6c0d4631c4d194a677a9a1a07d7219fa0f
RUN set -x \
  && curl -fSL "https://${DOCKER_BUCKET}/builds/Linux/x86_64/docker-${DOCKER_VERSION}.tgz" -o docker.tgz \
  && echo "${DOCKER_SHA256} *docker.tgz" | sha256sum -c - \
  && tar -xzvf docker.tgz \
  && mv docker/* /usr/local/bin/ \
  && rmdir docker \
  && rm docker.tgz \
  && docker -v
ENV DOCKER_HOST="unix:///var/run/docker.sock"

#========================================
# Add normal user with passwordless sudo
#========================================
RUN useradd seluser \
         --shell /bin/bash  \
         --create-home \
  && usermod -a -G sudo seluser \
  && gpasswd -a seluser video \
  && echo 'ALL ALL = (ALL) NOPASSWD: ALL' >> /etc/sudoers
ENV USR_HOME /home/seluser
ENV SEL_HOME=${USR_HOME}
RUN mkdir -p ${SEL_HOME}

#-----------#
# Fix perms #
#-----------#
RUN  chown -R seluser:seluser ${SEL_HOME} \
  && chown -R seluser:seluser ${USR_HOME}

#=====================
# Use Normal User now
#=====================
USER seluser
WORKDIR ${SEL_HOME}

#===========
# Selenium 2
#===========
ENV SEL_MAJOR_MINOR_VER="2.53" \
    SEL_PATCH_LEVEL_VER="1"
RUN  mkdir -p ${SEL_HOME} \
  && export SELBASE="https://selenium-release.storage.googleapis.com" \
  && export SELPATH="${SEL_MAJOR_MINOR_VER}/selenium-server-standalone-${SEL_MAJOR_MINOR_VER}.${SEL_PATCH_LEVEL_VER}.jar" \
  && wget -nv ${SELBASE}/${SELPATH}

#==========
# Zalenium
#==========
ENV DOCKER_ALONGSIDE_DOCKER="true"
ENV ZAL_VER="0.5.0-SNAPSHOT"
ADD ./scripts/entry.sh /usr/bin/
ADD ./target/zalenium.sh ${SEL_HOME}/
ADD ./target/zalenium-${ZAL_VER}.jar ${SEL_HOME}/zalenium-${ZAL_VER}.jar
# https://github.com/zalando-incubator/zalenium/releases/download/v${ZAL_VER}/zalenium-release-v${ZAL_VER}.tar.gz

#-----------------#
# Fix perms again #
#-----------------#
RUN  sudo chown -R seluser:seluser ${SEL_HOME} \
  && sudo chown -R seluser:seluser ${USR_HOME} \
  && chmod +x ${SEL_HOME}/zalenium.sh

# IMPORTANT: Using the string form `CMD "entry.sh"` without
# brackets [] causes Docker to run your process
# And using `bash` which doesn’t handle signals properly
ENTRYPOINT ["entry.sh"]
