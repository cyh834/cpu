use std::env;
use std::process::Command;

fn main() {
    let status = Command::new("make")
        .arg("-C")
        .arg(env::var("NEMU_HOME").expect("NEMU_HOME should be set"))
        .arg("ISA=riscv64 riscv64-cyh-ref_defconfig")
        .status()
        .expect("Failed to execute make");

    if !status.success() {
        panic!("make failed");
    }

    println!(
        "cargo:rustc-link-search=native={}/build",
        env::var("NEMU_HOME").expect("NEMU_HOME should be set")
    );

    println!("cargo:rustc-link-lib=riscv64-nemu-interpreter-so");

    println!("cargo:rerun-if-env-changed=NEMU_HOME");
}