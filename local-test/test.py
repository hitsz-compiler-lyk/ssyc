from time import sleep
from rich.console import Console
from typing import Callable
import os
import sys

console = Console()

def rel(*subs: str) -> str:
    return os.path.join(os.curdir, *subs)

exists = os.path.exists
echo = console.log

def exit_with(message: str):
    console.log(f'[red bold]{message}')
    exit(1)

def sh(command: str):
    if (ret := os.system(command)) != 0:
        exit_with(f'Command fail: {command} (return {ret})')

def fill_front_zero(num: int, target_length: int) -> str:
    return '0' * (target_length - len(str(num))) + str(num)

def fill_end_space(raw: str, target_length: int) -> str:
    return raw + ' ' * (target_length - len(raw))

def one_pass(status_message: str, from_suffix: str, to_suffix: str, show: bool=True):
    def decorator(func: Callable[[str, str], None]):
        def do(subdir: str):
            console.print(f"[cyan bold]================= begin: {status_message} =================")
            with console.status(f'[bold green]{status_message} on {subdir} ({from_suffix} -> {to_suffix})...') as status:
                files = os.listdir(rel('test-data', subdir))
                sources = sorted([s for s in files if s.endswith(from_suffix)])
                source_cnt = len(sources)
                total_len = len(str(source_cnt))

                for idx, filename in enumerate(sources):
                    full_src = rel('test-data', subdir, filename)
                    full_dst = rel('test-data', subdir, filename.removesuffix(from_suffix) + to_suffix)

                    func(full_src, full_dst)
                    if show:
                        console.log(f'Finish ({fill_front_zero(idx, total_len)}/{source_cnt}) {filename}')
            # console.clear()

        do.type = (from_suffix, to_suffix)
        return do
    return decorator


def build(subdir: str):
    with console.status('[bold green]Building ssyc...') as status:
        sh("java -jar bin/antlr4.jar -visitor -no-listener $(find src -name '*.g4')")
        echo('Finish: ANTLR Generation')

        sh("javac -encoding \"UTF-8\" -d target -cp 'lib/*' $(find src -name '*.java')")
        echo('Finish: Java compile')


def ssyc(option: str) -> Callable[[str, str], None]:
    def do(src: str, dst: str):
        result = os.system(f'java -Xss512m -cp "lib/*:target" top.origami404.ssyc.Main {option} {src} {dst} 2> ssyc.log')
        if result != 0:
            with open('ssyc.log', 'r') as f:
                for line in f.readlines():
                    print(line, end='')
            console.log(f'[red bold]Compiler failed on {src}')
            exit(1)
    return do

ssyc_llvm = one_pass('ssyc', '.sy', '.llvm')(ssyc('llvm'))

# Check that: https://stackoverflow.com/questions/62436694/why-cant-gcc-compile-assembly-produced-by-clang
clang_cmd = 'clang -I/usr/arm-linux-gnueabihf/include -no-integrated-as -target armv7-linux-gnueabihf -static -g0'

@one_pass('clang-emit-llvm', '.sy', '.llvm')
def clang_llvm(src: str, dst: str):
    new_src = pre_include(src)
    sh(f'{clang_cmd} -emit-llvm -S {new_src} -o {dst}')

@one_pass('llc', '.llvm', '.s')
def llc(src: str, dst: str):
    bc = src.removesuffix('.llvm') + '.bc'

    sh(f'llvm-as {src} -o {bc}')
    sh(f'llc -o {dst} {bc}')
    os.remove(bc)


ssyc_asm = one_pass('ssyc', '.sy', '.s')(ssyc('asm'))

def pre_include(source: str) -> str:
    lines = []

    # lines.append('extern "C" {\n')
    with open(rel('util', 'sylib.h'), 'r') as f:
        lines.extend(f.readlines())
    # lines.append("}\n")
    lines.append("//================== include end ==========================//\n")

    with open(source, 'r') as f:
        lines.extend(f.readlines())

    dst = source.removesuffix('.sy') + '.c'
    with open(dst, 'w') as f:
        f.writelines(lines)

    return dst

