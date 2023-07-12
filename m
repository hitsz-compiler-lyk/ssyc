#!/bin/bash
pushd () {
    command pushd "$@" > /dev/null
}

popd () {
    command popd "$@" > /dev/null
}

download_dependence() {
    [ ! -d lib ] && mkdir lib
    [ ! -d bin ] && mkdir bin

    wget 'https://repo1.maven.org/maven2/org/antlr/antlr4-runtime/4.8/antlr4-runtime-4.8.jar' \
        -O lib/antlr4-runtime-4.8.jar
    wget 'https://repo1.maven.org/maven2/org/antlr/antlr4-runtime/4.8/antlr4-runtime-4.8-sources.jar' \
        -O lib/antlr4-runtime-4.8-sources.jar
    wget 'https://repo1.maven.org/maven2/org/antlr/antlr4/4.8/antlr4-4.8-complete.jar' \
        -O bin/antlr4.jar
}

cleanup() {
    rm -r target
    mkdir target
}

compile() {
    # Generate lexer and parser
    java -jar bin/antlr4.jar -visitor -no-listener $(find src -name '*.g4')

    ANTLR_EXT_CACHE=$(find src -name '.antlr')
    test -d "$ANTLR_EXT_CACHE" && rm -r "$ANTLR_EXT_CACHE"

    # Compile to .class
    javac -encoding "UTF-8" -d target -cp 'lib/*' $(find src -name '*.java')
}

make_jar() {
    pushd target

    # Generate MANIFEST.MF
    mkdir META-INF
    touch META-INF/MANIFEST.MF

    echo 'Manifest-Version: 1.0' >> META-INF/MANIFEST.MF
    echo 'Main-Class: Main' >> META-INF/MANIFEST.MF
    echo 'Class-Path: . ../lib/antlr4-runtime-4.8.jar' >> META-INF/MANIFEST.MF

    # Make Jar
    jar -cvfm ssyc.jar META-INF/MANIFEST.MF *

    popd
}

run() {
    java -cp "lib/*:target" Main $@ -O2
}

run_jar() {
    java -jar target/ssyc.jar
}

build_test_image() {
machine_arch=`arch`
if [[ $machine_arch =~ "x86_64" ]];then
    docker build -f local-test/Dockerfile_x86 -t ssyc-test local-test
elif [[ $machine_arch =~ "aarch64" || $machine_arch =~ "arm64" ]];then
    docker build -f local-test/Dockerfile_arm64 -t ssyc-test local-test
else
    echo "build test image failed, unsupported arch:" $machine_arch
fi
}

run_test() {
    docker run --rm -it --user $(id -u):$(id -g) -e "TERM=xterm-256color" -v "$PWD:/src" ssyc-test $@
}

enter_test() {
    docker run --rm -it --user $(id -u):$(id -g) -e "TERM=xterm-256color" -v "$PWD:/src" --entrypoint /bin/bash ssyc-test
}

subcommand=${1:-'full'}

case $subcommand in
    install) download_dependence ;;
    clean) cleanup ;;
    build) compile ;;
    run) run ${@:2};;
    jar) make_jar ;;
    jar-run) run_jar ;;
    build_test) build_test_image ;;
    test) run_test ${@:2};;
    enter_test) enter_test ;;
    full) cleanup && compile && echo 'Build finish.' && run $@;;
esac