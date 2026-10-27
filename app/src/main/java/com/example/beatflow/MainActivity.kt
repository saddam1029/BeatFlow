package com.example.beatflow

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.beatflow.data.model.HomeTab
import com.example.beatflow.data.model.Song
import com.example.beatflow.ui.home.HomeViewModel
import com.example.beatflow.ui.home.SongAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var songAdapter: SongAdapter

    // UI Components
    private lateinit var toolbarHome: MaterialToolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var rvSongs: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var layoutMiniPlayer: View
    private lateinit var ivMiniPlayerArt: ImageView
    private lateinit var tvMiniPlayerTitle: TextView
    private lateinit var tvMiniPlayerArtist: TextView
    private lateinit var btnMiniPlayerPlay: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        initViews()
        applyWindowInsets()
        setupToolbar()
        setupTabs()
        setupRecyclerView()
        setupMiniPlayer()
        observeViewModel()
    }

    private fun initViews() {
        toolbarHome = findViewById(R.id.toolbarHome)
        tabLayout = findViewById(R.id.tabLayout)
        rvSongs = findViewById(R.id.rvSongs)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        layoutMiniPlayer = findViewById(R.id.layoutMiniPlayer)
        ivMiniPlayerArt = findViewById(R.id.ivMiniPlayerArt)
        tvMiniPlayerTitle = findViewById(R.id.tvMiniPlayerTitle)
        tvMiniPlayerArtist = findViewById(R.id.tvMiniPlayerArtist)
        btnMiniPlayerPlay = findViewById(R.id.btnMiniPlayerPlay)
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupToolbar() {
        toolbarHome.title = getString(R.string.home_title)
    }

    private fun setupTabs() {
        tabLayout.removeAllTabs()
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_online_songs))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_device_songs))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> viewModel.selectTab(HomeTab.ONLINE)
                    1 -> viewModel.selectTab(HomeTab.DEVICE)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onSongClick = { song -> viewModel.playSong(song) },
            onPlayClick = { song -> viewModel.playSong(song) },
            onOverflowClick = { anchorView, song -> showSongOverflowMenu(anchorView, song) }
        )

        rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = songAdapter
            itemAnimator = null // smoother item updates
        }
    }

    private fun setupMiniPlayer() {
        btnMiniPlayerPlay.setOnClickListener {
            viewModel.togglePlayPause()
        }
    }

    private fun observeViewModel() {
        // Observe song list changes
        viewModel.songs.observe(this) { songs ->
            if (songs.isNullOrEmpty()) {
                rvSongs.visibility = View.GONE
                layoutEmptyState.visibility = View.VISIBLE
            } else {
                layoutEmptyState.visibility = View.GONE
                rvSongs.visibility = View.VISIBLE
                songAdapter.submitList(songs)
            }
        }

        // Observe currently selected song
        viewModel.currentPlayingSong.observe(this) { song ->
            if (song == null) {
                layoutMiniPlayer.visibility = View.GONE
            } else {
                layoutMiniPlayer.visibility = View.VISIBLE
                tvMiniPlayerTitle.text = song.title
                tvMiniPlayerArtist.text = song.artist
            }
            updateAdapterPlayback()
        }

        // Observe playback state (playing / paused)
        viewModel.isPlaying.observe(this) { isPlaying ->
            val iconRes = if (isPlaying) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }
            btnMiniPlayerPlay.setImageResource(iconRes)
            updateAdapterPlayback()
        }
    }

    private fun updateAdapterPlayback() {
        val currentSongId = viewModel.currentPlayingSong.value?.id
        val isPlaying = viewModel.isPlaying.value ?: false
        songAdapter.updatePlaybackState(currentSongId, isPlaying)
    }

    private fun showSongOverflowMenu(anchorView: View, song: Song) {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.menu_song_overflow, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_share -> {
                    Toast.makeText(this, getString(R.string.action_share_toast, song.title), Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_delete -> {
                    Toast.makeText(this, getString(R.string.action_delete_toast, song.title), Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}
