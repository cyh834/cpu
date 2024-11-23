{ stdenv, fetchFromGitHub }:

stdenv.mkDerivation {
    name = "nexus-am";

    src = fetchFromGitHub {
        owner = "cyh834";
        repo = "nexus-am";
        hash = "";
    };

    buildPhase = ''
        mkdir -p $out

        export AM_HOME=$src
        pushd $src/tests/cputest
        make ARCH=riscv64-cyh LINUX_GNU_TOOLCHAIN=1
        cp -r build $out
        popd
    '';

}
