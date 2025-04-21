{ fetchFromGitHub
, pkgs
, lib
}:

pkgs.pkgsCross.riscv64-embedded.stdenv.mkDerivation rec{
  name = "riscv-tests";

  src = fetchFromGitHub {
    owner = "riscv-software-src";
    repo = "riscv-tests";
    rev = "master";
    hash = "sha256-WEWh4kfLTsuTxv3iPLmfgV1u7zeQWRXOLYrMtbNDvJE=";
  };

  postUnpack = ''
    rm -rf $sourceRoot/env
    cp -r ${../../tests/riscv-test-env} $sourceRoot/env
  '';

  enableParallelBuilding = true;

  configureFlags = [
    "--prefix=${placeholder "out"}/riscv64-unknown-elf"
  ];

  buildPhase = "make RISCV_PREFIX=riscv64-none-elf-";

  installPhase = ''
    runHook preInstall
    make install
    mkdir -p $out/debug/
    cp debug/*.py $out/debug/
    mkdir -p $out/tests/riscv-tests
    cp -r $out/riscv64-unknown-elf/share/riscv-tests $out/tests
    rm -rf $out/riscv64-unknown-elf
    runHook postInstall
  '';
}
