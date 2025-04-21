{ fetchFromGitHub
, pkgs
, lib
, casePrefix ? "cputest"
, caseName ? null
}:

pkgs.pkgsCross.riscv64-embedded.stdenv.mkDerivation rec{
  name = "nexus-am";

  src = fetchFromGitHub {
    owner = "cyh834";
    repo = "nexus-am";
    rev = "master";
    sha256 = "sha256-FIgTN3p9r03+vf5RxE9kRyL22FR2U9loh4lTAseVOAU=";
  };

  postPatch = ''
    # 替换 echo 命令
    find . -type f -exec sed -i 's|/bin/echo|echo|g' {} +
  '';

  nativeBuildInputs = [
    pkgs.pkgsCross.riscv64.buildPackages.gcc
  ];

  preBuild = ''
    export AM_HOME=$(pwd)
  '';

  makeFlags = [
    "-C tests/${casePrefix}"
    "${lib.optionalString (caseName != null) "ALL=${caseName}"}"
    "ARCH=riscv64-cyh"
    "${lib.optionalString (casePrefix == "amtest") "mainargs=${caseName}"}"
  ];

  installPhase = ''
    runHook preInstall
    mkdir -p $out/tests/${casePrefix}
    cp tests/${casePrefix}/build/*.bin $out/tests/${casePrefix}
    cp tests/${casePrefix}/build/*.elf $out/tests/${casePrefix}
    cp tests/${casePrefix}/build/*.txt $out/tests/${casePrefix}
    runHook postInstall
  '';
}
