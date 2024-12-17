package com.example.bookshelf
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class MainActivity : AppCompatActivity() {
    private val REQUEST_PERMISSIONS = 100
    private val FILE_PICKER_REQUEST_CODE = 123
    private val REQUEST_IMAGE_CAPTURE: Int = 1
    private val PERMISSION_ALL: Int = 1
    private val recommender: Recommender = Recommender()
    private var photoFile: File? = null
    private var photoURI: Uri? = null
    private lateinit var bookListView: ListView
    private lateinit var bookAdapter: ArrayAdapter<String>
    private val bookList = mutableListOf<Book>()

    private fun createImageFile(): File {
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile("photo_", ".jpg", storageDir)
    }

    private fun getFileName(uri: Uri): String {
        var fileName = ""
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            fileName = cursor.getString(nameIndex)
        }
        return fileName
    }

    private fun openFileChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, FILE_PICKER_REQUEST_CODE)
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            try {
                photoFile = createImageFile()
                photoFile?.let {
                    photoURI = FileProvider.getUriForFile(
                        this,
                        "${applicationContext.packageName}.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                dispatchTakePictureIntent()
            } else {
                Toast.makeText(this, "Permissions are required to use the camera.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){

            val PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
            ActivityCompat.requestPermissions(
                this,
                PERMISSIONS,
                PERMISSION_ALL
            )
        } else {
            dispatchTakePictureIntent()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bookListView = findViewById(R.id.bookListView)
        bookAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        bookListView.adapter = bookAdapter

        val btnOpenCamera = findViewById<Button>(R.id.btnOpenCamera)
        btnOpenCamera.setOnClickListener {
            checkAndRequestPermissions()
        }

        findViewById<Button>(R.id.btnSelectFile).setOnClickListener {
            openFileChooser()
        }
    }

    private fun updateListView() {
        bookAdapter.clear()
        bookAdapter.addAll(bookList.map { "${it.title} by ${it.author}" })
        bookAdapter.notifyDataSetChanged()
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val response: List<Book>
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Image saved at: ${photoFile?.absolutePath}", Toast.LENGTH_LONG).show()
            lifecycleScope.launch {
                val books = recommender.recommendBooksFromImage(photoFile)
                for(book in books){
                    bookList.add(book)
                }
                updateListView()
            }
        }
        else if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val file = File(cacheDir, getFileName(uri))
                inputStream?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                lifecycleScope.launch {
                    val books = recommender.recommendBooksFromImage(file)
                    for(book in books){
                        bookList.add(book)
                    }
                    updateListView()
                }
            }
        }
    }

}