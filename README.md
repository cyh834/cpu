# CPU

这是一个使用 Chisel 开发的 RISC-V CPU。

## 构建要求

- Nix

## 项目结构

- `cpu/`: CPU的Chisel源代码
- `cpuemu/`: DPI库的Rust源代码
- `elaborator/`: Chisel生成器源代码
- `configs/`: CPU和测试平台的默认配置,可由elaborator生成
- `nix/`: 整个编译流程的Nix构建脚本
- `build.sc & common.sc`: Scala构建脚本
- `flake.nix`: Nix搜索脚本的根目录

## 快速开始

### 安装 Nix

使用 [nix-installer](https://github.com/DeterminateSystems/nix-installer) 安装 Nix (会自动启用 flakes)
   ```bash
   curl --proto '=https' --tlsv1.2 -sSf -L https://install.determinate.systems/nix | sh -s -- install
   ```

### 使用 Nix 构建

生成Verilog
```bash
nix build '.#cpu.rtl'
```

生成测试用例
```bash
nix build '.#nexus-am'
```

使用Verilator仿真
```bash
nix develop '#test' -c python script/run.py nexus-am/dummy
```

使用Verilator生成波形
```bash
nix develop '#test' -c python script/run.py nexus-am/dummy --trace
```

### TODO

使用VCS仿真
```bash
nix run '.#cpu.vcs' --impure
```

使用VCS生成波形
```bash
nix run '.#cpu.vcs-trace' --impure
```


形式验证
```bash
nix build '.#cpu.jg-fpv' --impure
```

## 开发

### 格式化代码
```bash
# 格式化Nix代码
nix fmt

# 格式化Scala代码
nix develop -c bash -c 'mill -i cpu.reformat'

# 格式化Rust代码
nix develop -c bash -c 'cd cpuemu && cargo fmt'
```

### 更新依赖
```bash
# 更新nixpkgs
nix flake update

# 更新Chisel等依赖
cd nix/pkgs/dependencies
nix run '.#nvfetcher'

# 更新mill依赖
nix build '.#cpu.cpu-compiled.millDeps' --rebuild

# 更新Cargo依赖
nix develop -c bash -c 'cd cpuemu && cargo update'
```
最后可能要更新相应的Hash

## 参考

- [Chisel-nix](https://github.com/chipsalliance/chisel-nix)
- [T1](https://github.com/chipsalliance/t1)


