{ python3 }:

python3.pkgs.buildPythonPackage rec {
  pname = "script";
  version = "0.1.0";

  src = [
    ./../../script/run.py
  ];
}
