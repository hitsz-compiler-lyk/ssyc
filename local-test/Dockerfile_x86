FROM debian:11 as runner

WORKDIR /src

RUN sed -i 's|http://deb.debian.org|http://mirrors.sustech.edu.cn|g' /etc/apt/sources.list

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
    gcc-arm-linux-gnueabihf \
    g++-arm-linux-gnueabihf \
    libc6-dev-armhf-cross \
    qemu-user-static \
   # Only for this project, for manual debug
    gdb-multiarch \
   # Only for Java project
    openjdk-17-jdk \
 && rm -rf /var/lib/apt/lists/*

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
    python3 python3-pip llvm clang wget \
 && rm -rf /var/lib/apt/lists/* \
 && python3 -m pip install rich

RUN wget -O difft.tar.gz https://github.com/Wilfred/difftastic/releases/download/0.31.0/difft-x86_64-unknown-linux-gnu.tar.gz \
 && tar xvf difft.tar.gz -C /usr/bin/ \
 && rm difft.tar.gz

ENTRYPOINT [ "python3", "local-test/test.py" ]