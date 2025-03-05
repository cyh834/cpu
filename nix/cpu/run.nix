{ python3
, writeShellApplication
}:

let
  # 创建可执行包装脚本
  runScript = writeShellApplication {
    name = "cpu-run";
    runtimeInputs = [
      python3
    ];
    meta = {
      mainProgram = "cpu-run";
      description = "CPU仿真运行控制脚本";
    };
    text = ''
      exec ${python3}/bin/python ${./../../script/run.py} "$@"
    '';
  };
in
runScript