package com.wavesplatform.db

import java.nio.file.Files

import com.typesafe.config.ConfigFactory
import com.wavesplatform.database.LevelDBWriter
import com.wavesplatform.history.Domain
import com.wavesplatform.settings.{FunctionalitySettings, WavesSettings, loadConfig}
import com.wavesplatform.state.{Blockchain, BlockchainUpdaterImpl}
import com.wavesplatform.{NTPTime, TestHelpers}
import org.scalatest.Suite

trait WithState {
  protected def withState[A](fs: FunctionalitySettings)(f: Blockchain => A): A = {
    val path = Files.createTempDirectory("leveldb-test")
    val db   = openDB(path.toAbsolutePath.toString)
    try f(new LevelDBWriter(db, fs, 100000, 2000, 120 * 60 * 1000))
    finally {
      db.close()
      TestHelpers.deleteRecursively(path)
    }
  }

  def withStateAndHistory(fs: FunctionalitySettings)(test: Blockchain => Any): Unit = withState(fs)(test)
}

trait WithDomain extends WithState with NTPTime {
  _: Suite =>

  def withDomain[A](settings: WavesSettings = WavesSettings.fromConfig(loadConfig(ConfigFactory.load())))(test: Domain => A): A = {
    try withState(settings.blockchainSettings.functionalitySettings) { blockchain =>
      val bcu = new BlockchainUpdaterImpl(blockchain, settings, ntpTime)
      try test(Domain(bcu))
      finally bcu.shutdown()
    } finally {}
  }
}
