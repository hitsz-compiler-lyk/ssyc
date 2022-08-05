#!/usr/bin/python
from typing import Iterable
import os
import os.path as op

def find(path: str) -> Iterable[str]:
    for filename in os.listdir(path):
        file_path = op.join(path, filename)
        if op.isdir(file_path):
            yield from find(file_path)
        else:
            yield file_path


def lines(file_name: str) -> Iterable[tuple[int, str]]:
    with open(file_name, 'r') as f:
        for no, line in enumerate(f.readlines()):
            yield no + 1, line

if __name__ == '__main__':
    try:
        import sys
        _, lineNoStr = sys.argv
    except:
        print('Usage: find_exit_code <exit-code>')
        exit(1)

    lineNo = int(lineNoStr)

    def is_log_ensure(no: int, line: str) -> bool:
        return no % 128 == lineNo and line.find('Log.ensure') != -1

    def is_ir_verify(no: int, line: str) -> bool:
        return 128 + (no % 128) == lineNo \
            and (line.find('IRVerifyException') != -1 or line.find('ensure') != -1)

    pred = is_log_ensure if lineNo < 128 else is_ir_verify

    for file in find('src'):
        for no, line in lines(file):
            if pred(no, line):
                print(f'Found on {file}:{no} : {line}')