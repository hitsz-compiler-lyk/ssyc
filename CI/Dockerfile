FROM debian:11 as runner

WORKDIR /src

RUN sed -i 's/deb.debian.org/10.249.12.85/g' /etc/apt/sources.list

RUN sed -i 's|security.debian.org/debian-security|mirrors.ustc.edu.cn/debian-security|g' /etc/apt/sources.list

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
    openjdk-17-jdk-headless \
    gcc-arm-linux-gnueabihf \
    libc6-dev-armhf-cross \
    wget \
    && rm -rf /var/lib/apt/lists/*

COPY ./build.sh /build.sh

CMD ["bash", "/build.sh"]
