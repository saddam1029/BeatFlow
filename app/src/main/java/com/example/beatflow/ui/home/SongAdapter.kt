package com.example.beatflow.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.beatflow.R
import com.example.beatflow.data.model.Song

class SongAdapter(
    private val onSongClick: (Song) -> Unit,
    private val onPlayClick: (Song) -> Unit,
    private val onOverflowClick: (View, Song) -> Unit
) : ListAdapter<Song, SongAdapter.SongViewHolder>(SongDiffCallback()) {

    private var currentPlayingSongId: Long? = null
    private var isPlaying: Boolean = false

    fun updatePlaybackState(songId: Long?, isPlaying: Boolean) {
        val oldId = currentPlayingSongId
        this.currentPlayingSongId = songId
        this.isPlaying = isPlaying

        // Efficiently refresh affected rows
        currentList.forEachIndexed { index, song ->
            if (song.id == oldId || song.id == songId) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Reserved empty ImageView for album artwork
        val ivAlbumArt: ImageView = itemView.findViewById(R.id.ivAlbumArt)
        val tvTrackName: TextView = itemView.findViewById(R.id.tvTrackName)
        val tvArtistName: TextView = itemView.findViewById(R.id.tvArtistName)
        val btnPlaySong: ImageButton = itemView.findViewById(R.id.btnPlaySong)
        val btnSongOverflow: ImageButton = itemView.findViewById(R.id.btnSongOverflow)

        fun bind(song: Song) {
            tvTrackName.text = song.title
            tvArtistName.text = song.artist

            val isCurrentSongActive = (song.id == currentPlayingSongId && isPlaying)
            btnPlaySong.setImageResource(
                if (isCurrentSongActive) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )

            itemView.setOnClickListener { onSongClick(song) }
            btnPlaySong.setOnClickListener { onPlayClick(song) }
            btnSongOverflow.setOnClickListener { onOverflowClick(btnSongOverflow, song) }
        }
    }

    class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean = oldItem == newItem
    }
}
