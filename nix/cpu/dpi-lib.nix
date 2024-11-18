# SPDX-License-Identifier: Apache-2.0
# SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>

{ lib
, rustPlatform
, tbConfig
, dpiLibName
, sv2023 ? true
, vpi ? false
, enable-trace ? false
, difftest ? true
, ref-module ? null
, ref-module-interfaces ? null
, timescale ? 1
}:

rustPlatform.buildRustPackage rec {
  name = "dpi-lib";
  src = ./../../${dpiLibName};
  cargoHash = "sha256-zFowBTglrJhdHe2H+QrDR6xPF/QFNfqHtY6JFT6ku2Q=";
  buildFeatures = lib.optionals sv2023 [ "sv2023" ]
    ++ lib.optionals vpi [ "vpi" ] ++ lib.optionals enable-trace [ "trace" ]
    ++ lib.optionals difftest [ "difftest" ];

  env = {
    REF_MODULE = ref-module;
    ## REF_MODULE_LIB_DIR = lib.optionalString (ref-module != null) "${ref-module}/lib";
    ## REF_MODULE_INTERFACES_LIB_DIR = lib.optionalString (ref-module-interfaces != null) "${ref-module-interfaces}/lib";
    DESIGN_TIMEOUT = tbConfig.timeout;
    CLOCK_FLIP_TIME = tbConfig.testVerbatimParameter.clockFlipTick * timescale;
  };

  passthru = {
    inherit enable-trace;
    inherit env;
    libOutName = "lib${dpiLibName}.a";
  };
}
