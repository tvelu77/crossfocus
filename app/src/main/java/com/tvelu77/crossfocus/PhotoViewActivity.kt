package com.tvelu77.crossfocus

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tvelu77.crossfocus.databinding.ActivityPhotoViewBinding

class PhotoViewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityPhotoViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path == null) {
            finish()
            return
        }
        binding.photoView.setImageBitmap(BitmapFactory.decodeFile(path))
        binding.photoRoot.setOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_PATH = "extra_path"
    }
}
