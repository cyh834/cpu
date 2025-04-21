{ fetchFromGitHub
, pkgs
, lib
}:

pkgs.pkgsCross.riscv64-embedded.stdenv.mkDerivation rec{
  name = "riscv-tests";
  version = "7878085d2546af0eb7af72a1df00996d5d8c43fb";
  src = fetchFromGitHub {
    owner = "riscv-software-src";
      repo = "riscv-tests";
      rev = "${version}";
      hash = "sha256-CruSrXVO5Qlk63HPBVbwzl/RdxAAl2bknWawDHJwEKY=";
    };

    postUnpack = ''
      rm -rf $sourceRoot/env
      cp -r ${../tests/riscv-test-env} $sourceRoot/env
    '';

    enableParallelBuilding = true;

    configureFlags = [
      # to match rocket-tools path
      "--prefix=${placeholder "out"}/riscv64-unknown-elf"
    ];
    buildPhase = "make RISCV_PREFIX=riscv64-none-elf-";
    installPhase = ''
      runHook preInstall
      make install
      mkdir -p $out/debug/
      cp debug/*.py $out/debug/
      runHook postInstall
    '';
}
