import os
import sys
from typing import Tuple, List


def parse_args() -> Tuple[List[str], str]:
    if "/" in sys.argv[1]:
        testCase, testName = sys.argv[1].split("/")
        testpath= resolve_nix_path(f"\"#{testCase}\"")
        tests = [f for f in os.listdir(testpath) if f.endswith('.elf') and f.startswith(testName)]
        # 如果找到多个测试，只运行第一个
        if tests:
            tests = [tests[0]]
        else:
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

def nix_run(elfFilePath: str) -> int:
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
    ] + extra_args + [arg for arg in sys.argv[2:] if arg != "--trace"]
    cmd = " ".join(args)
    print(f"\033[1;36m{cmd}\033[0m")
    return os.system(cmd) >> 8  # 返回实际退出码

Error_CODE = {
    0: "\033[1;32mGoodTrap\033[0m",    # 亮绿色
    1: "\033[1;31mBadTrap\033[0m",     # 亮红色
    2: "\033[1;33mRunning\033[0m",     # 亮黄色
    3: "\033[1;35mTimeout\033[0m",     # 亮紫色
    4: "\033[1;36mUnknown\033[0m",     # 亮青色（原黄色改为更醒目的青色）
}

def run_test(tests: List[str], testpath: str):
    passed = []
    failed = []
    exit_codes = []
    for test in tests:
        print(f"\033[1;36mRunning: {test}\033[0m")
        nix_run(os.path.join(testpath, test))
        exit_code = int(open("exit_code.txt").read())
        os.remove("exit_code.txt")
        if exit_code == 0:
            passed.append(test)
            print(f"\033[1;32m✓ {test} PASSED\033[0m")
        else:
            failed.append(test)
            exit_codes.append(exit_code)
            print(f"\033[1;31m✗ {test} FAILED (code {exit_code})\033[0m")

    # 打印统计结果
    print(f"\n\033[1mTest Results:\033[0m")
    print(f"Total: {len(tests)}")
    print(f"\033[1;32mPassed: {len(passed)}\033[0m")
    print(f"\033[1;31mFailed: {len(failed)}\033[0m")
    
    if failed:
        print("\n\033[1;31mFailed Tests:\033[0m")
        for i, (test, code) in enumerate(zip(failed, exit_codes), 1):
            print(f"  \033[1;36m{i}.\033[0m \033[1;31m{test:<15}\033[0m - reason: {Error_CODE[code]}")

if __name__ == "__main__":
    tests, testpath = parse_args()
    run_test(tests, testpath)
