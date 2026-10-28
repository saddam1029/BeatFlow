package com.example.beatflow

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.beatflow.data.ads.InterstitialAdManager
import com.example.beatflow.data.mapper.resolveArtworkUri
import com.example.beatflow.domain.model.HomeTab
import com.example.beatflow.domain.model.Song
import com.example.beatflow.ui.home.HomeUiState
import com.example.beatflow.ui.home.HomeViewModel
import com.example.beatflow.ui.home.SongAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var songAdapter: SongAdapter

    // Top Bar UI Components
    private lateinit var layoutHeaderDefault: View
    private lateinit var layoutSearch: View
    private lateinit var btnSearch: ImageButton
    private lateinit var etSearch: EditText
    private lateinit var btnCancelSearch: ImageButton

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
    private lateinit var clPlay: View
    private lateinit var ivMiniPlayerArt: ImageView
    private lateinit var tvMiniPlayerTitle: TextView
    private lateinit var tvMiniPlayerArtist: TextView
    private lateinit var ivMiniPlayerBars: ImageView
    private lateinit var btnMiniPlayerPlay: ImageButton
    private lateinit var pbMiniPlayer: ProgressBar

    // Retained reference for Exit Confirmation Bottom Sheet
    private var exitBottomSheetDialog: BottomSheetDialog? = null

    // Pending song awaiting scoped storage delete confirmation
    private var pendingDeleteSong: Song? = null

    // Runtime permission launcher for local audio access
    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadDeviceSongs()
        } else {
            val permission = getRequiredAudioPermission()
            val isPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
            viewModel.onPermissionDenied(isPermanentlyDenied)
        }
    }

    // MediaStore scoped storage deletion confirmation launcher
    private val deleteMediaLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val song = pendingDeleteSong
        pendingDeleteSong = null
        if (result.resultCode == RESULT_OK && song != null) {
            viewModel.onDeviceSongDeleteConfirmed(song)
            Toast.makeText(
                this,
                getString(R.string.delete_song_success, song.trackName),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            viewModel.onDeviceSongDeleteCancelled()
            Toast.makeText(this, getString(R.string.delete_song_cancelled), Toast.LENGTH_SHORT).show()
        }
    }

    // Track whether notification permission has been requested during this session to avoid spamming prompts
    private var hasRequestedNotificationPermission = false

    // Runtime permission launcher for notifications on Android 13+
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Playback and app continue regardless of whether permission is granted or denied
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                if (!hasRequestedNotificationPermission) {
                    hasRequestedNotificationPermission = true
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                        MaterialAlertDialogBuilder(this, R.style.Theme_BeatFlow_AlertDialog)
                            .setTitle(getString(R.string.notification_permission_title))
                            .setMessage(getString(R.string.notification_permission_message))
                            .setPositiveButton(getString(R.string.btn_allow)) { dialog, _ ->
                                dialog.dismiss()
                                notificationPermissionLauncher.launch(permission)
                            }
                            .setNegativeButton(getString(R.string.btn_cancel)) { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                    } else {
                        notificationPermissionLauncher.launch(permission)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_PENDING_DELETE_ID)) {
            val id = savedInstanceState.getLong(KEY_PENDING_DELETE_ID)
            val name = savedInstanceState.getString(KEY_PENDING_DELETE_NAME).orEmpty()
            val artist = savedInstanceState.getString(KEY_PENDING_DELETE_ARTIST).orEmpty()
            val preview = savedInstanceState.getString(KEY_PENDING_DELETE_PREVIEW)
            val isLocal = savedInstanceState.getBoolean(KEY_PENDING_DELETE_LOCAL, true)
            pendingDeleteSong = Song(
                trackId = id,
                trackName = name,
                artistName = artist,
                artworkUrl = null,
                previewUrl = preview,
                trackViewUrl = null,
                durationMs = 0L,
                isLocal = isLocal
            )
        }

        initViews()
        applyWindowInsets()
        setupTopBarActions()
        setupTabs()
        setupRecyclerView()
        setupMiniPlayer()
        setupEmptyStateActions()
        setupBackNavigation()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // If the user is on Device Songs and permission was granted via Settings while away, auto-load
        if (viewModel.selectedTab.value == HomeTab.DEVICE && hasAudioPermission()) {
            if (!viewModel.hasDeviceSongsLoaded || viewModel.uiState.value is HomeUiState.PermissionRequired) {
                viewModel.loadDeviceSongs()
            }
        }
    }

    private fun initViews() {
        layoutHeaderDefault = findViewById(R.id.layoutHeaderDefault)
        layoutSearch = findViewById(R.id.layoutSearch)
        btnSearch = findViewById(R.id.btnSearch)
        etSearch = findViewById(R.id.etSearch)
        btnCancelSearch = findViewById(R.id.btnCancelSearch)

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
        clPlay = findViewById(R.id.clPlay)
        ivMiniPlayerArt = findViewById(R.id.ivMiniPlayerArt)
        tvMiniPlayerTitle = findViewById(R.id.tvMiniPlayerTitle)
        tvMiniPlayerArtist = findViewById(R.id.tvMiniPlayerArtist)
        ivMiniPlayerBars = findViewById(R.id.ivMiniPlayerBars)
        btnMiniPlayerPlay = findViewById(R.id.btnMiniPlayerPlay)
        pbMiniPlayer = findViewById(R.id.pbMiniPlayer)
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
            showSearchBar()
        }

        btnCancelSearch.setOnClickListener {
            if (!etSearch.text.isNullOrEmpty()) {
                etSearch.text.clear()
            } else {
                hideSearchBar()
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.filterSongs(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearch.text.toString().trim()
                if (query.isNotEmpty() && viewModel.selectedTab.value == HomeTab.ONLINE) {
                    viewModel.loadOnlineSongs(query)
                }
                hideKeyboard()
                true
            } else {
                false
            }
        }
    }

    private fun showSearchBar() {
        layoutHeaderDefault.visibility = View.GONE
        layoutSearch.visibility = View.VISIBLE
        etSearch.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(etSearch, 0)
    }

    private fun hideSearchBar() {
        etSearch.text?.clear()
        viewModel.clearSearch()
        layoutSearch.visibility = View.GONE
        layoutHeaderDefault.visibility = View.VISIBLE
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }

    private fun getRequiredAudioPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = getRequiredAudioPermission()
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAndRequestAudioPermission() {
        if (hasAudioPermission()) {
            if (!viewModel.hasDeviceSongsLoaded) {
                viewModel.loadDeviceSongs()
            }
        } else {
            audioPermissionLauncher.launch(getRequiredAudioPermission())
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun setupTabs() {
        tabLayout.removeAllTabs()
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_online_songs))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_device_songs))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> viewModel.selectTab(HomeTab.ONLINE)
                    1 -> {
                        viewModel.selectTab(HomeTab.DEVICE)
                        checkAndRequestAudioPermission()
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onSongClick = { song -> playSongWithAd(song) },
            onPlayClick = { song -> playSongWithAd(song) },
            onOverflowClick = { _, song -> showSongOptionsBottomSheet(song) }
        )

        rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = songAdapter
            itemAnimator = null
        }
    }

    private fun playSongWithAd(song: Song) {
        if (InterstitialAdManager.isShowingAd) {
            return
        }

        val wasPlaying = viewModel.isPlaying.value
        val currentSong = viewModel.currentPlayingSong.value
        val isDifferentSong = (song.trackId != currentSong?.trackId)

        if (wasPlaying) {
            viewModel.pause()
        }

        InterstitialAdManager.showAdIfReady(
            activity = this,
            onAdShowed = {
                // Ad showing
            },
            onAdDismissedOrFailed = {
                viewModel.playSong(song)
                checkAndRequestNotificationPermission()
            }
        )
    }

    private fun setupMiniPlayer() {
        layoutMiniPlayer.setOnClickListener {
            viewModel.togglePlayPause()
        }
        clPlay.setOnClickListener {
            viewModel.togglePlayPause()
        }
        btnMiniPlayerPlay.setOnClickListener {
            viewModel.togglePlayPause()
        }
    }

    private fun setupEmptyStateActions() {
        btnTryAgain.setOnClickListener {
            when (viewModel.selectedTab.value) {
                HomeTab.ONLINE -> viewModel.loadOnlineSongs()
                HomeTab.DEVICE -> checkAndRequestAudioPermission()
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (layoutSearch.visibility == View.VISIBLE) {
                    hideSearchBar()
                    return
                }

                if (exitBottomSheetDialog?.isShowing == true) {
                    return
                }
                showExitConfirmationSheet()
            }
        })
    }

    private fun showExitConfirmationSheet() {
        val dialog = BottomSheetDialog(this, R.style.Theme_BeatFlow_BottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_exit_confirmation, null)
        dialog.setContentView(view)

        val btnStay = view.findViewById<MaterialButton>(R.id.btnStayInApp)
        val btnExit = view.findViewById<MaterialButton>(R.id.btnExitApp)

        btnStay.setOnClickListener {
            dialog.dismiss()
        }

        btnExit.setOnClickListener {
            dialog.dismiss()
            finish()
        }

        dialog.setOnDismissListener {
            exitBottomSheetDialog = null
        }

        exitBottomSheetDialog = dialog
        dialog.show()
    }

    private fun showSongOptionsBottomSheet(song: Song) {
        val dialog = BottomSheetDialog(this, R.style.Theme_BeatFlow_BottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_song_options, null)
        dialog.setContentView(view)

        val tvTitle = view.findViewById<TextView>(R.id.tvSheetSongTitle)
        val tvArtist = view.findViewById<TextView>(R.id.tvSheetSongArtist)
        val btnShare = view.findViewById<View>(R.id.btnSheetShare)
        val btnDelete = view.findViewById<View>(R.id.btnSheetDelete)

        tvTitle.text = song.trackName
        tvArtist.text = song.artistName

        btnShare.setOnClickListener {
            dialog.dismiss()
            shareSong(song)
        }

        btnDelete.setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmationDialog(song)
        }

        dialog.show()
    }

    private fun showDeleteConfirmationDialog(song: Song) {
        MaterialAlertDialogBuilder(this, R.style.Theme_BeatFlow_AlertDialog)
            .setTitle(getString(R.string.delete_song_dialog_title))
            .setMessage(getString(R.string.delete_song_dialog_message))
            .setNegativeButton(getString(R.string.btn_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.btn_delete)) { dialog, _ ->
                dialog.dismiss()
                executeDeleteSong(song)
            }
            .show()
    }

    private fun executeDeleteSong(song: Song) {
        if (song.isLocal) {
            viewModel.deleteDeviceSong(
                song = song,
                onRequiresConfirmation = { intentSender ->
                    pendingDeleteSong = song
                    val intentSenderRequest = IntentSenderRequest.Builder(intentSender as IntentSender).build()
                    deleteMediaLauncher.launch(intentSenderRequest)
                },
                onComplete = { result ->
                    result.onSuccess {
                        Toast.makeText(
                            this,
                            getString(R.string.delete_song_success, song.trackName),
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { error ->
                        Toast.makeText(
                            this,
                            getString(R.string.delete_song_error, song.trackName) + ": ${error.localizedMessage}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        } else {
            viewModel.deleteOnlineSong(song) { result ->
                result.onSuccess {
                    Toast.makeText(
                        this,
                        getString(R.string.delete_song_success, song.trackName),
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        getString(R.string.delete_song_error, song.trackName) + ": ${error.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun shareSong(song: Song) {
        try {
            if (song.isLocal) {
                val previewUrl = song.previewUrl
                if (previewUrl.isNullOrBlank()) {
                    Toast.makeText(this, getString(R.string.share_file_unavailable), Toast.LENGTH_SHORT).show()
                    return
                }
                val contentUri = Uri.parse(previewUrl)
                val mimeType = contentResolver.getType(contentUri) ?: "audio/*"

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, song.trackName)
                    putExtra(Intent.EXTRA_TEXT, "${song.trackName} - ${song.artistName}")
                    type = mimeType
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(sendIntent, getString(R.string.share_song_title))
                startActivity(chooser)
            } else {
                val urlToShare = song.trackViewUrl?.takeIf { it.isNotBlank() }
                    ?: song.previewUrl?.takeIf { it.isNotBlank() }
                    ?: ""
                val shareText = if (urlToShare.isNotBlank()) {
                    "${song.trackName} - ${song.artistName}\n$urlToShare"
                } else {
                    "${song.trackName} - ${song.artistName}"
                }
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                val chooser = Intent.createChooser(sendIntent, getString(R.string.share_song_title))
                startActivity(chooser)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Collect UI state (Loading / Success / Empty / Error / PermissionRequired)
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

                            val artworkUrl = resolveArtworkUri(song.artworkUrl)
                            if (!artworkUrl.isNullOrBlank()) {
                                Glide.with(this@MainActivity)
                                    .load(artworkUrl)
                                    .placeholder(R.drawable.ic_beatflow_logo)
                                    .error(R.drawable.ic_beatflow_logo)
                                    .fallback(R.drawable.ic_beatflow_logo)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .centerCrop()
                                    .into(ivMiniPlayerArt)
                            } else {
                                Glide.with(this@MainActivity).clear(ivMiniPlayerArt)
                                ivMiniPlayerArt.setImageResource(R.drawable.ic_beatflow_logo)
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

                // Collect playback progress (0 to 100%)
                launch {
                    viewModel.playbackProgress.collect { progress ->
                        pbMiniPlayer.progress = progress
                    }
                }

                // Collect playback errors (e.g. invalid preview URL, missing files, or streaming issues)
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
                btnTryAgain.text = getString(R.string.btn_try_again)
                btnTryAgain.visibility = View.VISIBLE
                btnTryAgain.setOnClickListener {
                    when (viewModel.selectedTab.value) {
                        HomeTab.ONLINE -> viewModel.loadOnlineSongs()
                        HomeTab.DEVICE -> viewModel.loadDeviceSongs()
                    }
                }
            }
            is HomeUiState.PermissionRequired -> {
                progressBar.visibility = View.GONE
                rvSongs.visibility = View.GONE
                layoutSectionHeader.visibility = View.GONE
                layoutEmptyState.visibility = View.VISIBLE
                ivEmptyIcon.setImageResource(R.drawable.ic_music_note)
                tvEmptyTitle.text = state.title
                tvEmptySubtitle.text = state.message
                btnTryAgain.visibility = View.VISIBLE
                if (state.isPermanentlyDenied) {
                    btnTryAgain.text = getString(R.string.btn_open_settings)
                    btnTryAgain.setOnClickListener {
                        openAppSettings()
                    }
                } else {
                    btnTryAgain.text = getString(R.string.btn_grant_permission)
                    btnTryAgain.setOnClickListener {
                        checkAndRequestAudioPermission()
                    }
                }
            }
        }
    }

    private fun updateAdapterPlayback() {
        val currentTrackId = viewModel.currentPlayingSong.value?.trackId
        val isPlaying = viewModel.isPlaying.value
        songAdapter.updatePlaybackState(currentTrackId, isPlaying)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingDeleteSong?.let { song ->
            outState.putLong(KEY_PENDING_DELETE_ID, song.trackId)
            outState.putString(KEY_PENDING_DELETE_NAME, song.trackName)
            outState.putString(KEY_PENDING_DELETE_ARTIST, song.artistName)
            outState.putString(KEY_PENDING_DELETE_PREVIEW, song.previewUrl)
            outState.putBoolean(KEY_PENDING_DELETE_LOCAL, song.isLocal)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exitBottomSheetDialog?.dismiss()
        exitBottomSheetDialog = null
    }

    companion object {
        private const val KEY_PENDING_DELETE_ID = "key_pending_delete_id"
        private const val KEY_PENDING_DELETE_NAME = "key_pending_delete_name"
        private const val KEY_PENDING_DELETE_ARTIST = "key_pending_delete_artist"
        private const val KEY_PENDING_DELETE_PREVIEW = "key_pending_delete_preview"
        private const val KEY_PENDING_DELETE_LOCAL = "key_pending_delete_local"
    }
}
