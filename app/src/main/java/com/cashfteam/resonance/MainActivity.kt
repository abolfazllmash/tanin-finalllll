package com.cashfteam.resonance

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cashfteam.resonance.databinding.ActivityMainBinding
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), GameListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var engine: GameEngine
    private lateinit var soundManager: SoundManager
    private lateinit var hapticsManager: Haptics
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        soundManager = SoundManager()
        hapticsManager = Haptics(this)

        engine = GameEngine().apply {
            sound = soundManager
            haptics = hapticsManager
            listener = this@MainActivity
            bestLevel = prefs.bestLevel
        }
        binding.gameView.engine = engine

        binding.btnPlay.setOnClickListener { startGame() }
        binding.btnPause.setOnClickListener { pauseGame() }
        binding.btnResume.setOnClickListener { resumeGame() }
        binding.btnMainMenu.setOnClickListener { showMenu() }
        binding.btnResultAction.setOnClickListener { continueGame() }
        binding.overlayResult.setOnClickListener { continueGame() }
        binding.btnSoundMenu.setOnClickListener { toggleSound() }
        binding.btnSoundPause.setOnClickListener { toggleSound() }

        updateSoundLabels()
        showMenu()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (engine.scene) {
                    Scene.PLAYING -> pauseGame()
                    Scene.PAUSED -> showMenu()
                    Scene.MENU -> finish()
                }
            }
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && engine.scene != Scene.MENU) enableImmersive()
    }

    private fun enableImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun disableImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    private fun startGame() {
        // Surface may not be sized yet on a very fast first tap; retry next frame.
        if (engine.W < 1f || engine.H < 1f) {
            binding.gameView.post { startGame() }
            return
        }
        engine.scene = Scene.PLAYING
        engine.newField(1)
        binding.overlayMenu.visibility = View.GONE
        binding.overlayResult.visibility = View.GONE
        binding.overlayPause.visibility = View.GONE
        binding.btnPause.visibility = View.VISIBLE
        enableImmersive()
    }

    private fun showMenu() {
        engine.scene = Scene.MENU
        binding.txtMenuBest.text = getString(R.string.best_level, Fa.num(engine.bestLevel))
        binding.overlayPause.visibility = View.GONE
        binding.overlayResult.visibility = View.GONE
        binding.btnPause.visibility = View.GONE
        binding.overlayMenu.visibility = View.VISIBLE
        disableImmersive()
    }

    private fun pauseGame() {
        if (engine.scene != Scene.PLAYING) return
        engine.scene = Scene.PAUSED
        binding.overlayPause.visibility = View.VISIBLE
    }

    private fun resumeGame() {
        if (engine.scene != Scene.PAUSED) return
        engine.scene = Scene.PLAYING
        binding.overlayPause.visibility = View.GONE
        enableImmersive()
    }

    private fun continueGame() {
        if (engine.scene != Scene.PLAYING || engine.round != RoundState.RESULT) return
        engine.continueAfterResult()
        binding.overlayResult.visibility = View.GONE
    }

    private fun toggleSound() {
        soundManager.muted = !soundManager.muted
        updateSoundLabels()
    }

    private fun updateSoundLabels() {
        val t = getString(if (soundManager.muted) R.string.sound_off else R.string.sound_on)
        binding.btnSoundMenu.text = t
        binding.btnSoundPause.text = t
    }

    // GameListener — invoked from the Choreographer loop on the main thread.
    override fun onRoundEnd(cleared: Boolean, ignited: Int, total: Int, score: Int) {
        val close = !cleared && (engine.target - ignited) <= max(1, (engine.target * GameConfig.CLOSE_FRACTION).roundToInt())
        val grand = engine.isGrand(engine.level)
        binding.txtResultTitle.text = getString(
            when {
                cleared && grand -> R.string.grand_cleared
                cleared -> R.string.level_cleared
                grand -> R.string.grand_failed
                close -> R.string.so_close
                else -> R.string.try_again
            }
        )
        binding.txtResultSub.text = getString(
            R.string.result_sub, Fa.num(ignited), Fa.num(total), Fa.num(score)
        )
        binding.btnResultAction.text = getString(
            when {
                cleared -> R.string.next_level
                close -> R.string.try_again
                else -> R.string.retry
            }
        )
        binding.overlayResult.visibility = View.VISIBLE
    }

    override fun onBestLevel(best: Int) {
        prefs.bestLevel = best
    }
}
