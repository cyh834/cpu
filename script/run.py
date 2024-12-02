import os
import sys
from typing import Tuple, List

def parse_args() -> Tuple[List[str], str]:
    if "/" in sys.argv[1]:
        testCase, testName = sys.argv[1].split("/")
        testpath= resolve_nix_path(f"\"#{testCase}\"")
        tests = [f for f in os.listdir(testpath) if f.endswith('.elf') and f.startswith(testName)]
        if not tests:
            print(f"\033[1;31m错误:在 {testpath} 中没有找到以 {testName} 开头的 .elf 文件\033[0m")
            sys.exit(1)
    else:
        testCase = sys.argv[1]
        testpath = resolve_nix_path(f"\"#{testCase}\"")
        tests = [f for f in os.listdir(testpath) if f.endswith('.elf')]
        if not tests:
            print(f"\033[1;31m错误:在 {testpath} 中没有找到 .elf 文件\033[0m")
            sys.exit(1)

    return tests, testpath

def resolve_nix_path(attr: str, extra_args: list = None) -> str:
    if extra_args is None:
        extra_args = []
    
    # 构建基本命令
    args = [
        "nix",
        "build",
        "--no-link",
        "--no-warn-dirty",
        "--print-out-paths",
        attr
    ] + extra_args
    
    # 将参数列表转换为命令字符串
    cmd = " ".join(args)
    print(f"\033[1;36m{cmd}\033[0m")
    
    # 执行命令并获取输出
    nix_path = os.popen(cmd).read().strip()
    if not os.path.exists(nix_path):
        print(f"\033[1;31m错误: 路径 {nix_path} 不存在\033[0m")
        sys.exit(1)

    test_path = os.path.join(nix_path, "test")
    if not os.path.exists(test_path):
        print(f"\033[1;31m错误: 路径 {test_path} 不存在\033[0m")
        sys.exit(1)

    return test_path

def nix_run(elfFilePath: str):
    has_trace = "--trace" in sys.argv
    if has_trace:
        attr = "\".#cpu.verilated-trace\""
        extra_args = ["+dump-start=0", "+dump-end=1000000", "+wave-path=wave.fst"]
    else:
        attr = "\".#cpu.verilated\""

    args = [
        "nix",
        "run",
        attr,
        "--no-warn-dirty",
        "--",
        f"+elf-file={elfFilePath}"
    ] + extra_args
    cmd = " ".join(args)
    print(f"\033[1;36m{cmd}\033[0m")
    os.system(cmd)

def run_test(tests: List[str], testpath: str):
    for test in tests:
        print(f"\033[1;36mRunning: {test}\033[0m")

        nix_run(os.path.join(testpath, test))

if __name__ == "__main__":
    tests, testpath = parse_args()
    run_test(tests, testpath)
