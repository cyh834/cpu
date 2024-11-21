{ stdenv, fetchurl }:

stdenv.mkDerivation {
  version = "unstable-2024-11-19";
  pname = "libnemu";
  ## nix build failed，so download directly
  src = fetchurl {
    url = "https://raw.githubusercontent.com/cyh834/NEMU/master/artifact/riscv64-nemu-interpreter-so";
    sha256 = "sha256-A8b/3GTcWZjKXpo4xQ4jceyyVAENDw23EpI6OejuTiM=";
  };
  dontUnpack = true;
  
  installPhase = ''
    runHook preInstall
    mkdir -p $out/lib
    cp $src $out/lib/libriscv64-nemu-interpreter.so
    runHook postInstall
  '';

}
