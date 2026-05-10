package com.sextant.app

import android.app.Application
import com.sextant.app.data.SextantRepository

class SextantApplication : Application() {
  lateinit var repository: SextantRepository
    private set

  override fun onCreate() {
    super.onCreate()
    repository = SextantRepository(this)
  }
}
