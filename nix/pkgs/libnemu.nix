libnemu = final.stdenv.mkDerivation {
  name = "libnemu";
  ## nix build failed，so download directly
  src = final.fetchurl {
    url = "https://raw.githubusercontent.com/cyh834/NEMU/master/artifact/riscv64-nemu-interpreter-so";
    sha256 = "sha256-A8b/3GTcWZjKXpo4xQ4jceyyVAENDw23EpI6OejuTiM=";
  };
  
  dontUnpack = true;
  installPhase = ''
    mkdir -p $out/lib
    cp $src $out/lib/libriscv64_nemu_interpreter.so
  '';
};
