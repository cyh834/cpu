use std::env;

fn main() {
  if cfg!(feature = "difftest") {
    //nemu
    //println!(
    //  "cargo:rustc-link-lib=static={}",
    //  env::var("REF_MODULE").unwrap_or_else(|_| "nemu".to_string())
    //);

    // spike
    //println!(
    //  "cargo::rustc-link-search=native={}",
    //  env::var("REF_MODULE_LIB_DIR").expect("REF_MODULE_LIB_DIR should be set")
    //);
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

    //println!("cargo::rerun-if-env-changed=REF_MODULE_LIB_DIR");
    //println!("cargo::rerun-if-env-changed=REF_MODULE_INTERFACES_LIB_DIR");

    //println!("cargo::rustc-link-lib=stdc++");
  }
}
