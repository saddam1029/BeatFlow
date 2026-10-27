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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.beatflow.R
import com.example.beatflow.data.mapper.getHighResArtworkUrl
import com.example.beatflow.domain.model.Song

class SongAdapter(
    private val onSongClick: (Song) -> Unit,
    private val onPlayClick: (Song) -> Unit,
    private val onOverflowClick: (View, Song) -> Unit
) : ListAdapter<Song, SongAdapter.SongViewHolder>(SongDiffCallback()) {

    private var currentPlayingSongId: Long? = null
    private var isPlaying: Boolean = false

    fun updatePlaybackState(trackId: Long?, isPlaying: Boolean) {
        val oldId = currentPlayingSongId
        this.currentPlayingSongId = trackId
        this.isPlaying = isPlaying

        currentList.forEachIndexed { index, song ->
            if (song.trackId == oldId || song.trackId == trackId) {
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

    override fun onViewRecycled(holder: SongViewHolder) {
        super.onViewRecycled(holder)
        // Ensure previous Glide request is cancelled/cleared when item is recycled
        Glide.with(holder.itemView.context).clear(holder.ivAlbumArt)
    }

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAlbumArt: ImageView = itemView.findViewById(R.id.ivAlbumArt)
        val tvTrackName: TextView = itemView.findViewById(R.id.tvTrackName)
        val tvArtistName: TextView = itemView.findViewById(R.id.tvArtistName)
        val btnPlaySong: ImageButton = itemView.findViewById(R.id.btnPlaySong)
        val btnSongOverflow: ImageButton = itemView.findViewById(R.id.btnSongOverflow)

        fun bind(song: Song) {
            tvTrackName.text = song.trackName
            tvArtistName.text = song.artistName

            // Resolve high-resolution artwork URL safely
            val artworkUrl = getHighResArtworkUrl(song.artworkUrl)

            if (!artworkUrl.isNullOrBlank()) {
                Glide.with(itemView.context)
                    .load(artworkUrl)
                    .placeholder(R.drawable.bg_artwork_placeholder)
                    .error(R.drawable.bg_artwork_error)
                    .fallback(R.drawable.bg_artwork_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(ivAlbumArt)
            } else {
                // Clear any previous recycling image and show neutral fallback
                Glide.with(itemView.context).clear(ivAlbumArt)
                ivAlbumArt.setImageResource(R.drawable.bg_artwork_placeholder)
            }

            val isCurrentSongActive = (song.trackId == currentPlayingSongId && isPlaying)
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
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean =
            oldItem.trackId == newItem.trackId

        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean =
            oldItem == newItem
    }
}
