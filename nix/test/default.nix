{lib, newScope}:

lib.makeScope newScope (scope:{
  cputest = scope.callPackage ./nexus-am.nix { casePrefix = "cputest"; };
  amtest = scope.callPackage ./nexus-am.nix { casePrefix = "amtest"; caseName = "hello"; };
  riscv-tests = scope.callPackage ./riscv-tests.nix { };
})
