{ pkgs
, lib
, newScope
, fetchMillDeps
, publishMillJar
, git
, makeSetupHook
, writeText
, ...
}:
let
  dependencies = pkgs.callPackage ./_sources/generated.nix { };
in
lib.makeScope newScope (scope: {
  chisel =
    let
      chiselDeps = fetchMillDeps {
        name = "chisel";
        src = dependencies.chisel.src;
        fetchTargets = [ "unipublish" ];
        nativeBuildInputs = [
          git
        ];
        millDepsHash = "sha256-TmjZTFDXkWkQJTj4U9zZW6VxcJWNyHBuKL8op/2u/LI=";
      };
    in
    publishMillJar {
      name = "chisel";
      src = dependencies.chisel.src;
      publishTargets = [ "unipublish" ];
      buildInputs = [ chiselDeps.setupHook ];
      nativeBuildInputs = [
        git
      ];
      passthru = {
        inherit chiselDeps;
      };
    };

  rvdecoderdb =
    let
      rvdecoderdbDeps = fetchMillDeps {
        name = "rvdecoderdb";
        src = dependencies.rvdecoderdb.src;
        fetchTargets = [ "rvdecoderdb.jvm" ];
        nativeBuildInputs = [
          git
        ];
        millDepsHash = "sha256-++tktP3OYw7HDMRerjNk7L87GUzXu69iFUdSilCnUF4=";
      };
    in
    publishMillJar {
      name = "rvdecoderdb-snapshot";
      src = dependencies.rvdecoderdb.src;
      publishTargets = [ "rvdecoderdb.jvm" ];
      buildInputs = [ rvdecoderdbDeps.setupHook ];
      nativeBuildInputs = [
        git
      ];
      passthru = {
        inherit rvdecoderdbDeps;
      };
    };
  
  riscv-opcodes = makeSetupHook { name = "setup-riscv-opcodes-src"; } (writeText "setup-riscv-opcodes-src.sh" ''
    setupRiscvOpcodes() {
      mkdir -p dependencies
      ln -sfT "${dependencies.riscv-opcodes.src}" "dependencies/riscv-opcodes"
    }
    prePatchHooks+=(setupRiscvOpcodes)
  '');
})
