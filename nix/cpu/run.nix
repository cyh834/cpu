{ python3
, writeShellApplication
, coreutils
, nix
, cpu
}:

let
  # 创建可执行包装脚本
  runScript = writeShellApplication {
    name = "cpu-run";
    runtimeInputs = [
      python3
      coreutils # 提供基本的Unix工具
      nix       # 需要nix命令行工具
      cpu.verilated # 依赖CPU仿真组件
    ];
    meta = {
      mainProgram = "cpu-run";
      description = "CPU仿真运行控制脚本";
    };
    text = ''
      # 自动处理参数并传递到Python脚本
      exec ${python3}/bin/python ${./../../script/run.py} \
        --nix-store-path ${cpu.verilated} \
        "$@"
    '';
  };
in
runScript