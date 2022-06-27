# install requirement
./m install
./m build

for kind in $(ls test-data)
do
    for input in $(ls test-data/$kind/*.sy)
    do
        sy_file="${PWD}/${input}"
        arm_file="${PWD}/${input/%sy/s}"
        exec_file="${PWD}/${input/%sy/exec}"

        ./m run asm ${sy_file} ${arm_file}
        arm-linux-gnueabihf-gcc -march=armv7-a -static -g ${arm_file} -o ${exec_file}

    done
done

cd test-data
tar -cf ../build.tar .
