FROM ubuntu:24.04

ENV TZ=Europe/Amsterdam
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

RUN apt-get update && apt-get upgrade -y
RUN apt-get install -y build-essential git gcc pkg-config glib-2.0 libglib2.0-dev libsdl1.2-dev libaio-dev libcap-dev libattr1-dev libpixman-1-dev

RUN git clone --branch master --depth 1 git://git.qemu.org/qemu.git && \
    cd qemu && \
    git checkout c25df57ae8f9fe1c72eee2dab37d76d904ac382e && \
    git submodule update --init --recursive

WORKDIR /qemu

COPY build.sh .

ENV OUTPUT_DIR="/output"

ENTRYPOINT "/bin/bash" "build.sh" ${OUTPUT_DIR}