# SPDX-License-Identifier: Apache-2.0
# SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>

{ lib, newScope, libspike, libspike_interfaces, libnemu }:
lib.makeScope newScope (scope:
let
  designTarget = "CPU";
  tbTarget = "CPUTestBench";
  formalTarget = "CPUFormal";
  dpiLibName = "cpuemu";
in
{
  # RTL
  cpu-compiled = scope.callPackage ./cpu.nix { target = designTarget; };
  elaborate = scope.callPackage ./elaborate.nix {
    elaborator = scope.cpu-compiled.elaborator;
  };
  mlirbc = scope.callPackage ./mlirbc.nix { };
  rtl = scope.callPackage ./rtl.nix { };

  # Testbench
  tb-compiled = scope.callPackage ./cpu.nix { target = tbTarget; };
  tb-elaborate = scope.callPackage ./elaborate.nix {
    elaborator = scope.tb-compiled.elaborator;
  };
  tb-mlirbc =
    scope.callPackage ./mlirbc.nix { elaborate = scope.tb-elaborate; };
  tb-rtl = scope.callPackage ./rtl.nix { mlirbc = scope.tb-mlirbc;};
  tb-dpi-lib = scope.callPackage ./dpi-lib.nix {
    inherit dpiLibName;
    ref-module = libnemu;
  };

  verilated = scope.callPackage ./verilated.nix {
    rtl = scope.tb-rtl.override {
      enable-layers =
        [ "Verification" "Verification.Assert" ];
    };
    dpi-lib = scope.tb-dpi-lib;
  };
  verilated-trace = scope.verilated.override {
    dpi-lib = scope.verilated.dpi-lib.override { enable-trace = true; };
  };

  run = scope.callPackage ./run.nix { };

  vcs = scope.callPackage ./vcs.nix {
    dpi-lib = scope.tb-dpi-lib.override {
      sv2023 = false;
      vpi = true;
      timescale = 1000;
    };
    rtl = scope.tb-rtl.override {
      enable-layers =
        [ "Verification" "Verification.Assert" "Verification.Cover" ];
    };
  };
  vcs-trace = scope.vcs.override {
    dpi-lib = scope.vcs.dpi-lib.override { enable-trace = true; };
  };

  # Formal
  formal-compiled = scope.callPackage ./cpu.nix { target = formalTarget; };
  formal-elaborate = scope.callPackage ./elaborate.nix {
    elaborator = scope.formal-compiled.elaborator;
  };
  formal-mlirbc =
    scope.callPackage ./mlirbc.nix { elaborate = scope.formal-elaborate; };
  formal-rtl = scope.callPackage ./rtl.nix {
    mlirbc = scope.formal-mlirbc;
    enable-layers = [ "Verification" "Verification.Assume" "Verification.Assert" "Verification.Cover" ];
  };
  jg-fpv = scope.callPackage ./jg-fpv.nix {
    rtl = scope.formal-rtl;
  };

  # TODO: designConfig should be read from OM
  tbConfig = with builtins;
    fromJSON (readFile ./../../configs/${tbTarget}.json);

})

