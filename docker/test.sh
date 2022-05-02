cd /src

RED=$(tput setaf 1)
GREEN=$(tput setaf 2)
CYAN=$(tput setaf 6)
WHITE=$(tput setaf 7)
NORMAL=$(tput sgr0)

print_info () {
    printf "Test ${WHITE}%-40s $2%s ${NORMAL}\n" "$1" "$3"
}

for input in $(ls test-data/asm-handmade/*.sy)
do
    sy_file="${PWD}/${input}"
    arm_file="${PWD}/${input/sy/s}"
    exec_file="${PWD}/${input/sy/exec}"
  
    ./m run asm ${sy_file} ${arm_file}
    if [ $? -ne 0 ]; then
        print_info ${input} ${RED} "ssyc FAIL"
        exit 1
    fi

    arm-linux-gnueabihf-gcc -march=armv7-a -static -g ${arm_file} -o ${exec_file}
    if [ $? -ne 0 ]; then
        print_info ${input} ${RED} "as FAIL"
        exit 1
    fi

    qemu-arm-static ${exec_file}
    print_info ${input} ${CYAN} "return $?"
done