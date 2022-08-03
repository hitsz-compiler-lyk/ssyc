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
    echo 'Class-Path: . ../lib/antlr-runtime-3.5.2.jar' >> META-INF/MANIFEST.MF

    # Make Jar
    jar -cvfm ssyc.jar META-INF/MANIFEST.MF *

    popd
}

run() {
    java -cp "lib/*:target" Main $@
}

run_jar() {
    java -jar target/ssyc.jar
}

build_test_image() {
    docker build local-test -t ssyc-test
}

run_test() {
    docker run --rm -it --user $(id -u):$(id -g) -e "TERM=xterm-256color" -v "$PWD:/src" ssyc-test $@
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
    full) cleanup && compile && echo 'Build finish.' && run $@;;
esac