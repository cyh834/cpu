{lib, newScope}:

lib.makeScope newScope (scope:
  cputest = scope.callPackage ./nexus-am.nix { casePrefix = "cputest"; };
  amtest = scope.callPackage ./nexus-am.nix { casePrefix = "amtest"; };

  run = scope.callPackage ./run.nix { };

)
