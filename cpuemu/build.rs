use std::env;

fn main() {
  if cfg!(feature = "difftest") {
    println!("cargo::rustc-link-search=native={}", env::var("REF_MODULE_LIB_DIR").expect("REF_MODULE_LIB_DIR should be set"));
    println!("cargo::rustc-link-lib=dylib=riscv64-nemu-interpreter");

    println!("cargo::rerun-if-env-changed=REF_MODULE_LIB");
    println!("cargo::rustc-link-lib=stdc++");

    // spike
    //println!("cargo::rustc-link-lib=static=riscv");
    //println!("cargo::rustc-link-lib=static=softfloat");
    //println!("cargo::rustc-link-lib=static=disasm");
    //println!("cargo::rustc-link-lib=static=fesvr");
    //println!("cargo::rustc-link-lib=static=fdt");

    //println!(
    //  "cargo::rustc-link-search=native={}",
    //  env::var("REF_MODULE_INTERFACES_LIB_DIR")
    //    .expect("REF_MODULE_INTERFACES_LIB_DIR should be set")
    //);
    //println!("cargo::rustc-link-lib=static=spike_interfaces");

    //println!("cargo::rerun-if-env-changed=REF_MODULE_INTERFACES_LIB_DIR");

  }
}
//-L" "/nix/store/1csr19kp8kmjmljqs5ndr77ari8zcwc2-libnemu-unstable-2024-11-19/lib"
//"-lriscv64-nemu-interpreter"