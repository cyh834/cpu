{ stdenv, fetchFromGitHub}:
stdenv.mkDerivation rec {
    # 指定包名和版本
    pname = "libnemu";
    version = "master";

    # 从 GitHub 下载源代码
    src = fetchFromGitHub {
        owner = "OpenXiangShan";
        repo = "NEMU";
        rev = version;
        fetchSubmodules = false;
        sha256 = "sha256-c/UG6aYYhKYdiJEzSu4V9TjLwX8eN2hp9lD53TFaEt0=";
    };

    patches = [
    ../patches/nemu/add_config.patch
    ];

    buildPhase = ''
        runHook preBuild
        export NEMU_HOME=$TMPDIR
        make riscv64-cyh-ref_defconfig
        make -j
        runHook postBuild
    ''; 

    installPhase = ''
        runHook preInstall
        mkdir -p $out/lib
        mv $TMPDIR/build/nemu-interpreter.so $out/lib/nemu-interpreter.so
        runHook postInstall
    '';
}
