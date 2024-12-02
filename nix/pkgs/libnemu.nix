{ stdenv, fetchurl }:

stdenv.mkDerivation {
  version = "master";
  pname = "libnemu";
  ## nix build failed，so download directly
  src = fetchurl {
    url = "https://raw.githubusercontent.com/cyh834/NEMU/master/artifact/riscv64-nemu-interpreter-so";
    sha256 = "sha256-siPkuSj6JUEzJkd5itK7HLJ33f8/s9Sa0Io4TBwgZQk=";
  };
  dontUnpack = true;

  installPhase = ''
    runHook preInstall
    mkdir -p $out/lib
    cp $src $out/lib/libnemu.so
    runHook postInstall
  '';
}
