# SPDX-License-Identifier: Apache-2.0
# SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>

{ lib, stdenv, rtl, verilator, zlib, dpi-lib, thread-num ? 1, libnemu }:
let vName = "V${rtl.target}";
in stdenv.mkDerivation {
  name = "verilated";

  src = rtl;

  nativeBuildInputs = [ verilator ];

  # zlib is required for Rust to link against?
  # IIRC: zlib is required for 
  propagatedBuildInputs = [ zlib ];

  passthru = {
    inherit dpi-lib;
    inherit (rtl) target;
  };

  meta.mainProgram = vName;

  buildPhase = ''
    runHook preBuild

    echo "[nix] running verilator"
    echo `ls`
    verilator \
      ${lib.optionalString dpi-lib.enable-trace "--trace-fst"} \
      --timing \
      --threads ${toString thread-num} \
      -O1 \
      --main \
      --exe \
      --cc -f filelist.f --top ${rtl.target} \
      ${dpi-lib}/lib/${dpi-lib.libOutName} \
      ${libnemu}/lib/libnemu.so

    echo "[nix] building verilated C lib"

    # backup srcs
    mkdir -p $out/share
    cp -r obj_dir $out/share/verilated_src

    # We can't use -C here because the Makefile is generated with relative path
    cd obj_dir
    make -j "$NIX_BUILD_CORES" -f ${vName}.mk ${vName}

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out/{include,lib,bin}
    cp *.h $out/include
    cp *.a $out/lib
    cp ${vName} $out/bin

    runHook postInstall
  '';

  # nix fortify hardening add `-O2` gcc flag,
  # we'd like verilator to controll optimization flags, so disable it.
  # `-O2` will make gcc build time in verilating extremely long
  hardeningDisable = [ "fortify" ];
}
