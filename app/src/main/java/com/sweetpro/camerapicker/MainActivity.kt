package com.sweetpro.camerapicker

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.android.material.snackbar.Snackbar
import com.sweetpro.camerapicker.databinding.ActivityMainBinding
import java.io.*
import java.util.*

class MainActivity : AppCompatActivity() {
    private final val TAG ="MainActivity"

    var photoFile: File? = null  // local file for sharing with camera app
    var publicUri: Uri? = null   // content URI of the saved image file on public storage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // handle permission(s)
        resultLauncherForPermission.launch(neededRuntimePermissions)

        // handle actions
        binding.callCameraButton.setOnClickListener { openCameraAppToPick() }
        binding.savePublicButton.setOnClickListener { saveImageToPublic() }
        binding.loadPublicButton.setOnClickListener { loadImageFromPublic() }
    }

    private fun openCameraAppToPick() {
        val view = binding.root
        // prepare a file for saving the captured photo.
        photoFile = prepareEmptyPhotoFile("photo.jpg")
        if (photoFile == null) { 
            Snackbar.make(view, "Unable to make an empty local file", Snackbar.LENGTH_SHORT).show()
            return
        }

        // interact with camera app
        val photoUri = getUriFromFile(photoFile!!)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        if (cameraIntent.resolveActivity(packageManager) != null) {
            // call camera app using registerForActivityResult by passing contract,
            // to handle the result value(OK or CANCELED) after camera app is finished.
            resultLauncherForCameraResult.launch(cameraIntent)
        } else {
            Snackbar.make(view, "Unable to open camera", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToPublic() {
        if (photoFile == null) { return }

        // make empty target file on shareable media storage
        val targetFilename = "publicImage_${Date().time}.jsp"
        publicUri = makeEmptyTargetFile(targetFilename)
        if (publicUri == null) { return }

        Snackbar.make(binding.root, "content uri=${publicUri}", Snackbar.LENGTH_SHORT).show()
        makeCopycat(photoFile!!, publicUri!!)
        photoFile!!.delete()
    }

    private fun loadImageFromPublic() {
        if (publicUri == null) { return }

        binding.imagePublic.setImageURI(publicUri)
    }

    private fun prepareEmptyPhotoFile(filename: String): File? {
        // to get app specific directory for saving photo which taken by camera app
        val storageDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        var tempFile: File? = null
        try {
            tempFile = File.createTempFile(filename, ".jpg", storageDirectory)
        } catch (e: IOException) {
            Log.d(TAG, "prepareEmptyPhotoFile: ${e.message}")
        }
        return tempFile
    }

    // region:- Activity Result Handler
    // to handle the result value(ok or cancel) after camera app is finished.
    private val resultLauncherForCameraResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d(TAG, ": RESULT_OK")
                binding.imageLocal.setImageURI(getUriFromFile(photoFile!!))
            } else {
                Log.d(TAG, ": RESULT_CANCELED")
                photoFile?.delete()
                photoFile = null
            }
        }
    // endregion----

    private fun getUriFromFile(photoFile: File): Uri? {
        return FileProvider.getUriForFile(this,
            "com.sweetpro.camerapicker.fileprovider",
            photoFile)
    }



    private fun makeEmptyTargetFile(targetFilename: String): Uri? {
        Log.d(TAG, "makeEmptyTargetFile: targetFilename=${targetFilename}")

        // make empty target file on public storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return makeEmptyTargetFile_on10plus(targetFilename)  // using relative path
        } else {
            return makeEmptyTargetFile_below10(targetFilename)   // using absolute pathname
        }
    }

    private fun makeEmptyTargetFile_on10plus(targetFilename: String): Uri? {
        val relativePath = Environment.DIRECTORY_PICTURES + File.separatorChar + getString(R.string.app_name)
        Log.d(TAG, "makeEmptyTargetFile_on10plus: relativePath=${relativePath}")

        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, targetFilename)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)  // <<< see here

        val contentUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        return contentUri
    }

    private fun makeEmptyTargetFile_below10(targetFilename: String): Uri? {
        val publicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val absolutePathname = "${publicDirectory.absolutePath}/${targetFilename}"
        Log.d(TAG, "makeEmptyTargetFile_below10: absolutePathname=${absolutePathname}")

        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, targetFilename)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        contentValues.put(MediaStore.MediaColumns.DATA, absolutePathname)  // <<< see here

        val contentUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        return contentUri
    }

    private fun makeCopycat(photoFile: File, publicUri: Uri) {
        val source = FileInputStream(photoFile)
        // can not directly convert URI to file on Android 10,
        // so need to use contentResolver
        val pfd = contentResolver.openFileDescriptor(publicUri, "w")
        try {
            pfd?.use {
                val target = FileOutputStream(pfd.fileDescriptor)
                val buffer = ByteArray(4096)
                var length: Int
                while ( (source.read(buffer).also { length = it }) > 0 ) {
                    target.write(buffer, 0, length)
                }
                target.flush()
            }
        } catch (e: Exception) {
            Log.d(TAG, "makeCopycat: ${e.message}")
        }
    }


    // region:- Routine: permission, view binding
    //  view binding and requesting runtime permission(s)
    //
    // permission:
    // for requesting runtime permission(s) using new API
    private val neededRuntimePermissions = arrayOf(android.Manifest.permission.CAMERA)
    private val resultLauncherForPermission =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                permissions.entries.forEach {
                    Log.d(TAG, ": registerForActivityResult: ${it.key}=${it.value}")

                    // if any permission is not granted...
                    if (! it.value) {
                        // do anything if needed: ex) display about limitation
                        Snackbar.make(binding.root, R.string.permissions_request, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }

    // view binding
    lateinit var binding: ActivityMainBinding
    // endregion----
}