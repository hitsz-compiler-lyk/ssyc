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

    wget https://repo1.maven.org/maven2/org/antlr/antlr4-runtime/4.9.3/antlr4-runtime-4.9.3-sources.jar \
        -O lib/antlr4-runtime-4.9.3-sources.jar
    wget https://repo1.maven.org/maven2/org/antlr/antlr4-runtime/4.9.3/antlr4-runtime-4.9.3.jar \
        -O lib/antlr4-runtime-4.9.3.jar
    wget https://repo1.maven.org/maven2/org/antlr/antlr4/4.9.3/antlr4-4.9.3-complete.jar \
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
    javac -d target -cp 'lib/*' $(find src -name '*.java')
}

make_jar() {
    pushd target

    # Generate MANIFEST.MF
    mkdir META-INF
    touch META-INF/MANIFEST.MF

    echo 'Manifest-Version: 1.0' >> META-INF/MANIFEST.MF
    echo 'Main-Class: top.origami404.ssyc.Main' >> META-INF/MANIFEST.MF
    echo 'Class-Path: . ../lib/antlr-runtime-3.5.2.jar' >> META-INF/MANIFEST.MF

    # Make Jar
    jar -cvfm ssyc.jar META-INF/MANIFEST.MF *

    popd
}

run() {
    java -cp "lib/*:target" top.origami404.ssyc.Main $@
}

run_jar() {
    java -jar target/ssyc.jar
}

build_test_image() {
    docker build docker -t ssyc-test:v1
}

run_test() {
    compile
    docker run --rm -it -v "$PWD:/src" ssyc-test:v1
}

subcommand=${1:-'full'}

case $subcommand in
    install) download_dependence ;;
    clean) cleanup ;;
    build) compile ;;
    run) run $@;;
    jar) make_jar ;;
    jar-run) run_jar ;;
    build_test) build_test_image ;;
    test) run_test ;;
    full) cleanup && compile && echo 'Build finish.' && run $@;;
esac