# SPDX-License-Identifier: Apache-2.0
# SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>

{ lib
, rustPlatform
, tbConfig
, dpiLibName
, sv2023 ? true
, vpi ? false
, enable-trace ? false
, timescale ? 1
}:

rustPlatform.buildRustPackage rec {
  name = "dpi-lib";
  src = ./../../${dpiLibName};
  cargoHash = "sha256-AjL+iyigdG8Ah6SS8m5tnvoDb7D6oFjOTWw3MU+Wq8k=";
  buildFeatures = lib.optionals sv2023 [ "sv2023" ]
    ++ lib.optionals vpi [ "vpi" ] ++ lib.optionals enable-trace [ "trace" ];

  env = {
    DESIGN_TIMEOUT = tbConfig.timeout;
    CLOCK_FLIP_TIME = tbConfig.testVerbatimParameter.clockFlipTick * timescale;
    NEMU_HOME = tbConfig.nemuHome or (throw "tbConfig.nemuHome must be set");
  };

  passthru = {
    inherit enable-trace;
    inherit env;
    libOutName = "lib${dpiLibName}.a";
  };
}
