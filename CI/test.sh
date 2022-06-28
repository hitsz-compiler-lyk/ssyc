# current_time
ct=$(date +%m-%d_%H-%M-%S)
file_path="${PWD}/${ct}"

ED="\033[31;49m"
GREEN="\033[32;49m"
CYAN="\033[36;49m"
WHITE="\033[37;49m"
NORMAL="\033[0m"

print_info () {
    printf "Test ${WHITE}%-40s $2%s ${NORMAL}\n" "$1" "$3"
}

mkdir -p ${file_path}
tar -xf build.tar -C ${file_path}
cd ${file_path}


for kind in $(ls)
do
    echo "============================================="
    echo "               kind = ${kind}                "
    echo "---------------------------------------------"
    cd ${kind}
    for exec_file in $(ls *.exec)
    do
        # filename
        input=${exec_file/%exec/in}
        testout=${exec_file/%exec/testout}
        output=${exec_file/%exec/out}

        chmod +x $exec_file
        echo "Test: ${exec_file}"

        # input part gen
        input_part=""
        if [ -a ${input} ]
        then
            input_part=" < ${input}"
        fi

        # output part gen
        output_part=""
        if [ -a ${output} ]
        then
            output_part=" > ${testout}"
        fi

        bash -c "./${exec_file}${input_part}${output_part}"

        # test return value
        print_info ${input} ${CYAN} "return $?"

        # test output file
        if [ -a ${output} ]
        then
            diff ${testout} ${output} > /dev/null
            if [ $? == 0 ]
            then
                printf "${GREEN}output test pass!${NORMAL}"
            else
                printf "${RED}output test fail!${NORMAL}"
                difft ${testout} ${output}
            fi
        fi
        echo
    done
    echo "============================================="
    echo
    cd ..
done
mv ../build.tar .
