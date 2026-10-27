package com.example.beatflow

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.beatflow.data.mapper.getHighResArtworkUrl
import com.example.beatflow.domain.model.HomeTab
import com.example.beatflow.domain.model.Song
import com.example.beatflow.ui.home.HomeUiState
import com.example.beatflow.ui.home.HomeViewModel
import com.example.beatflow.ui.home.SongAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var songAdapter: SongAdapter

    // Top Bar UI Components
    private lateinit var btnSearch: ImageButton
    private lateinit var btnTune: ImageButton

    // Tab & Content UI Components
    private lateinit var tabLayout: TabLayout
    private lateinit var layoutSectionHeader: View
    private lateinit var tvSongCount: TextView
    private lateinit var rvSongs: RecyclerView
    private lateinit var progressBar: ProgressBar

    // Empty & Offline State UI Components
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var ivEmptyIcon: ImageView
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptySubtitle: TextView
    private lateinit var btnTryAgain: MaterialButton

    // Mini Player UI Components
    private lateinit var layoutMiniPlayer: View
    private lateinit var ivMiniPlayerArt: ImageView
    private lateinit var tvMiniPlayerTitle: TextView
    private lateinit var tvMiniPlayerArtist: TextView
    private lateinit var ivMiniPlayerBars: ImageView
    private lateinit var btnMiniPlayerPlay: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        initViews()
        applyWindowInsets()
        setupTopBarActions()
        setupTabs()
        setupRecyclerView()
        setupMiniPlayer()
        setupEmptyStateActions()
        observeViewModel()
    }

    private fun initViews() {
        btnSearch = findViewById(R.id.btnSearch)
        btnTune = findViewById(R.id.btnTune)

        tabLayout = findViewById(R.id.tabLayout)
        layoutSectionHeader = findViewById(R.id.layoutSectionHeader)
        tvSongCount = findViewById(R.id.tvSongCount)
        rvSongs = findViewById(R.id.rvSongs)
        progressBar = findViewById(R.id.progressBar)

        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        ivEmptyIcon = findViewById(R.id.ivEmptyIcon)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle)
        btnTryAgain = findViewById(R.id.btnTryAgain)

        layoutMiniPlayer = findViewById(R.id.layoutMiniPlayer)
        ivMiniPlayerArt = findViewById(R.id.ivMiniPlayerArt)
        tvMiniPlayerTitle = findViewById(R.id.tvMiniPlayerTitle)
        tvMiniPlayerArtist = findViewById(R.id.tvMiniPlayerArtist)
        ivMiniPlayerBars = findViewById(R.id.ivMiniPlayerBars)
        btnMiniPlayerPlay = findViewById(R.id.btnMiniPlayerPlay)
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupTopBarActions() {
        btnSearch.setOnClickListener {
            Toast.makeText(this, getString(R.string.cd_search), Toast.LENGTH_SHORT).show()
        }
        btnTune.setOnClickListener {
            Toast.makeText(this, getString(R.string.cd_filter), Toast.LENGTH_SHORT).show()
        }
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
            itemAnimator = null
        }
    }

    private fun setupMiniPlayer() {
        btnMiniPlayerPlay.setOnClickListener {
            viewModel.togglePlayPause()
        }
    }

    private fun setupEmptyStateActions() {
        btnTryAgain.setOnClickListener {
            viewModel.loadOnlineSongs()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Collect UI state (Loading / Success / Empty / Error)
                launch {
                    viewModel.uiState.collect { state ->
                        handleUiState(state)
                    }
                }

                // Collect currently playing song
                launch {
                    viewModel.currentPlayingSong.collect { song ->
                        if (song == null) {
                            layoutMiniPlayer.visibility = View.GONE
                        } else {
                            layoutMiniPlayer.visibility = View.VISIBLE
                            tvMiniPlayerTitle.text = song.trackName
                            tvMiniPlayerArtist.text = song.artistName

                            val artworkUrl = getHighResArtworkUrl(song.artworkUrl)
                            if (!artworkUrl.isNullOrBlank()) {
                                Glide.with(this@MainActivity)
                                    .load(artworkUrl)
                                    .placeholder(R.drawable.bg_artwork_placeholder)
                                    .error(R.drawable.bg_artwork_error)
                                    .fallback(R.drawable.bg_artwork_placeholder)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .centerCrop()
                                    .into(ivMiniPlayerArt)
                            } else {
                                Glide.with(this@MainActivity).clear(ivMiniPlayerArt)
                                ivMiniPlayerArt.setImageResource(R.drawable.bg_artwork_placeholder)
                            }
                        }
                        updateAdapterPlayback()
                    }
                }

                // Collect playback playing/paused status
                launch {
                    viewModel.isPlaying.collect { isPlaying ->
                        val iconRes = if (isPlaying) {
                            R.drawable.ic_pause_dark
                        } else {
                            R.drawable.ic_play_dark
                        }
                        btnMiniPlayerPlay.setImageResource(iconRes)
                        ivMiniPlayerBars.visibility = if (isPlaying) View.VISIBLE else View.GONE
                        updateAdapterPlayback()
                    }
                }

                // Collect playback errors (e.g. invalid preview URL or streaming issues)
                launch {
                    viewModel.playbackError.collect { errorMsg ->
                        if (!errorMsg.isNullOrBlank()) {
                            Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun handleUiState(state: HomeUiState) {
        when (state) {
            is HomeUiState.Loading -> {
                progressBar.visibility = View.VISIBLE
                rvSongs.visibility = View.GONE
                layoutSectionHeader.visibility = View.GONE
                layoutEmptyState.visibility = View.GONE
            }
            is HomeUiState.Success -> {
                progressBar.visibility = View.GONE
                layoutEmptyState.visibility = View.GONE
                layoutSectionHeader.visibility = View.VISIBLE
                tvSongCount.text = getString(R.string.songs_count_format, state.songs.size)
                rvSongs.visibility = View.VISIBLE
                songAdapter.submitList(state.songs)
            }
            is HomeUiState.Empty -> {
                progressBar.visibility = View.GONE
                rvSongs.visibility = View.GONE
                layoutSectionHeader.visibility = View.GONE
                layoutEmptyState.visibility = View.VISIBLE
                ivEmptyIcon.setImageResource(R.drawable.ic_music_note)
                tvEmptyTitle.text = state.title
                tvEmptySubtitle.text = state.message
                btnTryAgain.visibility = View.GONE
            }
            is HomeUiState.Error -> {
                progressBar.visibility = View.GONE
                rvSongs.visibility = View.GONE
                layoutSectionHeader.visibility = View.GONE
                layoutEmptyState.visibility = View.VISIBLE
                ivEmptyIcon.setImageResource(R.drawable.ic_wifi_off)
                tvEmptyTitle.text = state.title
                tvEmptySubtitle.text = state.message
                btnTryAgain.visibility = View.VISIBLE
            }
        }
    }

    private fun updateAdapterPlayback() {
        val currentTrackId = viewModel.currentPlayingSong.value?.trackId
        val isPlaying = viewModel.isPlaying.value
        songAdapter.updatePlaybackState(currentTrackId, isPlaying)
    }

    private fun showSongOverflowMenu(anchorView: View, song: Song) {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.menu_song_overflow, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_share -> {
                    Toast.makeText(
                        this,
                        getString(R.string.action_share_toast, song.trackName),
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
                R.id.action_delete -> {
                    Toast.makeText(
                        this,
                        getString(R.string.action_delete_toast, song.trackName),
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}