# @one_pass('gcc', '.sy', '.s')
# def gcc(src: str, dst: str):
#     new_src = pre_include(src)
#     sh(f'arm-linux-gnueabihf-gcc -std=gnu11 -march=armv7-a -static -g0 -S {new_src} -o {dst}')

@one_pass('clang', '.sy', '.s', show=False)
def clang(src: str, dst: str):
    new_src = pre_include(src)
    sh(f'{clang_cmd} -S {new_src} -o {dst} 2> /dev/null')

@one_pass('clang-O2', '.sy', '.s', show=False)
def clang_O2(src: str, dst: str):
    new_src = pre_include(src)
    sh(f'{clang_cmd} -O2 -S {new_src} -o {dst} 2> /dev/null')



@one_pass('gcc-as', '.s', '.exec', show=False)
def gcc_as(src: str, dst: str):
    sh(f'arm-linux-gnueabihf-gcc  -march=armv7-a -static -g {src} {rel("util", "libsysy.a")} -o {dst}')

@one_pass('run', '.exec', '.res', show=False)
def run(exec: str, result_file: str):
    base_name = exec.removesuffix('.exec')

    input_file = f'{base_name}.in'
    output_file = f'{base_name}.out'

    pgm_output = f'{base_name}.stdout'
    pgm_return = f'{base_name}.return'
    pgm_stderr = f'{base_name}.stderr'

    input_redir = f'< {input_file}' if exists(input_file) else ''

    sh(f'qemu-arm-static {exec} {input_redir} > {pgm_output} 2> {pgm_stderr}; echo "$?" > {pgm_return}')

    lines: list[bytes] = []
    with open(pgm_output, 'rb') as f:
        lines.extend(f.readlines())
    with open(pgm_return, 'rb') as f:
        lines.extend(f.readlines())
    for idx, line in enumerate(lines):
        if not line.endswith(b'\n'):
            lines[idx] = line + b'\n'
    with open(result_file, 'wb') as f:
        f.writelines(lines)


    nick_name = fill_end_space(os.path.split(base_name)[-1], 40)
    if os.system(f'diff {output_file} {result_file} > /dev/null') != 0:
        sh(f'difft {result_file} {output_file}')
        exit_with(f'[red bold]Fail [cyan]{nick_name} [red bold]: Wrong Answer')
    else:
        with open(pgm_stderr, 'r') as f:
            echo(f'[green bold]Pass [cyan]{nick_name} [green bold]: {f.readlines()}')

    os.remove(pgm_output)
    os.remove(pgm_return)
    os.remove(pgm_stderr)

def clear_up():
    with console.status('[green bold]Clearing...') as status:
        for subdir in sorted(os.listdir(rel('test-data'))):
            for file in sorted(os.listdir(rel('test-data', subdir))):
                full_filename = rel('test-data', subdir, file)
                if not (file.endswith('.sy') or file.endswith('.in') or file.endswith('.out')):
                    os.remove(full_filename)
                    # console.log(f'Removed {full_filename}')
                else:
                    # console.log(f'Skip {full_filename}')
                    pass
    console.clear()


def check_funcs(funcs):
    last_type = '.sy'
    for func in funcs:
        src_type, dst_type = func.type
        if src_type != last_type:
            exit_with('Passes type do NOT match')
        else:
            last_type = dst_type
    if last_type != '.s':
        exit_with('Last pass should return a .s file')

if __name__ == '__main__':
    try:
        pgm, subdir, test_item = sys.argv

        test_items = {
            'ssyc_llvm': [build, ssyc_llvm, llc],
            'ssyc_asm': [build, ssyc_asm],
            'clang': [clang],
            'clang_O2': [clang_O2]
        }
        funcs = test_items[test_item]
        # check_funcs(funcs)

        clear_up()
        for func in funcs:
            func(subdir)
        gcc_as(subdir)
        run(subdir)

    except ValueError:
        console.print('[white bold]Usage: ./m test <data-subdir> <test-item>')
        console.print('    <data-subdir>: ', sorted(os.listdir(rel('test-data'))))
        console.print('    <test-item>: ', ['ssyc_llvm', 'ssyc_asm', 'clang', 'clang_O2'])
        console.print('\nPassed: ', sys.argv)