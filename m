#!/bin/bash
pushd () {
    command pushd "$@" > /dev/null
}

popd () {
    command popd "$@" > /dev/null
}

cleanup() {
    rm -r target
    mkdir target
}

compile() {
    # Generate lexer and parser
    java -jar bin/antlr4.jar $(find src -name '*.g4')
    
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
    java -cp "lib/*:target" top.origami404.ssyc.Main
}

run_jar() {
    java -jar target/ssyc.jar
}

subcommand=${1:-'full'}

case $subcommand in
    clean) cleanup ;;
    build) compile ;;
    run) run ;;
    jar) make_jar ;;
    jar-run) run_jar ;;
    full) cleanup && compile && echo 'Build finish.' && run ;;
esac