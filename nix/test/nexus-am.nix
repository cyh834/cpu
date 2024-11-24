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
        sha256 = "sha256-DQKpyfo67oUYjEnfS4BV6OCFuXD7Z6BuUO7O7FalohY=";
    };

    postPatch = ''
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
        "${if caseName != null then "ALL=${caseName}" else ""}"
        "ARCH=riscv64-cyh"
    ];

    installPhase = ''
        runHook preInstall
        mkdir -p $out/test
        cp tests/${casePrefix}/build/*.bin $out/test
        cp tests/${casePrefix}/build/*.elf $out/test
        cp tests/${casePrefix}/build/*.txt $out/test
        runHook postInstall
    '';
}
