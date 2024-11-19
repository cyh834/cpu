use plusarg::PlusArgMatcher;
use std::{
  //fs,
  //path::{Path, PathBuf},
  path::PathBuf,
};
use tracing::Level;
use tracing_subscriber::{EnvFilter, FmtSubscriber};

pub mod dpi;
pub mod drive;
pub mod plusarg;
pub mod bus;
pub mod ref_module;

pub fn get_t() -> u64 {
  0
}

pub(crate) struct SimArgs {
  /// Path to the ELF file
  pub elf_file: PathBuf,

  /// Path to the log file
  pub log_file: Option<PathBuf>,

  pub log_level: String,

  // ISA config, no use
  pub set: String,
  pub lvl: String,

  #[cfg(feature = "trace")]
  pub wave_path: String,
  #[cfg(feature = "trace")]
  pub dump_start: u64,
  #[cfg(feature = "trace")]
  pub dump_end: u64,
}

impl SimArgs {
  pub fn setup_logger(&self) -> Result<(), Box<dyn std::error::Error>> {
    let log_level: Level = self.log_level.parse()?;
    let global_logger = FmtSubscriber::builder()
      .with_env_filter(EnvFilter::from_default_env())
      .with_max_level(log_level)
      .without_time()
      .with_target(false)
      .with_ansi(true)
      .compact()
      .finish();
    tracing::subscriber::set_global_default(global_logger)
      .expect("internal error: fail to setup log subscriber");
    Ok(())
  }

  pub fn from_plusargs(matcher: &PlusArgMatcher) -> Self {
    Self {
      elf_file: matcher.match_("elf-file").into(),
      log_file: matcher.try_match("log-file").map(PathBuf::from),
      log_level: matcher.try_match("log-level").unwrap_or("info").into(),
      set: matcher.try_match("set").unwrap_or("rv64imafdc").into(),
      lvl: matcher.try_match("lvl").unwrap_or("u").into(),
      #[cfg(feature = "trace")]
      dump_start: matcher.try_match("dump-start").unwrap_or("0").parse().unwrap(),
      #[cfg(feature = "trace")]
      dump_end: matcher.try_match("dump-end").unwrap_or("0").parse().unwrap(),
      #[cfg(feature = "trace")]
      wave_path: matcher.match_("wave-path").into(),
    }
  }
}
