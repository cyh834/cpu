# This file was generated by nvfetcher, please do not modify it manually.
{ fetchgit, fetchurl, fetchFromGitHub, dockerTools }:
{
  chisel = {
    pname = "chisel";
    version = "0e191719107b2d9ee9a98170a6c3419784152307";
    src = fetchFromGitHub {
      owner = "chipsalliance";
      repo = "chisel";
      rev = "0e191719107b2d9ee9a98170a6c3419784152307";
      fetchSubmodules = false;
      sha256 = "sha256-en6BdYoZwM250tnoOtX62Nd7ovxDtM/5ymi0Y2WYBDc=";
    };
    date = "2024-11-08";
  };
  riscv-opcodes = {
    pname = "riscv-opcodes";
    version = "2af964acf2425f6a9f7fd51187799640549f4df3";
    src = fetchFromGitHub {
      owner = "riscv";
      repo = "riscv-opcodes";
      rev = "2af964acf2425f6a9f7fd51187799640549f4df3";
      fetchSubmodules = false;
      sha256 = "sha256-qw/RFo3be3i8XqesFU+dx1Qf8LyGPFjM/PqMenXJCuQ=";
    };
    date = "2024-11-08";
  };
  rvdecoderdb = {
    pname = "rvdecoderdb";
    version = "0879de48edbd738ce390e2327eae5c23f7f33778";
    src = fetchFromGitHub {
      owner = "sequencer";
      repo = "rvdecoderdb";
      rev = "0879de48edbd738ce390e2327eae5c23f7f33778";
      fetchSubmodules = false;
      sha256 = "sha256-mWKxI/IP5VrZuXQF3Wf4+m75ccddcGe4DPhcz8AvouA=";
    };
    date = "2024-10-10";
  };
}
