{ stdenv, fetchFromGitHub, make }:
stdenv.mkDerivation rec {
    pname = "libnemu";
    version = "master";
    nativeBuildInputs = [ make ];
    src = fetchFromGitHub {
        owner = "cyh834";
        repo = "NEMU";
        rev = version;
        sha256 = "";
    };
}
