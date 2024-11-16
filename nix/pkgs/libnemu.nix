{ stdenv, fetchFromGitHub, bison, which, pkg-config, readline, SDL2, zstd, gcc, gpp, gdb, flex, ccache}:

stdenv.mkDerivation rec {
    # 指定包名和版本
    pname = "libnemu";
    version = "ec9e1ca7627dd35a7012ed019bd40e5ee50e567b";

    # 从 GitHub 下载源代码
    #src = fetchFromGitHub {
    #    owner = "cyh834";
    #    repo = "NEMU";
    #    rev = version;
    #    sha256 = "sha256-zbyQkGW+WzyVLEpJRnKxD5pqz/nFsVMMS8bMnTNYD6U=";
    # };
    src = ../../NEMU;

    # 应用补丁
    # Patches = [
    # ../patches/nemu/add_config.patch
    # ];

    # 添加构建依赖
    nativeBuildInputs = [ bison which pkg-config flex];
    buildInputs = [ readline SDL2 zstd gcc gpp gdb ccache];

    buildPhase = ''
        runHook preBuild
        export NEMU_HOME=$PWD
        
        make riscv64-cyh-ref_defconfig
        make -j
        runHook postBuild
    ''; 

    installPhase = ''
        runHook preInstall
        mkdir -p $out/lib
        mv build/nemu-interpreter.so $out/lib/nemu-interpreter.so
        runHook postInstall
    '';
}
