FROM debian:11 as runner

WORKDIR /src

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
    gcc-arm-linux-gnueabihf \
    libc6-dev-armhf-cross \
    qemu-user-static \
   # Only for this project, for manual debug
    gdb-multiarch \
   # Only for Java project
    openjdk-17-jdk \
 && rm -rf /var/lib/apt/lists/*

COPY ./test.sh /test.sh

CMD ["bash", "/test.sh"]